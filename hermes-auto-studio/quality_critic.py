#!/usr/bin/env python3
import argparse
import json
import math
import os
import subprocess
import tempfile
from pathlib import Path

from PIL import Image, ImageChops, ImageStat


def run(cmd):
    return subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, check=False)


def probe(path):
    p = run([
        "ffprobe", "-v", "error", "-show_entries",
        "format=duration,size:stream=index,codec_type,width,height,avg_frame_rate",
        "-of", "json", path,
    ])
    if p.returncode != 0:
        raise RuntimeError(p.stderr.strip() or "ffprobe failed")
    return json.loads(p.stdout)


def extract_frames(path, outdir, interval):
    pattern = str(Path(outdir) / "frame_%03d.jpg")
    p = run([
        "ffmpeg", "-y", "-loglevel", "error", "-i", path,
        "-vf", f"fps=1/{max(interval,0.25):.3f},scale=192:-2",
        "-frames:v", "32", pattern,
    ])
    if p.returncode != 0:
        raise RuntimeError(p.stderr.strip() or "frame extraction failed")
    return sorted(Path(outdir).glob("frame_*.jpg"))


def ahash(im):
    g = im.convert("L").resize((8, 8))
    px = list(g.getdata())
    avg = sum(px) / max(len(px), 1)
    bits = 0
    for i, v in enumerate(px):
        if v >= avg:
            bits |= 1 << i
    return bits


def hamming(a, b):
    return (a ^ b).bit_count()


def frame_metrics(files):
    if not files:
        return {
            "frames": 0,
            "avg_brightness": 0.0,
            "near_black_ratio": 1.0,
            "adjacent_diff": 0.0,
            "duplicate_ratio": 1.0,
            "hash_unique_ratio": 0.0,
        }
    ims = [Image.open(p).convert("RGB") for p in files]
    brightness = []
    hashes = []
    diffs = []
    for im in ims:
        brightness.append(sum(ImageStat.Stat(im.convert("L")).mean))
        hashes.append(ahash(im))
    for a, b in zip(ims, ims[1:]):
        d = ImageChops.difference(a, b).convert("L")
        diffs.append(ImageStat.Stat(d).mean[0])
    near_black = sum(1 for b in brightness if b < 18) / len(brightness)
    duplicate_pairs = 0
    if len(hashes) > 1:
        duplicate_pairs = sum(1 for a, b in zip(hashes, hashes[1:]) if hamming(a, b) <= 2)
        duplicate_ratio = duplicate_pairs / (len(hashes) - 1)
    else:
        duplicate_ratio = 1.0
    unique_ratio = len(set(hashes)) / len(hashes)
    return {
        "frames": len(ims),
        "avg_brightness": round(sum(brightness) / len(brightness), 2),
        "near_black_ratio": round(near_black, 4),
        "adjacent_diff": round(sum(diffs) / len(diffs), 3) if diffs else 0.0,
        "duplicate_ratio": round(duplicate_ratio, 4),
        "hash_unique_ratio": round(unique_ratio, 4),
    }


def score_video(meta, fm, mode, expected_duration, expected_shots, actual_shots):
    streams = meta.get("streams") or []
    fmt = meta.get("format") or {}
    duration = float(fmt.get("duration") or 0)
    size = int(fmt.get("size") or 0)
    video = next((s for s in streams if s.get("codec_type") == "video"), None)
    audio = next((s for s in streams if s.get("codec_type") == "audio"), None)

    score = 100.0
    reasons = []

    if not video:
        score -= 100
        reasons.append("NO_VIDEO_STREAM")
    if mode == "final" and not audio:
        score -= 25
        reasons.append("NO_AUDIO_STREAM")
    if size < (100000 if mode == "clip" else 250000):
        score -= 25
        reasons.append("FILE_TOO_SMALL")
    if duration <= 0:
        score -= 100
        reasons.append("INVALID_DURATION")
    if expected_duration > 0 and duration < expected_duration * 0.90:
        score -= 30
        reasons.append("DURATION_TOO_SHORT")
    if fm["near_black_ratio"] > 0.12:
        score -= 40
        reasons.append("TOO_MANY_DARK_FRAMES")
    if fm["duplicate_ratio"] > (0.82 if mode == "clip" else 0.72):
        score -= 32
        reasons.append("VISUAL_REPETITION_HIGH")
    elif fm["duplicate_ratio"] > (0.65 if mode == "clip" else 0.55):
        score -= 14
        reasons.append("VISUAL_REPETITION_MEDIUM")
    if fm["adjacent_diff"] < (0.75 if mode == "clip" else 1.1):
        score -= 28
        reasons.append("MOTION_TOO_LOW")
    elif fm["adjacent_diff"] < (1.4 if mode == "clip" else 1.8):
        score -= 10
        reasons.append("MOTION_LOW")
    if fm["hash_unique_ratio"] < (0.35 if mode == "clip" else 0.45):
        score -= 18
        reasons.append("FRAME_DIVERSITY_LOW")
    if mode == "final" and expected_shots > 0 and actual_shots < expected_shots:
        score -= 35
        reasons.append("SHOT_COUNT_BELOW_PLAN")

    score = max(0.0, min(100.0, score))
    return score, reasons, duration, size, bool(video), bool(audio)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--video", required=True)
    ap.add_argument("--mode", choices=("clip", "final"), default="final")
    ap.add_argument("--expected-duration", type=float, default=0)
    ap.add_argument("--expected-shots", type=int, default=0)
    ap.add_argument("--actual-shots", type=int, default=0)
    ap.add_argument("--min-score", type=float, default=62)
    ap.add_argument("--output")
    args = ap.parse_args()

    meta = probe(args.video)
    duration = float((meta.get("format") or {}).get("duration") or 0)
    interval = 0.35 if args.mode == "clip" else max(0.75, min(2.0, duration / 18.0 if duration else 1.0))

    with tempfile.TemporaryDirectory(prefix="djaeger-quality-") as td:
        frames = extract_frames(args.video, td, interval)
        fm = frame_metrics(frames)

    score, reasons, duration, size, has_video, has_audio = score_video(
        meta, fm, args.mode, args.expected_duration, args.expected_shots, args.actual_shots
    )
    passed = score >= args.min_score and "NO_VIDEO_STREAM" not in reasons and "INVALID_DURATION" not in reasons
    out = {
        "state": "PASS" if passed else "FAIL",
        "engine": "ARTISTIC_QUALITY_GATE_V1",
        "mode": args.mode,
        "score": round(score, 2),
        "min_score": args.min_score,
        "duration_sec": round(duration, 3),
        "size_bytes": size,
        "has_video": has_video,
        "has_audio": has_audio,
        "expected_duration_sec": args.expected_duration,
        "expected_shots": args.expected_shots,
        "actual_shots": args.actual_shots,
        "visual": fm,
        "reasons": reasons,
    }
    body = json.dumps(out, indent=2, sort_keys=True)
    if args.output:
        Path(args.output).write_text(body + "\n", encoding="utf-8")
    print(body)
    raise SystemExit(0 if passed else 2)


if __name__ == "__main__":
    main()
