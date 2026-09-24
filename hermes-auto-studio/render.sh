#!/usr/bin/env bash
set -euo pipefail

STUDIO_FEED="${STUDIO_FEED:-https://hermes-work-chatgpt-relay-v3-production.up.railway.app/studio-feed}"
mkdir -p /tmp/hermes-studio
FEED=/tmp/hermes-studio/feed.json

code="$(curl -sS -o "$FEED" -w '%{http_code}' --max-time 60 "$STUDIO_FEED" || true)"
if [ "$code" != "200" ]; then
  echo "No fresh Studio feed (HTTP $code)."
  exit 0
fi

id="$(jq -r '.job.planner_id // empty' "$FEED")"
tag="$(jq -r '.job.render_tag // empty' "$FEED")"
if [ -z "$id" ] || [ -z "$tag" ]; then
  echo "No render job."
  exit 0
fi

CHECKPOINT_TAG="${tag}-checkpoint"
WAIT_STATE="/tmp/hermes-studio/provider-wait.json"
WAIT_ATTEMPTS=0
HAS_WAIT_STATE=0
PROVIDER_QUOTA_BACKOFF_SECONDS="${PROVIDER_QUOTA_BACKOFF_SECONDS:-14400}"
PROVIDER_QUOTA_BACKOFF_MAX_SECONDS="${PROVIDER_QUOTA_BACKOFF_MAX_SECONDS:-43200}"
PROVIDER_TRANSIENT_BACKOFF_SECONDS="${PROVIDER_TRANSIENT_BACKOFF_SECONDS:-3600}"
PROVIDER_TRANSIENT_BACKOFF_MAX_SECONDS="${PROVIDER_TRANSIENT_BACKOFF_MAX_SECONDS:-14400}"

ensure_checkpoint_release() {
  if gh release view "$CHECKPOINT_TAG" --repo "$GITHUB_REPOSITORY" >/dev/null 2>&1; then
    return 0
  fi
  gh release create "$CHECKPOINT_TAG" \
    --repo "$GITHUB_REPOSITORY" \
    --target "${GITHUB_SHA:-main}" \
    --title "HERMES Auto Studio checkpoint $id" \
    --notes "Internal resumable checkpoint only. NOT publishable. publication_invariant=BLOCKED until the final AI-video quality gate passes." \
    --prerelease >/dev/null
}

record_provider_wait() {
  local state="$1"
  local scene="$2"
  local base max shift delay now retry retry_iso
  case "$state" in
    WAIT_QUOTA)
      base="$PROVIDER_QUOTA_BACKOFF_SECONDS"
      max="$PROVIDER_QUOTA_BACKOFF_MAX_SECONDS"
      ;;
    *)
      base="$PROVIDER_TRANSIENT_BACKOFF_SECONDS"
      max="$PROVIDER_TRANSIENT_BACKOFF_MAX_SECONDS"
      ;;
  esac
  WAIT_ATTEMPTS=$((WAIT_ATTEMPTS+1))
  shift=$((WAIT_ATTEMPTS-1))
  [ "$shift" -le 2 ] || shift=2
  delay=$((base << shift))
  [ "$delay" -le "$max" ] || delay="$max"
  now="$(date -u +%s)"
  retry=$((now+delay))
  retry_iso="$(date -u -d "@$retry" +'%Y-%m-%dT%H:%M:%SZ')"
  jq -n \
    --arg state "$state" \
    --arg planner_id "$id" \
    --arg render_tag "$tag" \
    --arg provider "$AI_VIDEO_SPACE" \
    --arg retry_at "$retry_iso" \
    --argjson retry_after_epoch "$retry" \
    --argjson attempts "$WAIT_ATTEMPTS" \
    --argjson scene "$scene" \
    '{
      state:$state,
      planner_id:$planner_id,
      render_tag:$render_tag,
      provider:$provider,
      blocked_scene:$scene,
      attempts:$attempts,
      retry_after_epoch:$retry_after_epoch,
      retry_at:$retry_at,
      publication_invariant:"BLOCKED",
      final_vector_video_scenes:0
    }' > "$WAIT_STATE"
  ensure_checkpoint_release
  gh release upload "$CHECKPOINT_TAG" "$WAIT_STATE" --repo "$GITHUB_REPOSITORY" --clobber >/dev/null
  HAS_WAIT_STATE=1
  echo "AUTO_STUDIO_WAIT state=$state planner_id=$id scene=$scene attempt=$WAIT_ATTEMPTS retry_at=$retry_iso publication_invariant=BLOCKED"
}

