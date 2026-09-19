#!/usr/bin/env bash
set -euo pipefail

REPO="${GITHUB_REPOSITORY:-Djaeger1/DJAEGER-WORK}"
PRIVACY="${YT_PRIVACY_STATUS:-private}"
CHANNEL_LABEL="${YT_CHANNEL_LABEL:-YouTube}"

case "$PRIVACY" in
  private|unlisted|public) ;;
  *) echo "Invalid YT_PRIVACY_STATUS: $PRIVACY"; exit 1 ;;
esac

if [ -z "${YT_CLIENT_ID:-}" ] || [ -z "${YT_CLIENT_SECRET:-}" ] || [ -z "${YT_REFRESH_TOKEN:-}" ]; then
  echo "WAITING_YOUTUBE_AUTH: add YT_CLIENT_ID, YT_CLIENT_SECRET, and YT_REFRESH_TOKEN as repository Actions secrets."
  exit 0
fi

mapfile -t studio_tags < <(
  gh api "repos/$REPO/releases?per_page=50" --jq '.[] | select(.tag_name|startswith("hermes-studio-")) | .tag_name'
)

tag=""
id=""
for candidate in "${studio_tags[@]}"; do
  cid="${candidate#hermes-studio-}"
  if ! gh release view "hermes-published-$cid" --repo "$REPO" >/dev/null 2>&1; then
    tag="$candidate"
    id="$cid"
    break
  fi
done

if [ -z "$tag" ] || [ -z "$id" ]; then
  echo "No unpublished rendered video."
  exit 0
fi

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
gh release download "$tag" --repo "$REPO" --dir "$work" --pattern 'final.mp4' --pattern 'thumbnail.jpg' --pattern 'metadata.json'

test -s "$work/final.mp4"
test -s "$work/metadata.json"

title="$(jq -r '.title // .topic // "HERMES WORK"' "$work/metadata.json" | head -c 100)"
description="$(jq -r '.description // ""' "$work/metadata.json")"
hashtags="$(jq -r '(.hashtags // []) | join(" ")' "$work/metadata.json")"
full_description="$(printf '%s\n\n%s' "$description" "$hashtags" | head -c 4800)"

token_json="$(curl -fsS --max-time 30 \
  -X POST 'https://oauth2.googleapis.com/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode "client_id=$YT_CLIENT_ID" \
  --data-urlencode "client_secret=$YT_CLIENT_SECRET" \
  --data-urlencode "refresh_token=$YT_REFRESH_TOKEN" \
  --data-urlencode 'grant_type=refresh_token')"
access_token="$(printf '%s' "$token_json" | jq -r '.access_token // empty')"
if [ -z "$access_token" ]; then
  echo "YouTube OAuth refresh failed."
  exit 1
fi

jq -n \
  --arg title "$title" \
  --arg description "$full_description" \
  --arg privacy "$PRIVACY" \
  '{
    snippet:{title:$title,description:$description,categoryId:"27"},
    status:{privacyStatus:$privacy,selfDeclaredMadeForKids:true}
  }' > "$work/request.json"

size="$(wc -c < "$work/final.mp4" | tr -d ' ')"
headers="$work/start.headers"
start_code="$(curl -sS -o "$work/start.body" -D "$headers" -w '%{http_code}' --max-time 30 \
  -X POST 'https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status' \
  -H "Authorization: Bearer $access_token" \
  -H 'Content-Type: application/json; charset=UTF-8' \
  -H 'X-Upload-Content-Type: video/mp4' \
  -H "X-Upload-Content-Length: $size" \
  --data-binary @"$work/request.json")"
if [ "$start_code" -lt 200 ] || [ "$start_code" -ge 300 ]; then
  echo "YouTube resumable session failed: HTTP $start_code"
  cat "$work/start.body"
  exit 1
fi
upload_url="$(awk 'BEGIN{IGNORECASE=1} /^location:/{sub(/\r$/,""); sub(/^[^:]*:[[:space:]]*/,""); print; exit}' "$headers")"
if [ -z "$upload_url" ]; then
  echo "YouTube upload location missing."
  exit 1
fi

upload_code="$(curl -sS -o "$work/upload.json" -w '%{http_code}' --max-time 900 \
  -X PUT "$upload_url" \
  -H "Authorization: Bearer $access_token" \
  -H 'Content-Type: video/mp4' \
  --data-binary @"$work/final.mp4")"
if [ "$upload_code" -lt 200 ] || [ "$upload_code" -ge 300 ]; then
  echo "YouTube video upload failed: HTTP $upload_code"
  cat "$work/upload.json"
  exit 1
fi

video_id="$(jq -r '.id // empty' "$work/upload.json")"
if [ -z "$video_id" ]; then
  echo "YouTube response did not contain a video id."
  exit 1
fi

if [ -s "$work/thumbnail.jpg" ]; then
  curl -fsS --max-time 60 \
    -X POST "https://www.googleapis.com/upload/youtube/v3/thumbnails/set?videoId=$video_id&uploadType=multipart" \
    -H "Authorization: Bearer $access_token" \
    -F "media=@$work/thumbnail.jpg;type=image/jpeg" >/dev/null || echo "Thumbnail upload skipped/failed; video upload remains valid."
fi

case "$PRIVACY" in
  public) pub_status="PUBLISHED" ;;
  unlisted) pub_status="UPLOADED_UNLISTED" ;;
  *) pub_status="UPLOADED_PRIVATE" ;;
esac
published_at="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
jq -n \
  --arg platform "youtube" \
  --arg external_id "$video_id" \
  --arg url "https://youtu.be/$video_id" \
  --arg channel "$CHANNEL_LABEL" \
  --arg status "$pub_status" \
  --arg published_at "$published_at" \
  '{platform:$platform,external_id:$external_id,url:$url,channel:$channel,status:$status,published_at:$published_at}' > "$work/publication.json"

gh release create "hermes-published-$id" "$work/publication.json" \
  --repo "$REPO" \
  --title "HERMES Publication $id" \
  --notes-file "$work/publication.json"

echo "YOUTUBE_UPLOAD_OK planner_id=$id video_id=$video_id privacy=$PRIVACY"
