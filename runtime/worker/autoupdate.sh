#!/system/bin/sh
# HERMES WORK Auto Updater
# Pull-only, HERMES WORK runtime only. No modem/root framework changes.
ROOT="${HERMES_ROOT:-/data/adb/hermes_work}"
STATE="$ROOT/state"
LOG="$ROOT/logs/autoupdate.log"
PID="$STATE/autoupdate.pid"
OWNER="$STATE/autoupdate.owner"
LOCK="$STATE/autoupdate.lock"
FAILED="$STATE/autoupdate_failed_version"
mkdir -p "$STATE" "$ROOT/logs" "$ROOT/updates" "$ROOT/releases" "$ROOT/backups"
OLD_OWNER="$(cat "$OWNER" 2>/dev/null)"
if [ -n "$OLD_OWNER" ] && [ "$OLD_OWNER" != "$" ] && kill -0 "$OLD_OWNER" 2>/dev/null; then
  echo "$(date '+%Y-%m-%dT%H:%M:%S%z') duplicate updater exit owner=$OLD_OWNER self=$" >> "$LOG"
  exit 0
fi
echo $ > "$OWNER"
echo $ > "$PID"

ts(){ date '+%Y-%m-%dT%H:%M:%S%z'; }
log(){ echo "$(ts) $*" >> "$LOG"; }
cfg(){
  REL="$ROOT/releases/$(cat "$ROOT/current_release" 2>/dev/null)"
  sed -n "s/^$1=//p" "$REL/config/work.env" 2>/dev/null | tail -1
}
json_escape(){ printf '%s' "$1" | sed 's/\\/\\\\/g;s/"/\\"/g'; }
publish(){
  ST="$1"; REASON="$2"; LATEST="${3:-}"
  CUR="$(cat "$ROOT/current_release" 2>/dev/null | tr -d '\r\n')"
  NOW="$(ts)"
  TMP="$STATE/autoupdate.json.tmp"
  printf '{"state":"%s","reason":"%s","current":"%s","latest":"%s","last_check":"%s","automatic":true,"integrity":"SHA256","rollback":true}\n' \
    "$(json_escape "$ST")" "$(json_escape "$REASON")" "$(json_escape "$CUR")" "$(json_escape "$LATEST")" "$NOW" > "$TMP"
  chmod 600 "$TMP" 2>/dev/null
  mv -f "$TMP" "$STATE/autoupdate.json"
}
cleanup(){ rm -rf "$LOCK" 2>/dev/null; }
owner_cleanup(){ cleanup; CUR_OWNER="$(cat "$OWNER" 2>/dev/null)"; [ "$CUR_OWNER" = "$" ] && rm -f "$OWNER" 2>/dev/null; }
trap owner_cleanup EXIT HUP INT TERM

initial="$(cfg AUTO_UPDATE_INITIAL_DELAY_SECONDS)"; [ -n "$initial" ] || initial=45
case "$initial" in *[!0-9]*|'') initial=45;; esac
sleep "$initial"