clear_provider_wait() {
  if [ "$HAS_WAIT_STATE" != "1" ]; then
    WAIT_ATTEMPTS=0
    return 0
  fi
  local now_iso
  now_iso="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
  jq -n \
    --arg planner_id "$id" \
    --arg render_tag "$tag" \
    --arg provider "$AI_VIDEO_SPACE" \
    --arg cleared_at "$now_iso" \
    '{
      state:"CLEARED",
      planner_id:$planner_id,
      render_tag:$render_tag,
      provider:$provider,
      attempts:0,
      retry_after_epoch:0,
      cleared_at:$cleared_at,
      publication_invariant:"BLOCKED",
      final_vector_video_scenes:0
    }' > "$WAIT_STATE"
  ensure_checkpoint_release
  gh release upload "$CHECKPOINT_TAG" "$WAIT_STATE" --repo "$GITHUB_REPOSITORY" --clobber >/dev/null || true
  HAS_WAIT_STATE=0
  WAIT_ATTEMPTS=0
}

REBUILD_EXISTING=0
if gh release view "$tag" --repo "$GITHUB_REPOSITORY" >/dev/null 2>&1; then
  oldmeta="/tmp/hermes-studio/existing-metadata.json"
  rm -f "$oldmeta"
  gh release download "$tag" --repo "$GITHUB_REPOSITORY" --dir /tmp/hermes-studio --pattern 'metadata.json' --clobber >/dev/null 2>&1 || true
  if [ -s /tmp/hermes-studio/metadata.json ]; then
    mv /tmp/hermes-studio/metadata.json "$oldmeta"
  fi
  old_quality="$(jq -r '.quality_gate // "LEGACY_UNVERIFIED"' "$oldmeta" 2>/dev/null || echo LEGACY_UNVERIFIED)"
  old_score="$(jq -r '.artistic_quality_score // 0' "$oldmeta" 2>/dev/null || echo 0)"
  old_bible="$(jq -r '.character_bible // ""' "$oldmeta" 2>/dev/null || true)"
  old_generation="$(jq -r '.render_generation // ""' "$oldmeta" 2>/dev/null || true)"
  old_invariant="$(jq -r '.publication_invariant // ""' "$oldmeta" 2>/dev/null || true)"
  old_required="$(jq -r '.required_ai_video_shots // 0' "$oldmeta" 2>/dev/null || echo 0)"
  old_success="$(jq -r '.successful_ai_video_shots // 0' "$oldmeta" 2>/dev/null || echo 0)"
  old_final_vector="$(jq -r '.final_vector_video_scenes // -1' "$oldmeta" 2>/dev/null || echo -1)"
  if [ "$old_quality" = "PASS" ] && awk "BEGIN{exit !($old_score >= 62)}" && [ "$old_bible" = "DJAEGER_WORK_KIDS_V2" ] && [ "$old_generation" = "DJAEGER_STUDIO_V4_CREATIVE" ] && [ "$old_invariant" = "PASS" ] && [ "$old_required" -gt 0 ] && [ "$old_success" -eq "$old_required" ] && [ "$old_final_vector" -eq 0 ]; then
    echo "Current Creative Director V4 release already exists for $tag."
    exit 0
  fi
  REBUILD_EXISTING=1
  echo "Rebuilding legacy/low-quality release for $tag."
fi

