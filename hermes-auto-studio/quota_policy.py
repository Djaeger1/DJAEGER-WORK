#!/usr/bin/env python3
"""Quota-aware planning for the public Hugging Face ZeroGPU Wan 2.2 Space.

The current Space computes its ZeroGPU reservation from the requested duration,
image size and inference steps. This planner mirrors the published formula for
the 832x480 landscape path used by HERMES keyframes, then keeps a safety reserve.

It never attempts to bypass provider quotas. When the available profile cannot
fit a job, the renderer stops before spending provider quota.
"""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path


ANON_BUDGET = 105
AUTH_BUDGET = 290
PROVIDER_QUOTA_ANON = 120
PROVIDER_QUOTA_FREE = 300
LANDSCAPE_W = 832
LANDSCAPE_H = 480
MIN_DURATION = 0.8


def estimate(duration: float, steps: int) -> int:
    frames = 1 + max(8, min(80, int(round(duration * 16))))
    factor = (frames * LANDSCAPE_W * LANDSCAPE_H) / (81 * 832 * 624)
    step_duration = 15 * (factor ** 1.5)
    return math.ceil(10 + int(steps) * step_duration)


def raw_scene_shots(scene: dict) -> int:
    explicit = scene.get("shots")
    if isinstance(explicit, list) and explicit:
        return max(1, len(explicit))
    purpose = str(scene.get("purpose", ""))
    return 2 if purpose in {"TEACH_1", "TEACH_2", "INTERACTIVE_RECALL"} else 1


def pick_profile(authenticated: bool, scene_count: int, scene_raw_shots: list[int]) -> dict:
    if authenticated:
        budget = AUTH_BUDGET
        quota = PROVIDER_QUOTA_FREE
        steps = 4
        duration = 2.0
        max_per_scene = 2
        max_total = 12
        max_retries = 1
        mode = "AUTHENTICATED_FREE_RESERVE"
    else:
        budget = ANON_BUDGET
        quota = PROVIDER_QUOTA_ANON
        steps = 2
        duration = 2.0
        max_per_scene = 1
        max_total = 6
        max_retries = 0
        mode = "ANONYMOUS_COMPACT_RESERVE"

    raw_shots = sum(scene_raw_shots)
    # Preserve explicit multi-shot scenes only while the whole job fits the profile.
    effective_counts = [min(max(1, n), max_per_scene) for n in scene_raw_shots]
    effective_shots = sum(effective_counts)
    if effective_shots > max_total:
        max_per_scene = 1
        effective_counts = [1] * scene_count
        effective_shots = scene_count

    # Fit all scenes before allowing any retries. Shrink clip duration first,
    # then inference steps, rather than silently dropping AI-video coverage.
    candidates = [
        (duration, steps),
        (1.8, steps),
        (1.6, steps),
        (1.5, steps),
        (1.4, steps),
        (1.2, steps),
        (1.0, steps),
        (0.9, steps),
        (0.8, steps),
        (1.5, 1),
        (1.2, 1),
        (1.0, 1),
        (0.9, 1),
        (0.8, 1),
    ]
    seen = set()
    chosen = None
    for d, st in candidates:
        key = (float(d), int(st))
        if key in seen:
            continue
        seen.add(key)
        per = estimate(d, st)
        total = effective_shots * per
        retry_total = total + (per if max_retries else 0)
        if total <= budget and retry_total <= budget:
            chosen = (float(d), int(st), per, total, max_retries)
            break
    if chosen is None:
        # We still return a deterministic fail-closed plan.
        d, st = candidates[-1]
        per = estimate(d, st)
        chosen = (float(d), int(st), per, effective_shots * per, 0)

    d, st, per, total, retries = chosen
    fits = total <= budget and total <= quota and retries >= 0
    return {
        "mode": mode,
        "authenticated": authenticated,
        "provider_daily_quota_seconds": quota,
        "planner_budget_seconds": budget,
        "scene_count": scene_count,
        "raw_planned_shots": raw_shots,
        "effective_planned_shots": effective_shots,
        "duration_seconds": d,
        "steps": st,
        "estimated_seconds_per_shot": per,
        "estimated_total_seconds": total,
        "max_shots_per_scene": max_per_scene,
        "max_total_shots": max_total,
        "max_selective_retries": retries,
        "fits": fits,
        "policy": "NO_QUOTA_BYPASS_CHECKPOINTED_RESUME",
    }


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--feed", required=True)
    ap.add_argument("--authenticated", action="store_true")
    args = ap.parse_args()

    data = json.loads(Path(args.feed).read_text(encoding="utf-8"))
    scenes = data.get("job", {}).get("scenes", [])
    if not isinstance(scenes, list) or not scenes:
        raise SystemExit("QUOTA_PLAN_FAIL no scenes")

    scene_raw_shots = [raw_scene_shots(s) if isinstance(s, dict) else 1 for s in scenes]
    plan = pick_profile(bool(args.authenticated), len(scenes), scene_raw_shots)
    print(json.dumps(plan, separators=(",", ":")))


if __name__ == "__main__":
    main()
