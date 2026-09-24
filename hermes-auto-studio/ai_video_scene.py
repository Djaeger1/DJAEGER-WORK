#!/usr/bin/env python3
import argparse
import json
import os
import shutil
import sys
import tempfile
from pathlib import Path

DEFAULT_SPACE = os.getenv("AI_VIDEO_SPACE", "zerogpu-aoti/wan2-2-fp8da-aoti-faster")
NEGATIVE = (
    "static image, still frame, frozen motion, repeated loop, identical repeated gesture, "
    "flicker, jitter, warped face, extra limbs, deformed hands, changing clothes, "
    "changing character identity, duplicate character, unexpected extra character, "
    "watermark, logo, subtitles, generated text, unreadable text, low quality, blur, "
    "camera shake, sudden zoom, morphing object"
)

EXIT_WAIT_QUOTA = 75
EXIT_WAIT_PROVIDER = 76

def classify_provider_error(exc):
    text = str(exc).lower()
    quota_markers = (
        "exceeded your zerogpu runs limit",
        "zerogpu runs limit",
        "zero gpu runs limit",
        "quota exceeded",
        "rate limit",
        "too many requests",
        "http 429",
        "status 429",
        " 429 ",
        "more quota",
    )
    if any(marker in text for marker in quota_markers):
        return "WAIT_QUOTA", EXIT_WAIT_QUOTA

    provider_markers = (
        "service unavailable",
        "temporarily unavailable",
        "space is sleeping",
        "space unavailable",
        "no gpu was available",
        "zerogpu queue",
        "zerogpu queues",
        "higher priority in zerogpu",
        "connection timed out",
        "timed out",
        "timeout",
        "connection reset",
        "connection refused",
        "bad gateway",
        "gateway timeout",
        "http 502",
        "http 503",
        "http 504",
        "status 502",
        "status 503",
        "status 504",
    )
    if any(marker in text for marker in provider_markers):
        return "WAIT_PROVIDER", EXIT_WAIT_PROVIDER
    return None, None

def selftest():
    cases = [
        ("You have exceeded your ZeroGPU runs limit. Authenticate for more quota", "WAIT_QUOTA", EXIT_WAIT_QUOTA),
        ("HTTP 429 Too Many Requests", "WAIT_QUOTA", EXIT_WAIT_QUOTA),
        ("HTTP 503 Service Unavailable", "WAIT_PROVIDER", EXIT_WAIT_PROVIDER),
        ("No GPU was available after 60s. Create a free account to get a higher priority in ZeroGPU queues.", "WAIT_PROVIDER", EXIT_WAIT_PROVIDER),
        ("connection timed out while waiting for queue", "WAIT_PROVIDER", EXIT_WAIT_PROVIDER),
        ("unexpected provider endpoint signature", None, None),
    ]
    for message, expected_state, expected_code in cases:
        state, code = classify_provider_error(RuntimeError(message))
        if state != expected_state or code != expected_code:
            raise SystemExit(
                f"SELFTEST_FAIL message={message!r} got=({state!r},{code!r}) "
                f"expected=({expected_state!r},{expected_code!r})"
            )
    print("AI_VIDEO_PROVIDER_CLASSIFIER_SELFTEST=PASS")


def extract_path(value):
    if isinstance(value, str) and os.path.exists(value):
        return value
    if isinstance(value, dict):
        for k in ("path", "name", "video", "file"):
            v = value.get(k)
            if isinstance(v, str) and os.path.exists(v):
                return v
        for v in value.values():
            p = extract_path(v)
            if p:
                return p
    if isinstance(value, (list, tuple)):
        for v in value:
            p = extract_path(v)
            if p:
                return p
    return None