if gh release view "$CHECKPOINT_TAG" --repo "$GITHUB_REPOSITORY" >/dev/null 2>&1; then
  rm -f "$WAIT_STATE"
  gh release download "$CHECKPOINT_TAG" --repo "$GITHUB_REPOSITORY" --dir /tmp/hermes-studio --pattern 'provider-wait.json' --clobber >/dev/null 2>&1 || true
  if [ -s "$WAIT_STATE" ]; then
    wait_planner="$(jq -r '.planner_id // ""' "$WAIT_STATE" 2>/dev/null || true)"
    wait_state="$(jq -r '.state // ""' "$WAIT_STATE" 2>/dev/null || true)"
    WAIT_ATTEMPTS="$(jq -r '.attempts // 0' "$WAIT_STATE" 2>/dev/null || echo 0)"
    retry_after="$(jq -r '.retry_after_epoch // 0' "$WAIT_STATE" 2>/dev/null || echo 0)"
    case "$WAIT_ATTEMPTS" in ''|*[!0-9]*) WAIT_ATTEMPTS=0 ;; esac
    case "$retry_after" in ''|*[!0-9]*) retry_after=0 ;; esac
    if [ "$wait_planner" = "$id" ] && { [ "$wait_state" = "WAIT_QUOTA" ] || [ "$wait_state" = "WAIT_PROVIDER" ]; }; then
      HAS_WAIT_STATE=1
      now_epoch="$(date -u +%s)"
      if [ "$retry_after" -gt "$now_epoch" ]; then
        retry_at="$(jq -r '.retry_at // ""' "$WAIT_STATE" 2>/dev/null || true)"
        echo "AUTO_STUDIO_BACKOFF state=$wait_state planner_id=$id retry_at=$retry_at attempts=$WAIT_ATTEMPTS"
        exit 0
      fi
    fi
  fi
fi

echo "Installing zero-card render tools..."
sudo apt-get update -qq
sudo apt-get install -y -qq ffmpeg jq imagemagick espeak-ng fonts-dejavu-core
python3 -m pip install --quiet --disable-pip-version-check --break-system-packages edge-tts "gradio_client>=1.6,<2" pillow || {
  echo "Required AI-video client dependencies unavailable."
  exit 1
}

rm -rf studio
mkdir -p studio/scenes
cp "$FEED" studio/feed.json

if gh release view "$CHECKPOINT_TAG" --repo "$GITHUB_REPOSITORY" >/dev/null 2>&1; then
  gh release download "$CHECKPOINT_TAG" --repo "$GITHUB_REPOSITORY" --dir studio/scenes --pattern 'ai_*.mp4' --clobber >/dev/null 2>&1 || true
fi

LANG_CODE="$(jq -r '.job.language // "id"' studio/feed.json)"
TITLE="$(jq -r '.job.video_title // .job.topic // "HERMES WORK"' studio/feed.json)"
CHARACTER_BIBLE="${CHARACTER_BIBLE:-hermes-auto-studio/character-bible.json}"
CHANNEL_SEED="${CHANNEL_SEED:-314159}"
AI_VIDEO_SPACE="${AI_VIDEO_SPACE:-zerogpu-aoti/wan2-2-fp8da-aoti-faster}"
if [ ! -s "$CHARACTER_BIBLE" ]; then
  echo "Character Bible missing: $CHARACTER_BIBLE"
  exit 1
fi
STYLE_PROMPT="$(jq -r '.style.prompt' "$CHARACTER_BIBLE")"
CHARACTER_ANCHORS="$(jq -r '[.characters[] | (.name + ": " + .visual_anchor)] | join(". ")' "$CHARACTER_BIBLE")"

COUNT="$(jq '.job.scenes | length' studio/feed.json)"
if [ "$COUNT" -lt 1 ]; then
  echo "No scenes in job"
  exit 1
fi

VOICE=""
if command -v edge-tts >/dev/null 2>&1; then
  edge-tts --list-voices >/tmp/hermes-studio/voices.txt 2>/dev/null || true
  if [ "$LANG_CODE" = "id" ]; then
    VOICE="$(awk '$1 ~ /^id-ID-/ && !v {v=$1} END{print v}' /tmp/hermes-studio/voices.txt)"
  else
    VOICE="$(awk '$1 ~ /^en-US-/ && !v {v=$1} END{print v}' /tmp/hermes-studio/voices.txt)"
  fi