while true; do
  REL="$ROOT/releases/$(cat "$ROOT/current_release" 2>/dev/null)"
  enabled="$(cfg AUTO_UPDATE_ENABLED)"; [ -n "$enabled" ] || enabled=1
  interval="$(cfg AUTO_UPDATE_INTERVAL_SECONDS)"; [ -n "$interval" ] || interval=900
  case "$interval" in *[!0-9]*|'') interval=900;; esac
  [ "$interval" -lt 300 ] 2>/dev/null && interval=300

  if [ "$enabled" != "1" ]; then
    publish "DISABLED" "CONFIG_DISABLED"
    sleep "$interval"; continue
  fi

  # Never compete with modem/tether recovery or thermally stressed device.
  if [ -x /system/bin/ip ] && ! /system/bin/ip addr show rndis0 2>/dev/null | grep -q 'inet '; then
    publish "DEFERRED" "TETHER_NOT_READY"
    sleep 60; continue
  fi
  TEMP_RAW="$(cat /sys/class/power_supply/battery/temp 2>/dev/null)"
  case "$TEMP_RAW" in *[!0-9]*|'') TEMP_RAW=0;; esac
  [ "$TEMP_RAW" -gt 430 ] 2>/dev/null && { publish "DEFERRED" "THERMAL_GUARD"; sleep 120; continue; }
  MEM_KB="$(awk '/^MemAvailable:/{print $2;exit}' /proc/meminfo 2>/dev/null)"
  case "$MEM_KB" in *[!0-9]*|'') MEM_KB=999999;; esac
  [ "$MEM_KB" -lt 262144 ] 2>/dev/null && { publish "DEFERRED" "LOW_RAM"; sleep 120; continue; }

  if ! mkdir "$LOCK" 2>/dev/null; then sleep 30; continue; fi
  trap cleanup EXIT HUP INT TERM

  CHANNEL="$(cfg UPDATE_CHANNEL_URL)"
  if [ -z "$CHANNEL" ]; then
    publish "ERROR" "CHANNEL_MISSING"; cleanup; sleep "$interval"; continue
  fi

  CH="$ROOT/updates/channel.auto.json"
  if ! /system/bin/wget -qO "$CH.tmp" "$CHANNEL" 2>/dev/null; then
    rm -f "$CH.tmp"; publish "RETRYING" "CHANNEL_FETCH_FAILED"; cleanup; sleep 120; continue
  fi
  mv -f "$CH.tmp" "$CH"

  VER="$(sed -n 's/.*"version"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$CH" | head -1)"
  BUNDLE="$(sed -n 's/.*"bundle"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$CH" | head -1)"
  SHA="$(sed -n 's/.*"sha256"[[:space:]]*:[[:space:]]*"\([0-9A-Fa-f]*\)".*/\1/p' "$CH" | head -1 | tr 'A-F' 'a-f')"
  case "$VER" in *[!A-Za-z0-9._-]*|'') publish "ERROR" "INVALID_VERSION"; cleanup; sleep "$interval"; continue;; esac
  case "$BUNDLE" in *[!A-Za-z0-9._-]*|'') publish "ERROR" "INVALID_BUNDLE"; cleanup; sleep "$interval"; continue;; esac
  [ "${#SHA}" -eq 64 ] || { publish "ERROR" "INVALID_SHA" "$VER"; cleanup; sleep "$interval"; continue; }

  CUR="$(cat "$ROOT/current_release" 2>/dev/null | tr -d '\r\n')"
  if [ "$CUR" = "$VER" ]; then
    rm -f "$FAILED" 2>/dev/null
    publish "UP_TO_DATE" "CURRENT_IS_LATEST" "$VER"
    cleanup; sleep "$interval"; continue
  fi

  BAD="$(cat "$FAILED" 2>/dev/null | tr -d '\r\n')"
  if [ "$BAD" = "$VER" ]; then
    publish "QUARANTINED" "PREVIOUS_SELFTEST_FAILED_WAITING_FOR_NEW_RELEASE" "$VER"
    cleanup; sleep "$interval"; continue
  fi

  publish "DOWNLOADING" "NEW_RELEASE_FOUND" "$VER"
  BASE="${CHANNEL%/*}"
  ZIP="$ROOT/updates/$VER.auto.zip"
  rm -f "$ZIP.tmp"
  if ! /system/bin/wget -qO "$ZIP.tmp" "$BASE/$BUNDLE" 2>/dev/null; then
    rm -f "$ZIP.tmp"; publish "RETRYING" "BUNDLE_FETCH_FAILED" "$VER"; cleanup; sleep 120; continue
  fi
  SIZE="$(wc -c < "$ZIP.tmp" 2>/dev/null | tr -d ' ')"
  case "$SIZE" in *[!0-9]*|'') SIZE=0;; esac
  if [ "$SIZE" -le 0 ] || [ "$SIZE" -gt 33554432 ]; then
    rm -f "$ZIP.tmp"; publish "ERROR" "BUNDLE_SIZE_INVALID" "$VER"; cleanup; sleep "$interval"; continue
  fi
  GOT="$(sha256sum "$ZIP.tmp" 2>/dev/null | awk '{print tolower($1)}')"
  if [ "$GOT" != "$SHA" ]; then
    rm -f "$ZIP.tmp"; publish "ERROR" "SHA256_MISMATCH" "$VER"; cleanup; sleep "$interval"; continue
  fi
  mv -f "$ZIP.tmp" "$ZIP"

  STAGE="$ROOT/releases/.stage-$VER-auto"
  DEST="$ROOT/releases/$VER"
  NEW="$DEST.new"
  rm -rf "$STAGE" "$NEW"
  mkdir -p "$STAGE" "$NEW"
  if command -v unzip >/dev/null 2>&1; then
    unzip -oq "$ZIP" -d "$STAGE" || UNZIP_FAIL=1
  elif [ -x /data/adb/magisk/busybox ]; then
    /data/adb/magisk/busybox unzip -oq "$ZIP" -d "$STAGE" || UNZIP_FAIL=1
  elif command -v busybox >/dev/null 2>&1; then
    busybox unzip -oq "$ZIP" -d "$STAGE" || UNZIP_FAIL=1
  else
    UNZIP_FAIL=1
  fi
  if [ "${UNZIP_FAIL:-0}" = 1 ] || [ ! -f "$STAGE/manifest.json" ] || [ ! -f "$STAGE/payload/bin/workd" ] || [ ! -f "$STAGE/payload/worker/handoff.sh" ] || [ ! -f "$STAGE/payload/worker/autoupdate.sh" ]; then
    rm -rf "$STAGE" "$NEW"; publish "ERROR" "BUNDLE_STRUCTURE_INVALID" "$VER"; cleanup; sleep "$interval"; continue
  fi

  cp "$STAGE/manifest.json" "$NEW/manifest.json" || COPY_FAIL=1
  cp -R "$STAGE/payload/." "$NEW/" || COPY_FAIL=1
  if [ "${COPY_FAIL:-0}" = 1 ]; then
    rm -rf "$STAGE" "$NEW"; publish "ERROR" "STAGE_COPY_FAILED" "$VER"; cleanup; sleep "$interval"; continue
  fi
  chmod 755 "$NEW/bin/workd" "$NEW/worker/tick.sh" "$NEW/worker/handoff.sh" "$NEW/worker/autoupdate.sh" 2>/dev/null

  PREV="$CUR"
  printf 'time=%s\nfrom=%s\nto=%s\nsha256=%s\n' "$(ts)" "$PREV" "$VER" "$SHA" > "$ROOT/backups/autoupdate-$(date '+%Y%m%d-%H%M%S').txt"
  [ -n "$PREV" ] && printf '%s\n' "$PREV" > "$ROOT/previous_release"
  rm -rf "$DEST"
  if ! mv "$NEW" "$DEST"; then
    rm -rf "$STAGE" "$NEW"; publish "ERROR" "ACTIVATION_MOVE_FAILED" "$VER"; cleanup; sleep "$interval"; continue
  fi
  rm -rf "$STAGE"
  publish "ACTIVATING" "SELFTEST_PENDING" "$VER"
  log "install $PREV -> $VER sha256=$SHA"

  sh "$DEST/worker/handoff.sh" "$ROOT" "$VER" "$PREV" >/dev/null 2>&1 &

  HEALTHY=0
  TRY=0
  while [ "$TRY" -lt 30 ]; do
    sleep 1
    STATUS_JSON="$(/system/bin/wget -qO- http://127.0.0.1:8766/api/work/status 2>/dev/null)"
    ACTIVE="$(cat "$ROOT/current_release" 2>/dev/null | tr -d '\r\n')"
    if [ "$ACTIVE" = "$VER" ] && printf '%s' "$STATUS_JSON" | grep -Fq "\"release\":\"$VER\""; then
      HEALTHY=1
      break
    fi
    TRY=$((TRY+1))
  done

  if [ "$HEALTHY" = 1 ]; then
    rm -f "$FAILED"
    publish "UP_TO_DATE" "AUTO_UPDATE_SUCCESS" "$VER"
    log "PASS $VER selftest_tries=$TRY"
    cleanup
    NEWUP="$DEST/worker/autoupdate.sh"
    if [ -f "$NEWUP" ]; then
      exec /system/bin/sh "$NEWUP"
    fi
  else
    ACTIVE="$(cat "$ROOT/current_release" 2>/dev/null | tr -d '\r\n')"
    STATUS_JSON="$(/system/bin/wget -qO- http://127.0.0.1:8766/api/work/status 2>/dev/null)"
    if [ "$ACTIVE" = "$PREV" ] && printf '%s' "$STATUS_JSON" | grep -Fq "\"release\":\"$PREV\""; then
      printf '%s\n' "$VER" > "$FAILED"
      publish "QUARANTINED" "VERIFIED_ROLLBACK_TO_PREVIOUS" "$VER"
      log "FAIL $VER verified_rollback=$PREV"
    else
      rm -f "$FAILED"
      publish "VERIFYING" "HANDOFF_STATE_INCONCLUSIVE_NO_QUARANTINE" "$VER"
      log "WARN $VER no_false_quarantine active=$ACTIVE"
    fi
    cleanup
    sleep 120
  fi
done
