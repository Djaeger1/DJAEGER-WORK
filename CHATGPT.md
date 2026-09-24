# DJAEGER WORK — Handover / Single Source of Truth

Updated: 2026-09-24

## Scope
This file is ONLY for DJAEGER WORK / HERMES WORK. Do not mix it with DJAEGER Gaming.

## Current runtime
- Stable channel: v2.6.0-creative-director
- Bundle: HERMES_WORK_RUNTIME_v2.6.0_CREATIVE_DIRECTOR.zip
- Previous rollback release: v2.5.43-youtube-analytics-playground
- Live Redmi 5A auto-updater verification on 2026-09-24: UP_TO_DATE, reason=AUTO_UPDATE_SUCCESS, SHA256 integrity enabled, rollback enabled.
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
- Current engine: AUTO_STUDIO_V4_CREATIVE.
- AI-video remains mandatory; vector-only publication remains forbidden.
- Character Bible V2 defaults to Nara + Pip for a cleaner recurring cast.
- Rendering is shot-based rather than one 1-second clip looped across a long scene.
- No `-stream_loop -1` publication path remains in Auto Studio V4.
- Long teaching/interactive scenes are split into multiple explicit shots with concrete subjects/actions/camera plans.
- AI-video generation requests 2-second clips, validates each shot, selectively retries only failed shots with a new seed, and preserves successful checkpoints.
- ARTISTIC_QUALITY_GATE_V1 checks technical validity plus sampled-frame brightness, motion, repetition/duplicate ratio, frame diversity, duration, audio, and planned-vs-successful shot counts.
- Final publication requires quality PASS, artistic_quality_score >= 62, Character Bible V2, DJAEGER_STUDIO_V4_CREATIVE, every required AI shot successful, and no final vector-only scene.
- ZeroGPU quota/provider exhaustion remains a WAIT state with resumable checkpoint/backoff.

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


## Creative Director v2.6 final state — 2026-09-24
- User rejected the old output quality; audit proved V3 generated ~1 second of AI motion per scene and repeated it with FFmpeg to fill 5–10 second scenes. The old gate was technically valid but artistically insufficient.
- Creative Research V3 is now live. Existing Google/YouTube Suggest demand discovery is retained as the first layer.
- YOUTUBE_BENCHMARK_V1 is live and verified against the existing YouTube OAuth token. First verified research run: 46 suggestion sources checked, 200 candidates found, 18 public YouTube benchmark samples across 3 benchmark queries, 0 benchmark errors, 0 HERMES neurons.
- Benchmark records include public video ID/title/channel/published time/duration/views/view velocity/thumbnail URL/made-for-kids flag. Benchmark is cached daily and failure/quota does not destroy the base research loop.
- CREATIVE_DIRECTOR_V1 is live at /api/work/creative. It combines demand signals, public benchmark metadata when available, category learning-format rules, and own-channel performance adjustment into a Creative Brief.
- Creative Brief includes hook strategy, character strategy, visual style, pacing, interaction strategy, explicit learning subjects, production rules, benchmark sample/duration/velocity, and research signals.
- Verified example for planner 830c43a839ea (belajar angka anak paud): subjects are "satu apel merah", "dua bola biru", "tiga bintang kuning"; pacing explicitly forbids filling long scenes by looping a one-second motion.
- SCRIPT_ENGINE_V2_CREATIVE is live. Pending legacy scripts migrate automatically; published historical scripts are preserved. Verified planner 830c43a839ea regenerated with explicit subjects and per-scene shot plans.
- PRODUCTION_PACK_V2_CREATIVE is live. Verified pack includes creative_brief.json plus script/narration/visual prompts/captions/metadata/manifest.
- Semantic Character Bible V2 keyframes are text-free for AI generation; readable captions are composited afterward to avoid warped model-generated text.
- Main GitHub release/ARMv7 CI passed and stable v2.6.0 was installed by the normal SHA256-verified updater without flash/reboot.
- YouTube OAuth/Analytics survived the upgrade: analytics_consent_required=false, yt_analytics_readonly=true, scope_count=3, secrets_exposed=false. Channel remains FEEDBACK_CONNECTED.
- Today's daily render target was already met by the final V3 video before v2.6 deployment. Therefore the V4 renderer has passed source/CI/self-tests but HAS NOT YET produced a real full V4 video. Do not claim visual quality is empirically validated until the first normal V4 render completes and the user/result audit confirms it.
- Do not reset the daily target merely to force a V4 render or waste ZeroGPU quota. The next normal production cycle should use V4 automatically.
- The old scene-5 WAIT_QUOTA monitor is obsolete; the hourly DJAEGER Work monitor was updated to track V4 Creative Director/multi-shot/artistic-gate completion only.
- Redmi 5A is directly connected through Remote Desktop Commander as Redmi-5A and can be audited/maintained without asking the user to type HERMES terminal commands for normal operations.
- Storage cleanup on 2026-09-24 reduced /data/adb/hermes_work from about 560 MB to about 110 MB by removing obsolete releases/update ZIPs while preserving current+previous rollback. At that point /data improved from ~89% used (~1.0 GB free) to ~85% used (~1.5 GB free).
- Hardware note: Redmi 5A is operationally stable, but eMMC lifetime registers read 0x08/0x08 with pre_eol_info=01; avoid unnecessary write amplification and do not let releases/logs accumulate indefinitely.