fi

: > studio/concat.txt
AI_VIDEO_SCENES=0
AI_VIDEO_SHOTS=0
PLANNED_SHOTS=0
VECTOR_KEYFRAMES=0
BASIC_KEYFRAMES=0
CHECKPOINT_REUSED=0
SELECTIVE_RETRIES=0
FIRST_KEYFRAME=""

clip_quality_ok() {
  local clip="$1"
  local report="$2"
  python3 hermes-auto-studio/quality_critic.py     --video "$clip" --mode clip --expected-duration 1.5     --min-score 40 --output "$report" >/tmp/hermes-studio/clip-quality.log 2>&1
}

for idx in $(seq 0 $((COUNT-1))); do
  n=$((idx+1))
  scene="$(jq -c ".job.scenes[$idx]" studio/feed.json)"
  prompt="$(printf '%s' "$scene" | jq -r '.visual_prompt // .purpose // "friendly preschool educational illustration"')"
  cast="$(jq -r --arg n "$n" '.scene_cast[$n] // .default_cast // ["Nara","Pip"] | join(", ")' "$CHARACTER_BIBLE")"
  prompt="$STYLE_PROMPT. Character Bible: $CHARACTER_ANCHORS. Scene cast: $cast. Only use the named recurring cast for this scene; preserve their exact face, hair, clothes, colors, proportions, and accessories. $prompt"
  voice="$(printf '%s' "$scene" | jq -r '.voice_over // ""')"
  onscreen="$(printf '%s' "$scene" | jq -r '.on_screen_text // ""')"
  scene_dur="$(printf '%s' "$scene" | jq -r '.duration_sec // 7')"
  [ "$scene_dur" -ge 3 ] 2>/dev/null || scene_dur=7

  audio="studio/scenes/scene_$(printf '%02d' "$n").mp3"
  if [ -n "$VOICE" ] && edge-tts --voice "$VOICE" --text "$voice" --write-media "$audio" >/dev/null 2>&1; then
    :
  else
    wav="studio/scenes/scene_$(printf '%02d' "$n").wav"
    if [ "$LANG_CODE" = "id" ]; then evoice=id; else evoice=en-us; fi
    espeak-ng -v "$evoice" -s 145 -w "$wav" "$voice"
    ffmpeg -y -loglevel error -i "$wav" -c:a libmp3lame -q:a 4 "$audio"
  fi

  aud_dur="$(ffprobe -v error -show_entries format=duration -of default=nw=1:nk=1 "$audio" 2>/dev/null | awk '{printf "%.2f",$1+0.35}')"
  dur="$(python3 - "$scene_dur" "$aud_dur" <<'PY'
