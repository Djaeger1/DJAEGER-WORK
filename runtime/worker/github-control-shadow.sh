#!/system/bin/sh
# DJAEGER WORK GitHub durable control shadow.
# Read-only by design: this worker NEVER executes device mutations.

ROOT="${HERMES_ROOT:-/data/adb/hermes_work}"
STATE="$ROOT/state"
LOG="$ROOT/logs/github-control-shadow.log"
PID="$STATE/github-control-shadow.pid"
mkdir -p "$STATE" "$ROOT/logs"

ts(){ date '+%Y-%m-%dT%H:%M:%S%z'; }
log(){ echo "$(ts) $*" >> "$LOG"; }
cfg(){
  REL="$ROOT/releases/$(cat "$ROOT/current_release" 2>/dev/null)"
  sed -n "s/^$1=//p" "$REL/config/work.env" 2>/dev/null | tail -1
}
publish(){
  ST="$1"; REASON="$2"; GEN="$3"
  TMP="$STATE/github-control.json.tmp"
  printf '{"state":"%s","reason":"%s","generation":"%s","mode":"SHADOW","writes_allowed":false,"updated_at":"%s"}\n' \
    "$ST" "$REASON" "$GEN" "$(ts)" > "$TMP"
  chmod 600 "$TMP" 2>/dev/null
  mv -f "$TMP" "$STATE/github-control.json"
}

echo $$ > "$PID"
trap 'rm -f "$PID" 2>/dev/null' EXIT HUP INT TERM

while true; do
  ENABLED="$(cfg GITHUB_CONTROL_SHADOW)"
  INTERVAL="$(cfg GITHUB_CONTROL_INTERVAL_SECONDS)"
  URL="$(cfg GITHUB_CONTROL_URL)"
  [ -n "$INTERVAL" ] || INTERVAL=300
  case "$INTERVAL" in *[!0-9]*|'') INTERVAL=300;; esac
  [ "$INTERVAL" -lt 300 ] 2>/dev/null && INTERVAL=300

  if [ "$ENABLED" != "1" ]; then
    publish "DISABLED" "CONFIG_DISABLED" "0"
    sleep "$INTERVAL"
    continue
  fi

  if [ -z "$URL" ]; then
    publish "ERROR" "CONTROL_URL_MISSING" "0"
    sleep "$INTERVAL"
    continue
  fi

  DESIRED="$STATE/github-control-desired.json"
  if ! /system/bin/wget -qO "$DESIRED.tmp" "$URL" 2>/dev/null; then
    rm -f "$DESIRED.tmp"
    publish "RETRYING" "DESIRED_FETCH_FAILED" "0"
    sleep "$INTERVAL"
    continue
  fi
  mv -f "$DESIRED.tmp" "$DESIRED"

  SCHEMA="$(sed -n 's/.*"schema"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' "$DESIRED" | head -1)"
  GEN="$(sed -n 's/.*"generation"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' "$DESIRED" | head -1)"
  MODE="$(sed -n 's/.*"mode"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$DESIRED" | head -1)"
  CMD="$(sed -n 's/.*"command"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$DESIRED" | head -1)"
  WRITES="$(sed -n 's/.*"allow_device_writes"[[:space:]]*:[[:space:]]*\([^,}]*\).*/\1/p' "$DESIRED" | head -1 | tr -d '[:space:]')"

  if [ "$SCHEMA" != "1" ] || [ "$MODE" != "SHADOW" ] || [ "$CMD" != "OBSERVE" ] || [ "$WRITES" != "false" ]; then
    publish "BLOCKED" "UNSAFE_OR_UNKNOWN_DESIRED_STATE" "${GEN:-0}"
    log "blocked schema=$SCHEMA mode=$MODE command=$CMD writes=$WRITES generation=$GEN"
    sleep "$INTERVAL"
    continue
  fi

  # OBSERVE is the only allowed operation: read local status and cache it.
  if /system/bin/wget -qO "$STATE/github-control-local-status.json.tmp" \
      "http://127.0.0.1:8766/api/work/status" 2>/dev/null; then
    mv -f "$STATE/github-control-local-status.json.tmp" "$STATE/github-control-local-status.json"
    publish "OBSERVED" "READ_ONLY_STATUS_CAPTURED" "${GEN:-0}"
  else
    rm -f "$STATE/github-control-local-status.json.tmp"
    publish "RETRYING" "LOCAL_STATUS_UNAVAILABLE" "${GEN:-0}"
  fi

  sleep "$INTERVAL"
done
