# DJAEGER WORK — Handover / Single Source of Truth

Updated: 2026-09-24

## Scope
This file is ONLY for DJAEGER WORK / HERMES WORK. Do not mix it with DJAEGER Gaming.

## Current runtime
- Stable channel: v2.5.43-youtube-analytics-playground
- Bundle: HERMES_WORK_RUNTIME_v2.5.43_YOUTUBE_ANALYTICS_PLAYGROUND.zip
- Runtime auto-update path: signed/pinned release channel -> trusted remote maintenance -> Redmi 5A.
- Live Railway evidence on 2026-09-23: remote update returned INSTALLED from v2.5.40-snapshot-integrity to v2.5.41-dashboard-state-sync.
- No reboot is required by the v2.5.41 manifest.

## Dashboard
- Android dashboard version: 1.4.3 (versionCode 143).
- Signed stable dashboard APK is published through release/dashboard/ and installed in-place on Redmi Note 8 Pro.
- Runtime updates and dashboard APK updates remain separate channels; the dashboard stable APK channel can now be fetched and installed remotely through the authorized terminal without uninstalling app data.
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
- Runtime v2.5.43 accepts the existing WEB OAuth client's registered redirect https://developers.google.com/oauthplayground through POST /api/work/youtube/oauth/upgrade-analytics.
- Dashboard v1.4.3 can receive the OAuth Playground callback URL through Android ACTION_SEND, extract the authorization code locally, exchange it through Redmi 5A, and trigger feedback sync without exposing the authorization code or refresh token in chat/log output.
- Google consent was completed for moclomper@gmail.com after verifying the only new permission shown was read-only YouTube Analytics reporting.
- Live capability verification: analytics_consent_required=false, yt_analytics_readonly=true, youtube_force_ssl=true, scope_count=3, secrets_exposed=false.
- The upgraded refresh token remains stored only in the Redmi 5A OAuth vault.

## YouTube feedback
- Basic YouTube feedback is connected and has real performance records.
- Analytics scope is now legitimately connected. Latest direct status: state=LEARNING, yt_analytics_readonly=true, analytics_consent_required=false, retention=DATA_PENDING, ctr=DATA_PENDING, records=5, last_sync=2026-09-24T02:42:55Z.
- Dashboard WAWASAN correctly shows TERHUBUNG and 13 views. Retention/CTR/watch-time/best-topic remain BELUM ADA DATA until YouTube actually supplies those metrics.
- DATA_PENDING is not an OAuth failure and must not be converted to fabricated zero values.

## Validation
- v2.5.43 release workflow passed source validation and ARMv7 runtime build.
- Runtime remote maintenance installed v2.5.43 and post-handoff live verification passed: auto updater reports UP_TO_DATE / HANDOFF_VERIFIED.
- Dashboard v1.4.3 signed build passed APK identity/signature verification and Android package manager verified versionCode 143 / versionName 1.4.3 after remote in-place install.
- OAuth Playground callback handoff was validated live; direct runtime capabilities report yt_analytics_readonly=true and analytics_consent_required=false.
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


## Live finalization checkpoint — 2026-09-24
- Dashboard APK v1.4.3 SIGNED was remotely installed in-place on Redmi Note 8 Pro via the authorized terminal; package manager verified versionCode 143 / versionName 1.4.3. Existing app data was preserved (no uninstall, no reboot).
- A stable dashboard APK channel now exists at release/dashboard/ so future signed dashboard builds can be fetched and installed without asking the user to manually download the artifact.
- Auto Studio checkpoint is newer than the earlier scene-4 note: scenes 1-4 are persisted as ai_01.mp4 through ai_04.mp4.
- Latest provider wait is WAIT_QUOTA on scene 5, attempt 1, retry_at=2026-09-24T03:01:14Z (10:01:14 WIB). publication_invariant remains BLOCKED until every required AI-video scene passes.
- The next normal cron after that retry boundary is 2026-09-24T04:23:00Z (11:23 WIB).
- YouTube Analytics consent gate is closed: the upgraded token has yt-analytics.readonly and youtube.force-ssl; direct feedback status is DATA_PENDING rather than CONSENT_REQUIRED. No further OAuth action is required.
