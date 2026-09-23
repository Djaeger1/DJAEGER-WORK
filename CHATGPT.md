# DJAEGER WORK — Handover / Single Source of Truth

Updated: 2026-09-23

## Scope
This file is ONLY for DJAEGER WORK / HERMES WORK. Do not mix it with DJAEGER Gaming.

## Current runtime
- Stable channel: v2.5.42-youtube-analytics-consent
- Bundle: HERMES_WORK_RUNTIME_v2.5.42_YOUTUBE_ANALYTICS_CONSENT.zip
- Runtime auto-update path: signed/pinned release channel -> trusted remote maintenance -> Redmi 5A.
- Live Railway evidence on 2026-09-23: remote update returned INSTALLED from v2.5.40-snapshot-integrity to v2.5.41-dashboard-state-sync.
- No reboot is required by the v2.5.41 manifest.

## Dashboard
- Android dashboard version: 1.4.1 (versionCode 141).
- Signed GitHub Actions artifact: DJAEGER-WORK-Native-v1.4.1-SIGNED.
- Runtime v2.5.41 is already live on Redmi 5A, but the Android dashboard APK is a separate client package and is not installed by the runtime auto-update channel. The UI fixes become visible only after v1.4.0 is installed on the phone that runs the dashboard.
- Planner no longer uses hard-coded BELUM TERHUBUNG placeholders for scripts/production/upload.
- Planner consumes /api/work/dashboard for scripts_ready, live Studio state, and YouTube uploaded_total.
- Insights treats FEEDBACK_CONNECTED as a healthy connected state.
- Missing Analytics values display as BELUM ADA DATA rather than implying zero.
- BELUM DIPRODUKSI was renamed BELUM RENDER and now uses the strict pre-render count.

## Runtime dashboard contract
- New endpoint: GET /api/work/dashboard
- Engine: DASHBOARD_STATE_V1
- Aggregates planner, Studio, YouTube upload/publication, channel, feedback, and knowledge state.
- Knowledge produced count means validated Auto Studio renders, not performance-record count.
- Uploaded count means YouTube registry entries.
- not_rendered counts only planner stages before final render.
- WAIT_QUOTA / WAIT_PROVIDER are first-class Studio states.

## Auto Studio
- AI-video remains mandatory for required moving scenes.
- Vector-only publication remains forbidden.
- ZeroGPU quota/provider exhaustion is a WAIT state, not a workflow failure.
- AI clips are checkpointed per scene in a prerelease checkpoint.
- Proven checkpoint for planner 8535c6ee8274: scenes 1-3 were saved; scene 4 entered WAIT_QUOTA.
- Retry uses backoff and resumes from the missing scene rather than spending quota again on completed scenes.

## YouTube Analytics consent
- Runtime v2.5.42 adds POST /api/work/youtube/oauth/upgrade-analytics.
- Dashboard v1.4.1 adds one-tap "AKTIFKAN YOUTUBE ANALYTICS".
- Flow uses state + PKCE and a temporary 127.0.0.1 loopback callback; client secret and refresh token remain stored on Redmi 5A.
- Google user consent is still mandatory and must not be bypassed.
- After successful consent, the dashboard requests /api/work/youtube/feedback so retention/CTR/watch-time can populate from real Analytics data.
- Google documents loopback redirects for installed apps but deprecates mobile loopback support; if the current OAuth client rejects it, use a supported Google OAuth client/redirect flow rather than weakening validation.

## YouTube feedback
- Basic YouTube feedback is connected and has real performance records.
- Latest live evidence showed analytics_consent_required=true and yt_analytics_readonly=false.
- Retention and CTR must stay unavailable until the YouTube Analytics OAuth scope is granted legitimately.
- Never fabricate retention, CTR, watch-time, or best-topic values.

## Validation
- v2.5.42 release workflow passed source validation and ARMv7 runtime build.
- Runtime remote maintenance accepted installation from v2.5.41 to v2.5.42; post-handoff live confirmation should be checked after the temporary device-tunnel restart.
- Dashboard v1.4.1 signed build passed APK identity/signature verification.
- ARMv7 runtime compiled successfully.
- Dashboard v1.4.0 release APK compiled successfully.
- Historical safety/telemetry/process CI was repaired to validate stable invariants rather than obsolete hard-coded release versions.
- Latest PR validation passed: self-enforced quarantine, remote pinned self-update, CPU thermal telemetry, RAM forensics, process convergence, and emergency cold-start checks.
- Main release workflow passed after formatting-safe validator repair.

## Non-negotiable boundaries
- Keep DJAEGER WORK separate from DJAEGER Gaming.
- Do not publish vector-only fallback as completed AI video.
- Do not invent unavailable YouTube Analytics metrics.
- Preserve signed/pinned runtime update verification, rollback, thermal/resource guards, and DATA_ONLY bridge behavior.
- Do not store raw OAuth tokens, API keys, remote keys, or other secrets in this file.
