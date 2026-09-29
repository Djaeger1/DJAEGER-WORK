#!/system/bin/sh
set -eu
ARCHIVE="${1:-}"
STATE="/data/adb/djaeger_ai"
WORK="/data/local/tmp/djaeger_ai_restore_$$"
TS="$(date +%Y%m%d-%H%M%S)"
ROLLBACK="/sdcard/Download/DJAEGER_AI_STATE_PRE_RESTORE_${TS}.tar.gz"
fail(){ echo "RESTORE_STATUS=FAIL"; echo "ERROR=$*"; rm -rf "$WORK"; exit 1; }
[ -n "$ARCHIVE" ] || fail "Tidak ada file backup."
[ -f "$ARCHIVE" ] || fail "File backup tidak ditemukan: $ARCHIVE"
case "$ARCHIVE" in *.tar.gz|*.tgz) ;; *) fail "Ekstensi backup harus .tar.gz atau .tgz.";; esac
mkdir -p "$WORK"
LIST="$WORK/list.txt"
echo "CHECK=ARCHIVE"
tar -tzf "$ARCHIVE" > "$LIST" 2>"$WORK/tar.err" || fail "Arsip tidak valid: $(cat "$WORK/tar.err")"
BAD=0
while IFS= read -r p; do
  p="$(printf '%s' "$p" | sed 's#^\./##')"
  case "$p" in data/adb/djaeger_ai|data/adb/djaeger_ai/*) ;; *) echo "UNSAFE_ENTRY=$p"; BAD=1; break;; esac
  case "$p" in /*|*../*|../*) echo "UNSAFE_ENTRY=$p"; BAD=1; break;; esac
done < "$LIST"
[ "$BAD" -eq 0 ] || fail "Arsip mengandung path yang tidak diizinkan."
if [ -d "$STATE" ]; then
  echo "ROLLBACK=CREATING"
  tar -czf "$ROLLBACK" -C / data/adb/djaeger_ai || fail "Gagal membuat rollback."
  echo "ROLLBACK=$ROLLBACK"
else echo "ROLLBACK=NOT_NEEDED"; fi
tar -tvzf "$ARCHIVE" 2>/dev/null | awk 'substr($0,1,1)=="l" || substr($0,1,1)=="h" {bad=1} END {exit bad}' || fail "Arsip mengandung symlink/hardlink dan ditolak."
mkdir -p "$WORK/extract"
tar -xzf "$ARCHIVE" -C "$WORK/extract" || fail "Ekstraksi gagal."
SRC="$WORK/extract/data/adb/djaeger_ai"
[ -d "$SRC" ] || fail "Folder data/adb/djaeger_ai tidak ada."
[ -f "$SRC/gemini.conf" ] || fail "gemini.conf tidak ada."
[ -s "$SRC/gemini_keys.vault" ] || fail "gemini_keys.vault kosong/tidak ada."
[ -f "$SRC/hermes_cloud.conf" ] || fail "hermes_cloud.conf tidak ada."
KEYCOUNT="$(grep -E '^[[:space:]]*[^#[:space:]]+([=:]|$)' "$SRC/gemini_keys.vault" 2>/dev/null | wc -l | tr -d ' ')"; [ -n "$KEYCOUNT" ] || KEYCOUNT="0"
FILECOUNT="$(find "$SRC" -type f 2>/dev/null | wc -l | tr -d ' ')"
BYTES="$(du -sk "$SRC" 2>/dev/null | awk '{print $1*1024}')"
OLD="$WORK/old_state"
if [ -d "$STATE" ]; then mv "$STATE" "$OLD" || fail "Tidak bisa memindahkan state lama."; fi
if ! mv "$SRC" "$STATE"; then
  if [ -d "$OLD" ]; then mv "$OLD" "$STATE" || true; fi
  fail "Commit restore gagal."
fi
chown -R 0:0 "$STATE" 2>/dev/null || true
chmod 700 "$STATE" 2>/dev/null || true
chmod 600 "$STATE/gemini.conf" "$STATE/gemini_keys.vault" "$STATE/hermes_cloud.conf" 2>/dev/null || true
[ -f "$STATE/gemini.conf" ] || fail "Final gemini.conf hilang."
[ -s "$STATE/gemini_keys.vault" ] || fail "Final gemini_keys.vault hilang."
[ -f "$STATE/hermes_cloud.conf" ] || fail "Final hermes_cloud.conf hilang."
echo "RESTORE_STATUS=PASS"
echo "RESTORED_PATH=$STATE"
echo "GEMINI_KEY_VAULT=PRESENT"
echo "GEMINI_KEYS=$KEYCOUNT"
echo "HERMES_CREDENTIAL=RESTORED"
echo "STATE_FILES=$FILECOUNT"
echo "STATE_BYTES=$BYTES"
echo "ROLLBACK_AVAILABLE=$([ -f "$ROLLBACK" ] && echo YES || echo NO)"
echo "REBOOT_REQUIRED=NO"
rm -rf "$OLD" "$WORK"
exit 0