import sys
a=float(sys.argv[1] or 7)
b=float(sys.argv[2] or 0)
print(f"{max(a,b,3.0):.2f}")
PY
)"

  shot_count="$(printf '%s' "$scene" | jq '(.shots // []) | length')"
  if [ "$shot_count" -lt 1 ]; then
    shot_count=1
  fi
  PLANNED_SHOTS=$((PLANNED_SHOTS+shot_count))
  scene_list="studio/scenes/scene_$(printf '%02d' "$n")_shots.txt"
  : > "$scene_list"

  for sidx in $(seq 0 $((shot_count-1))); do
    sn=$((sidx+1))
    if printf '%s' "$scene" | jq -e '.shots and (.shots|length)>0' >/dev/null 2>&1; then
      shot="$(printf '%s' "$scene" | jq -c ".shots[$sidx]")"
    else
      shot="$(jq -nc --arg p "$prompt" --arg subject "$(printf '%s' "$scene" | jq -r '.subject // .visual_goal // .purpose // "learning object"')" --argjson d "$scene_dur" '{number:1,duration_sec:$d,subject:$subject,action:"clear learning action",camera:"stable medium shot",prompt:$p}')"
    fi
    shotdur="$(printf '%s' "$shot" | jq -r '.duration_sec // 0')"
    [ "$shotdur" -gt 0 ] 2>/dev/null || shotdur="$scene_dur"
    subject="$(printf '%s' "$shot" | jq -r '.subject // "learning object"')"
    action="$(printf '%s' "$shot" | jq -r '.action // "clear learning action"')"
    camera="$(printf '%s' "$shot" | jq -r '.camera // "stable medium shot"')"
    shot_prompt="$(printf '%s' "$shot" | jq -r '.prompt // empty')"
    [ -n "$shot_prompt" ] || shot_prompt="$prompt"

    shot_scene="$(printf '%s' "$scene" | jq -c --arg subject "$subject" --arg goal "$action" '. + {subject:$subject,visual_goal:$goal,on_screen_text:""}')"
    img="studio/scenes/scene_$(printf '%02d' "$n")_shot_$(printf '%02d' "$sn").jpg"
    svg="studio/scenes/scene_$(printf '%02d' "$n")_shot_$(printf '%02d' "$sn").svg"

    echo "Preparing semantic Character Bible keyframe scene=$n shot=$sn subject=$subject"
    if python3 hermes-auto-studio/vector_scene.py --bible "$CHARACTER_BIBLE" --scene "$shot_scene" --topic "$TITLE" --cast "$cast" --number "$n" --output "$svg"       && convert -background none "$svg" -quality 94 "$img"       && identify "$img" >/dev/null 2>&1; then
      VECTOR_KEYFRAMES=$((VECTOR_KEYFRAMES+1))
    else
      BASIC_KEYFRAMES=$((BASIC_KEYFRAMES+1))
      convert -size 1280x720 "gradient:#23395d-#101820" -gravity center -fill white -font DejaVu-Sans-Bold -pointsize 46         -annotate +0+0 "$subject" "$img"
    fi
    [ -n "$FIRST_KEYFRAME" ] || FIRST_KEYFRAME="$img"

    ai_clip="studio/scenes/ai_$(printf '%02d' "$n")_$(printf '%02d' "$sn").mp4"
    quality_json="studio/scenes/quality_$(printf '%02d' "$n")_$(printf '%02d' "$sn").json"
    motion_prompt="$STYLE_PROMPT. Character Bible: $CHARACTER_ANCHORS. Scene cast: $cast. Explicit subject: $subject. Action: $action. Camera: $camera. $shot_prompt. Animate this exact semantic Character Bible keyframe for a real shot with visible natural preschool-friendly motion. Keep every character's face, hair, clothing, colors, proportions and accessories unchanged. Preserve the learning subject. Stable camera unless the shot plan asks otherwise. No morphing, no added characters, no generated text, no repeated gesture loop."

    clip_ready=0
    if [ -s "$ai_clip" ] && ffprobe -v error -show_entries stream=codec_type -of csv=p=0 "$ai_clip" | grep -q video && clip_quality_ok "$ai_clip" "$quality_json"; then
      echo "Reusing validated AI-video checkpoint scene=$n shot=$sn."
      CHECKPOINT_REUSED=$((CHECKPOINT_REUSED+1))
      clip_ready=1
    fi

    if [ "$clip_ready" -ne 1 ]; then
      rm -f "$ai_clip" "$quality_json"
      for attempt in 1 2; do
        seed="$((CHANNEL_SEED+n*100+sn+(attempt-1)*10000))"
        echo "AI-video scene=$n shot=$sn attempt=$attempt provider=$AI_VIDEO_SPACE"
        set +e
        python3 hermes-auto-studio/ai_video_scene.py           --image "$img" --prompt "$motion_prompt" --output "$ai_clip"           --seed "$seed" --duration 2.0 --space "$AI_VIDEO_SPACE"
        ai_rc=$?
        set -e

        if [ "$ai_rc" -eq 75 ]; then
          record_provider_wait "WAIT_QUOTA" "$n"
          exit 0
        elif [ "$ai_rc" -eq 76 ]; then
          record_provider_wait "WAIT_PROVIDER" "$n"
          exit 0
        elif [ "$ai_rc" -ne 0 ]; then
          echo "AI_VIDEO_REQUIRED: scene=$n shot=$sn failed rc=$ai_rc"
          if [ "$attempt" -ge 2 ]; then
            exit 1
          fi
          SELECTIVE_RETRIES=$((SELECTIVE_RETRIES+1))
          continue
        fi

        if [ -s "$ai_clip" ] && ffprobe -v error -show_entries stream=codec_type -of csv=p=0 "$ai_clip" | grep -q video && clip_quality_ok "$ai_clip" "$quality_json"; then
          clip_ready=1
          clear_provider_wait
          break
        fi
        echo "ARTISTIC_CLIP_REJECT scene=$n shot=$sn attempt=$attempt"
        rm -f "$ai_clip"
        if [ "$attempt" -lt 2 ]; then
          SELECTIVE_RETRIES=$((SELECTIVE_RETRIES+1))
        fi
      done
      if [ "$clip_ready" -ne 1 ]; then
        echo "ARTISTIC_CLIP_GATE_FAILED scene=$n shot=$sn; publication blocked."
        exit 1
      fi
      ensure_checkpoint_release
      if ! gh release upload "$CHECKPOINT_TAG" "$ai_clip" --repo "$GITHUB_REPOSITORY" --clobber >/dev/null; then
        echo "AI_VIDEO_CHECKPOINT_FAILED scene=$n shot=$sn; refusing further quota spend without resumability."
        exit 1
      fi
      echo "AI_VIDEO_CHECKPOINT_SAVED scene=$n shot=$sn"
    fi

    AI_VIDEO_SHOTS=$((AI_VIDEO_SHOTS+1))

    clipdur="$(ffprobe -v error -show_entries format=duration -of default=nw=1:nk=1 "$ai_clip" 2>/dev/null || echo 2)"
    read slow pad <<EOF