def generate(image, prompt, output, seed, duration, space):
    from gradio_client import Client, handle_file

    client = Client(space, hf_token=os.getenv("HF_TOKEN") or None, verbose=False)
    endpoint_candidates = ["/generate_video"]
    try:
        api = client.view_api(return_format="dict") or {}
        named = api.get("named_endpoints", {}) if isinstance(api, dict) else {}
        for name in named:
            if name not in endpoint_candidates and "generate" in name.lower():
                endpoint_candidates.append(name)
    except Exception as exc:
        print("AI_VIDEO_API_DISCOVERY_WARN " + repr(exc), file=sys.stderr)

    def call(endpoint):
        if "wan2-2-fp8da-aoti-faster" in space:
            # Wan2.2 AoTI: image, prompt, steps, negative, duration,
            # high-noise CFG, low-noise CFG, seed, randomize.
            return client.predict(
                handle_file(image),
                prompt,
                4,
                NEGATIVE,
                float(duration),
                1.0,
                1.0,
                int(seed),
                False,
                api_name=endpoint,
            )
        # Legacy Wan2.1 fast fallback signature.
        return client.predict(
            handle_file(image),
            prompt,
            512,
            896,
            NEGATIVE,
            float(duration),
            1.0,
            4,
            int(seed),
            False,
            api_name=endpoint,
        )

    last = None
    for endpoint in endpoint_candidates:
        try:
            result = call(endpoint)
            src = extract_path(result)
            if not src:
                raise RuntimeError(f"provider returned no local video file: {type(result).__name__} {result!r}")
            Path(output).parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(src, output)
            return {"ok": True, "space": space, "endpoint": endpoint, "output": output}
        except Exception as exc:
            last = exc
            print(f"AI_VIDEO_PROVIDER_ATTEMPT_FAILED space={space} endpoint={endpoint} error={exc!r}", file=sys.stderr)
    raise RuntimeError(f"AI video provider failed ({space}): {last!r}")

def smoke_keyframe(path):
    # A deterministic, original preschool character card used only to validate that
    # the external video model can animate an image. It is never published.
    from PIL import Image, ImageDraw
    im = Image.new("RGB", (896, 512), (229, 244, 242))
    d = ImageDraw.Draw(im)
    d.ellipse((330, 95, 565, 330), fill=(63, 191, 181), outline=(30, 90, 88), width=8)
    d.ellipse((385, 165, 420, 200), fill=(30, 30, 30))
    d.ellipse((475, 165, 510, 200), fill=(30, 30, 30))
    d.polygon([(445, 220), (420, 250), (470, 250)], fill=(245, 160, 55))
    d.ellipse((390, 250, 505, 290), outline=(30, 90, 88), width=6)
    d.text((270, 385), "DJAEGER WORK AI VIDEO PREFLIGHT", fill=(35, 55, 70))
    im.save(path, quality=92)

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--image")
    ap.add_argument("--prompt")
    ap.add_argument("--output")
    ap.add_argument("--seed", type=int, default=314160)
    ap.add_argument("--duration", type=float, default=2.0)
    ap.add_argument("--space", default=DEFAULT_SPACE)
    ap.add_argument("--smoke", action="store_true")
    ap.add_argument("--selftest", action="store_true")
    args = ap.parse_args()

    if args.selftest:
        selftest()
        return
    if not args.output:
        raise SystemExit("missing --output")

    tmp = None
    try:
        if args.smoke:
            tmp = tempfile.NamedTemporaryFile(suffix=".jpg", delete=False)
            tmp.close()
            smoke_keyframe(tmp.name)
            image = tmp.name
            prompt = (
                "Original friendly turquoise preschool bird mascot gently flaps its wings, "
                "small cheerful body motion, smooth animation, fixed character design, "
                "stable face and colors, clean 2D educational cartoon, no text changes."
            )
        else:
            if not args.image or not os.path.exists(args.image):
                raise SystemExit("missing --image")
            if not args.prompt:
                raise SystemExit("missing --prompt")
            image = args.image
            prompt = args.prompt

        try:
            info = generate(image, prompt, args.output, args.seed, args.duration, args.space)
        except Exception as exc:
            state, exit_code = classify_provider_error(exc)
            if state:
                payload = {
                    "state": state,
                    "space": args.space,
                    "reason": str(exc)[:500],
                }
                print(
                    "AI_VIDEO_WAIT " + json.dumps(payload, separators=(",", ":")),
                    file=sys.stderr,
                )
                raise SystemExit(exit_code)
            raise
        print("AI_VIDEO_OK " + json.dumps(info, separators=(",", ":")))
    finally:
        if tmp:
            try:
                os.unlink(tmp.name)
            except OSError:
                pass

if __name__ == "__main__":
    main()
