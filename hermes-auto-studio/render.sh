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

if gh release view "$tag" --repo "$GITHUB_REPOSITORY" >/dev/null 2>&1; then
  echo "Release already exists for $tag."
  exit 0
fi

echo "Installing zero-card render tools..."
sudo apt-get update -qq
sudo apt-get install -y -qq ffmpeg jq imagemagick espeak-ng fonts-dejavu-core
python3 -m pip install --quiet --disable-pip-version-check --break-system-packages edge-tts || echo "edge-tts unavailable; espeak-ng fallback will be used"

rm -rf studio
mkdir -p studio/scenes
cp "$FEED" studio/feed.json

LANG_CODE="$(jq -r '.job.language // "id"' studio/feed.json)"
TITLE="$(jq -r '.job.video_title // .job.topic // "HERMES WORK"' studio/feed.json)"
CHARACTER_BIBLE="${CHARACTER_BIBLE:-hermes-auto-studio/character-bible.json}"
CHANNEL_SEED="${CHANNEL_SEED:-314159}"
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
for idx in $(seq 0 $((COUNT-1))); do
  n=$((idx+1))
  scene="$(jq -c ".job.scenes[$idx]" studio/feed.json)"
  prompt="$(printf '%s' "$scene" | jq -r '.visual_prompt // .purpose // "friendly preschool educational illustration"')"
  cast="$(jq -r --arg n "$n" '.scene_cast[$n] // ["Nara","Pip"] | join(", ")' "$CHARACTER_BIBLE")"
  prompt="$STYLE_PROMPT. Character Bible: $CHARACTER_ANCHORS. Scene cast: $cast. Only use the named recurring cast for this scene; preserve their exact face, hair, clothes, colors, proportions, and accessories. $prompt"
  voice="$(printf '%s' "$scene" | jq -r '.voice_over // ""')"
  onscreen="$(printf '%s' "$scene" | jq -r '.on_screen_text // ""')"
  scene_dur="$(printf '%s' "$scene" | jq -r '.duration_sec // 7')"
  [ "$scene_dur" -ge 3 ] 2>/dev/null || scene_dur=7

  enc="$(python3 - "$prompt" <<'PY'
import sys, urllib.parse
print(urllib.parse.quote(sys.argv[1], safe=''))
PY
)"
  img="studio/scenes/scene_$(printf '%02d' "$n").jpg"
  img_url="https://image.pollinations.ai/prompt/$enc?width=1280&height=720&nologo=true&seed=$((CHANNEL_SEED+n))"

  if ! curl -fL --max-time 25 "$img_url" -o "$img"; then
    echo "AI image unavailable for scene $n; using deterministic fallback."
    convert -size 1280x720 "gradient:#23395d-#101820"       -gravity center -fill white -font DejaVu-Sans-Bold -pointsize 58       -annotate +0-40 "HERMES WORK"       -pointsize 34 -annotate +0+55 "$onscreen" "$img"
  fi

  if ! identify "$img" >/dev/null 2>&1; then
    convert -size 1280x720 "xc:#23395d" -gravity center -fill white       -font DejaVu-Sans-Bold -pointsize 52 -annotate +0+0 "$onscreen" "$img"
  fi

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

  seg="studio/scenes/seg_$(printf '%02d' "$n").mp4"
  ffmpeg -y -loglevel error -loop 1 -i "$img" -i "$audio" -t "$dur"     -vf "scale=1280:720:force_original_aspect_ratio=increase,crop=1280:720,zoompan=z='min(zoom+0.0007,1.08)':d=1:s=1280x720:fps=30,format=yuv420p"     -c:v libx264 -preset veryfast -crf 22 -c:a aac -b:a 128k -af "apad" "$seg"
  echo "file 'scenes/$(basename "$seg")'" >> studio/concat.txt
done

ffmpeg -y -loglevel error -f concat -safe 0 -i studio/concat.txt -c copy studio/final.mp4
cp studio/scenes/scene_01.jpg studio/thumbnail.jpg
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

jq '{
  state:"RENDERED",
  engine:"AUTO_STUDIO_V1",
  planner_id:.job.planner_id,
  topic:.job.topic,
  title:.job.video_title,
  description:.job.description,
  hashtags:.job.hashtags,
  render_tag:.job.render_tag,
  visual_provider:"POLLINATIONS_ANONYMOUS_OR_DETERMINISTIC_FALLBACK",
  voice_provider:"EDGE_TTS_OR_ESPEAK_FALLBACK",
  render_provider:"GITHUB_ACTIONS_FFMPEG",
  target_duration_sec:(.job.duration_sec // 0),
  card_required:false,
  hermes_ai_used:false,
  neurons_used:0,
  character_bible:"DJAEGER_WORK_KIDS_V1",
  recurring_cast:["Nara","Bimo","Sasa","Pip"],
  repository:"Djaeger1/DJAEGER-WORK"
}' studio/feed.json > studio/metadata.json
jq --arg actual "$ACTUAL_DURATION" '. + {actual_duration_sec:($actual|tonumber)}' studio/metadata.json > studio/metadata.json.tmp
mv studio/metadata.json.tmp studio/metadata.json

echo "Rendered: $TITLE"
cat studio/probe.json

gh release create "$tag"   studio/final.mp4 studio/thumbnail.jpg studio/metadata.json studio/probe.json   --repo "$GITHUB_REPOSITORY"   --title "HERMES Auto Studio $id"   --notes "Auto-rendered by HERMES AUTO STUDIO V1. No paid API, no card-required render dependency, 0 HERMES Neurons."
