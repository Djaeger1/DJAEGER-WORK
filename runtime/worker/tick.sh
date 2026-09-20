#!/system/bin/sh
ROOT="${HERMES_ROOT:-/data/adb/hermes_work}"
REL="${HERMES_RELEASE:-$ROOT/releases/$(cat "$ROOT/current_release" 2>/dev/null)}"
PID="$ROOT/state/workd.pid"; LOG="$ROOT/logs/workd.log"; APID="$ROOT/state/autoupdate.pid"
mkdir -p "$ROOT/data/research" "$ROOT/data/knowledge" "$ROOT/data/database" "$ROOT/state" "$ROOT/logs" "$ROOT/backups" "$ROOT/updates"

if ! { [ -f "$PID" ] && kill -0 "$(cat "$PID" 2>/dev/null)" 2>/dev/null; }; then
  nohup "$REL/bin/workd" --root "$ROOT" --release "$REL" >>"$LOG" 2>&1 &
  echo $! > "$PID"
fi

AUP="$REL/worker/autoupdate.sh"
if [ -f "$AUP" ]; then
  OLD_AUP="$(cat "$APID" 2>/dev/null)"
  case "$OLD_AUP" in *[!0-9]*|'') OLD_AUP="";; esac
  if [ -z "$OLD_AUP" ] || ! kill -0 "$OLD_AUP" 2>/dev/null; then
    FOUND=""
    for P in /proc/[0-9]*; do
      N="${P#/proc/}"
      CMD="$(tr '\000' ' ' < "$P/cmdline" 2>/dev/null)"
      case "$CMD" in *"$ROOT/releases/"*"/worker/autoupdate.sh"* ) FOUND="$N"; break;; esac
    done
    if [ -n "$FOUND" ]; then
      echo "$FOUND" > "$APID"
    else
      HERMES_ROOT="$ROOT" nohup /system/bin/sh "$AUP" >>"$ROOT/logs/autoupdate.log" 2>&1 &
      echo $! > "$APID"
    fi
  fi
fi
exit 0