$(python3 - "$clipdur" "$shotdur" <<'PY'
import sys
clip=max(float(sys.argv[1] or 2),0.2)
target=max(float(sys.argv[2] or 3),0.5)
slow=min(2.0,max(1.0,target/clip))
remain=max(0.0,target-clip*slow)
print(f"{slow:.4f} {remain:.4f}")
PY
)
EOF
    shotseg="studio/scenes/shotseg_$(printf '%02d' "$n")_$(printf '%02d' "$sn").mp4"
    ffmpeg -y -loglevel error -i "$ai_clip" -t "$shotdur"       -vf "setpts=${slow}*PTS,scale=1280:720:force_original_aspect_ratio=increase,crop=1280:720,fps=30,tpad=stop_mode=clone:stop_duration=${pad},format=yuv420p"       -an -c:v libx264 -preset veryfast -crf 21 "$shotseg"
    echo "file '$(basename "$shotseg")'" >> "$scene_list"
  done

  AI_VIDEO_SCENES=$((AI_VIDEO_SCENES+1))

  scene_visual="studio/scenes/scene_visual_$(printf '%02d' "$n").mp4"
  ffmpeg -y -loglevel error -f concat -safe 0 -i "$scene_list" -c copy "$scene_visual"

  textfile="studio/scenes/text_$(printf '%02d' "$n").txt"
  printf '%s' "$onscreen" > "$textfile"
  seg="studio/scenes/seg_$(printf '%02d' "$n").mp4"
  if [ -n "$onscreen" ]; then
    ffmpeg -y -loglevel error -i "$scene_visual" -i "$audio" -t "$dur"       -vf "drawtext=fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:textfile=${textfile}:fontcolor=white:fontsize=42:borderw=3:bordercolor=black@0.55:box=1:boxcolor=black@0.30:boxborderw=16:x=(w-text_w)/2:y=h-text_h-58:enable='between(t,0.35,3.25)',format=yuv420p"       -c:v libx264 -preset veryfast -crf 21 -c:a aac -b:a 144k -af "apad" "$seg"
  else
    ffmpeg -y -loglevel error -i "$scene_visual" -i "$audio" -t "$dur"       -c:v copy -c:a aac -b:a 144k -af "apad" "$seg"
  fi
  echo "file 'scenes/$(basename "$seg")'" >> studio/concat.txt
done

