#!/system/bin/sh
# DJAEGER WORK process convergence.
# Scope is intentionally narrow: only HERMES WORK release worker scripts.
ROOT="${HERMES_ROOT:-/data/adb/hermes_work}"
STATE="$ROOT/state"
LOG="$ROOT/logs/process-converge.log"
SELF=$$
PARENT=$PPID
mkdir -p "$STATE" "$ROOT/logs"

ts(){ date '+%Y-%m-%dT%H:%M:%S%z'; }
log(){ echo "$(ts) $*" >> "$LOG"; }

VICTIMS="$STATE/process-converge.victims.$$"
NEXT="$STATE/process-converge.next.$$"
: > "$VICTIMS"
trap 'rm -f "$VICTIMS" "$NEXT" 2>/dev/null' EXIT HUP INT TERM

is_worker_root(){
  CMD="$1"
  case "$CMD" in
    *"$ROOT/releases/"*"/worker/autoupdate.sh"*|    *"$ROOT/releases/"*"/worker/github-control-shadow.sh"*|    *"$ROOT/releases/"*"/worker/tick.sh"*|    *"$ROOT/releases/"*"/worker/bridge-deploy.sh"*|    *"$ROOT/releases/"*"/worker/handoff.sh"* )
      return 0 ;;
  esac
  return 1
}

# Seed with HERMES worker-script roots only.
for P in /proc/[0-9]*; do
  N="${P#/proc/}"
  [ "$N" = "$SELF" ] && continue
  [ "$N" = "$PARENT" ] && continue
  CMD="$(tr '\000' ' ' < "$P/cmdline" 2>/dev/null)"
  is_worker_root "$CMD" || continue
  echo "$N" >> "$VICTIMS"
done

sort -u "$VICTIMS" -o "$VICTIMS" 2>/dev/null
ROOT_COUNT="$(wc -l < "$VICTIMS" 2>/dev/null | tr -d ' ')"
case "$ROOT_COUNT" in *[!0-9]*|'') ROOT_COUNT=0;; esac

# Expand to descendants, but never cross into unrelated process trees.
PASS=0
while [ "$PASS" -lt 12 ]; do
  : > "$NEXT"
  for P in /proc/[0-9]*; do
    N="${P#/proc/}"
    [ "$N" = "$SELF" ] && continue
    [ "$N" = "$PARENT" ] && continue
    PP="$(awk '/^PPid:/{print $2;exit}' "$P/status" 2>/dev/null)"
    case "$PP" in *[!0-9]*|'') continue;; esac
    grep -qx "$PP" "$VICTIMS" 2>/dev/null || continue
    grep -qx "$N" "$VICTIMS" 2>/dev/null && continue
    echo "$N" >> "$NEXT"
  done
  [ -s "$NEXT" ] || break
  cat "$NEXT" >> "$VICTIMS"
  sort -u "$VICTIMS" -o "$VICTIMS" 2>/dev/null
  PASS=$((PASS+1))
done

TOTAL="$(wc -l < "$VICTIMS" 2>/dev/null | tr -d ' ')"
case "$TOTAL" in *[!0-9]*|'') TOTAL=0;; esac
log "scan roots=$ROOT_COUNT total=$TOTAL self=$SELF parent=$PARENT"

# Graceful first, then hard-stop only surviving HERMES worker descendants.
if [ "$TOTAL" -gt 0 ]; then
  while read -r N; do
    case "$N" in *[!0-9]*|'') continue;; esac
    [ "$N" -le 1 ] 2>/dev/null && continue
    [ "$N" = "$SELF" ] && continue
    [ "$N" = "$PARENT" ] && continue
    kill "$N" 2>/dev/null || true
  done < "$VICTIMS"
  sleep 2
  while read -r N; do
    case "$N" in *[!0-9]*|'') continue;; esac
    [ "$N" -le 1 ] 2>/dev/null && continue
    [ "$N" = "$SELF" ] && continue
    [ "$N" = "$PARENT" ] && continue
    kill -0 "$N" 2>/dev/null || continue
    kill -9 "$N" 2>/dev/null || true
  done < "$VICTIMS"
fi

rm -f "$STATE/autoupdate.pid" "$STATE/autoupdate.owner" "$STATE/github-control-shadow.pid" 2>/dev/null
rm -rf "$STATE/autoupdate.daemon.lock" 2>/dev/null

# Publish a compact audit result.
LEFT=0
for P in /proc/[0-9]*; do
  CMD="$(tr '\000' ' ' < "$P/cmdline" 2>/dev/null)"
  is_worker_root "$CMD" && LEFT=$((LEFT+1))
done

TMP="$STATE/process-converge.json.tmp"
printf '{"state":"DONE","worker_roots_before":%s,"targeted_processes":%s,"worker_roots_after":%s,"scope":"HERMES_WORK_RELEASE_WORKERS_ONLY","updated_at":"%s"}\n'   "$ROOT_COUNT" "$TOTAL" "$LEFT" "$(ts)" > "$TMP"
chmod 600 "$TMP" 2>/dev/null
mv -f "$TMP" "$STATE/process-converge.json"
log "done roots_before=$ROOT_COUNT targeted=$TOTAL roots_after=$LEFT"
exit 0