ffmpeg -y -loglevel error -f concat -safe 0 -i studio/concat.txt -c copy studio/final.mp4
if [ -n "$FIRST_KEYFRAME" ] && [ -s "$FIRST_KEYFRAME" ]; then
  convert "$FIRST_KEYFRAME" -resize 1280x720^ -gravity center -extent 1280x720     -fill "rgba(0,0,0,0.48)" -draw "roundrectangle 55,535 1225,685 28,28"     -fill white -font DejaVu-Sans-Bold -pointsize 46 -gravity south -annotate +0+62 "$TITLE"     studio/thumbnail.jpg
else
  ffmpeg -y -loglevel error -ss 1 -i studio/final.mp4 -frames:v 1 studio/thumbnail.jpg
fi
ffprobe -v error -show_entries format=duration,size -of json studio/final.mp4 > studio/probe.json

TARGET_DURATION="$(jq -r '.job.duration_sec // 0' studio/feed.json)"
ACTUAL_DURATION="$(jq -r '.format.duration // 0' studio/probe.json)"
python3 - "$TARGET_DURATION" "$ACTUAL_DURATION" <<'PY'
import sys
target=float(sys.argv[1] or 0)
actual=float(sys.argv[2] or 0)
if target > 0 and actual < target * 0.90:
    raise SystemExit(f"Rendered duration too short: target={target:.2f}s actual={actual:.2f}s")
PY

bytes="$(stat -c%s studio/final.mp4)"
if [ "$bytes" -lt 200000 ]; then
  echo "Rendered MP4 too small"
  exit 1
fi

QUALITY_GATE="PASS"
if [ "$AI_VIDEO_SCENES" -ne "$COUNT" ]; then
  echo "AI-video scene coverage failed: $AI_VIDEO_SCENES/$COUNT scenes"
  exit 1
fi
if [ "$AI_VIDEO_SHOTS" -ne "$PLANNED_SHOTS" ] || [ "$PLANNED_SHOTS" -lt "$COUNT" ]; then
  echo "AI-video shot coverage failed: $AI_VIDEO_SHOTS/$PLANNED_SHOTS shots"
  exit 1
fi

set +e
python3 hermes-auto-studio/quality_critic.py   --video studio/final.mp4 --mode final   --expected-duration "$TARGET_DURATION"   --expected-shots "$PLANNED_SHOTS" --actual-shots "$AI_VIDEO_SHOTS"   --min-score 62 --output studio/quality-report.json
critic_rc=$?
set -e
if [ "$critic_rc" -ne 0 ]; then
  echo "ARTISTIC_QUALITY_GATE_FAILED: refusing publication."
  cat studio/quality-report.json || true
  exit 1
fi
ARTISTIC_SCORE="$(jq -r '.score // 0' studio/quality-report.json)"

jq '{
  state:"RENDERED",
  engine:"AUTO_STUDIO_V4_CREATIVE",
  planner_id:.job.planner_id,
  topic:.job.topic,
  title:.job.video_title,
  description:.job.description,
  hashtags:.job.hashtags,
  render_tag:.job.render_tag,
  visual_provider:"CHARACTER_BIBLE_V2_SEMANTIC_KEYFRAME_TO_WAN_I2V",
  render_generation:"DJAEGER_STUDIO_V4_CREATIVE",
  voice_provider:"EDGE_TTS_OR_ESPEAK_FALLBACK",
  render_provider:"GITHUB_ACTIONS_FFMPEG_MULTISHOT",
  target_duration_sec:(.job.duration_sec // 0),
  card_required:false,
  hermes_ai_used:false,
  external_ai_video_used:true,
  ai_video_provider:"HUGGINGFACE_ZERO_GPU_WAN2_2_AOTI_FAST",
  ai_video_clip_duration_sec:2.0,
  ai_video_strategy:"MULTI_SHOT_NO_STREAM_LOOP_SELECTIVE_RETRY",
  zerogpu_quota_mode:"ANONYMOUS_ZERO_COST_RESUMABLE_CHECKPOINTS",
  neurons_used:0,
  character_bible:"DJAEGER_WORK_KIDS_V2",
  recurring_cast:["Nara","Pip"],
  artistic_quality_gate:"ARTISTIC_QUALITY_GATE_V1",
  repository:"Djaeger1/DJAEGER-WORK"
}' studio/feed.json > studio/metadata.json
jq --arg actual "$ACTUAL_DURATION" --arg quality "$QUALITY_GATE" --arg score "$ARTISTIC_SCORE" --arg space "$AI_VIDEO_SPACE"    --argjson required_scenes "$COUNT" --argjson ai_scenes "$AI_VIDEO_SCENES"    --argjson required_shots "$PLANNED_SHOTS" --argjson ai_shots "$AI_VIDEO_SHOTS"    --argjson vector_keys "$VECTOR_KEYFRAMES" --argjson basic_keys "$BASIC_KEYFRAMES"    --argjson checkpoint_reused "$CHECKPOINT_REUSED" --argjson selective_retries "$SELECTIVE_RETRIES"    '. + {
      actual_duration_sec:($actual|tonumber),
      quality_gate:$quality,
      artistic_quality_score:($score|tonumber),
      ai_video_space:$space,
      required_ai_video_scenes:$required_scenes,
      successful_ai_video_scenes:$ai_scenes,
      required_ai_video_shots:$required_shots,
      successful_ai_video_shots:$ai_shots,
      final_vector_video_scenes:0,
      publication_invariant:(if ($quality=="PASS" and ($score|tonumber)>=62 and $ai_shots==$required_shots and $required_shots>0) then "PASS" else "BLOCKED" end),
      visual_stats:{
        ai_video_scenes:$ai_scenes,
        ai_video_shots:$ai_shots,
        planned_shots:$required_shots,
        checkpoint_reused:$checkpoint_reused,
        selective_retries:$selective_retries,
        semantic_character_bible_keyframes:$vector_keys,
        basic_keyframes:$basic_keys,
        stream_loop_used:false
      }
    }' studio/metadata.json > studio/metadata.json.tmp
mv studio/metadata.json.tmp studio/metadata.json

echo "Rendered: $TITLE"
cat studio/probe.json

if [ "$REBUILD_EXISTING" = "1" ]; then
  gh release upload "$tag" studio/final.mp4 studio/thumbnail.jpg studio/metadata.json studio/probe.json studio/quality-report.json --repo "$GITHUB_REPOSITORY" --clobber
  gh release edit "$tag" --repo "$GITHUB_REPOSITORY" --title "HERMES Auto Studio $id"     --notes "Re-rendered by HERMES AUTO STUDIO V4 Creative Director with Character Bible V2, multi-shot AI video, selective retry, and artistic quality gate. Public ZeroGPU provider, no paid API, 0 HERMES Neurons."
else
  gh release create "$tag" studio/final.mp4 studio/thumbnail.jpg studio/metadata.json studio/probe.json studio/quality-report.json     --repo "$GITHUB_REPOSITORY" --title "HERMES Auto Studio $id"     --notes "Auto-rendered by HERMES AUTO STUDIO V4 Creative Director with Character Bible V2, multi-shot AI video, selective retry, and artistic quality gate. Public ZeroGPU provider, no paid API, 0 HERMES Neurons."
fi

if gh release view "$CHECKPOINT_TAG" --repo "$GITHUB_REPOSITORY" >/dev/null 2>&1; then
  gh release delete "$CHECKPOINT_TAG" --repo "$GITHUB_REPOSITORY" --cleanup-tag --yes >/dev/null 2>&1 || \
    echo "CHECKPOINT_CLEANUP_WARN tag=$CHECKPOINT_TAG"
fi

echo "QUALITY_GATE=$QUALITY_GATE ARTISTIC_SCORE=$ARTISTIC_SCORE AI_VIDEO_SCENES=$AI_VIDEO_SCENES AI_VIDEO_SHOTS=$AI_VIDEO_SHOTS PLANNED_SHOTS=$PLANNED_SHOTS SELECTIVE_RETRIES=$SELECTIVE_RETRIES CHECKPOINT_REUSED=$CHECKPOINT_REUSED"
