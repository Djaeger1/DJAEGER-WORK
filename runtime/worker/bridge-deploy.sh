#!/system/bin/sh
# HERMES WORK Bridge bootstrap
# Data-only relay. No Workers AI binding, no LLM call, no Neurons.
ROOT="${HERMES_ROOT:-/data/adb/hermes_work}"
STATE="$ROOT/state"
LOG="$ROOT/logs/bridge-deploy.log"
API="https://api.cloudflare.com/client/v4"
SCRIPT="hermes-work-bridge"
BASE="https://hermes-work-bridge.moclomper.workers.dev"
TMP="$ROOT/updates/bridge-bootstrap"
SRC="$TMP/index.js"
RESP="$TMP/resp.json"
SECRET="$TMP/secret.json"
mkdir -p "$STATE" "$ROOT/logs" "$TMP" || exit 1
chmod 700 "$TMP" 2>/dev/null || true
exec >>"$LOG" 2>&1

stamp(){ date '+%Y-%m-%dT%H:%M:%S%z'; }
publish(){
  ST="$1"; REASON="$2"
  cat >"$STATE/bridge.json.tmp" <<EOF
{"state":"$ST","reason":"$REASON","endpoint":"$BASE","mode":"DATA_ONLY","ai_used":false,"neurons_used":0,"updated_at":"$(stamp)"}
EOF
  chmod 600 "$STATE/bridge.json.tmp" 2>/dev/null
  mv -f "$STATE/bridge.json.tmp" "$STATE/bridge.json"
}
echo "===== HERMES WORK BRIDGE $(stamp) ====="

TOKEN_FILE="$STATE/bridge_key"
TOKEN=""
if [ -s "$TOKEN_FILE" ]; then
  TOKEN="$(tr -d '\r\n ' < "$TOKEN_FILE" 2>/dev/null)"
fi
if ! printf '%s' "$TOKEN" | grep -Eq '^[0-9A-Fa-f]{64}$'; then
  TOKEN="$(dd if=/dev/urandom bs=32 count=1 2>/dev/null | od -An -tx1 | tr -d ' \n')"
  printf '%s\n' "$TOKEN" > "$TOKEN_FILE" || exit 2
  chmod 600 "$TOKEN_FILE" 2>/dev/null || true
fi
printf '%s' "$TOKEN" | grep -Eq '^[0-9A-Fa-f]{64}$' || {
  publish "BRIDGE_KEY_FAILED" "LOCAL_BRIDGE_KEY_GENERATION_FAILED"
  exit 2
}
echo "LOCAL_BRIDGE_KEY=READY (hidden)"

cat >"$SRC" <<'__HERMES_WORK_BRIDGE_JS__'
const VERSION = "1.0.0";

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      "access-control-allow-origin": "*",
      "access-control-allow-headers": "authorization, content-type",
      "access-control-allow-methods": "GET, POST, OPTIONS"
    }
  });
}

function auth(request, env) {
  const secret = String(env.HERMES_BRIDGE_TOKEN || "").trim();
  if (!secret) return false;
  return (request.headers.get("authorization") || "") === `Bearer ${secret}`;
}

function str(v, max = 96) {
  return String(v ?? "").slice(0, max);
}
function num(v, lo, hi) {
  const n = Number(v);
  if (!Number.isFinite(n)) return null;
  return Math.max(lo, Math.min(hi, n));
}
function cleanSnapshot(b) {
  return {
    device: "REDMI_5A_HERMES_WORK",
    sent_at: str(b.sent_at, 40),
    release: str(b.release, 80),
    tether_state: str(b.tether_state, 16),
    temperature_c: num(b.temperature_c, -20, 100),
    mem_available_mb: num(b.mem_available_mb, 0, 8192),
    worker_state: str(b.worker_state, 24),
    safe_mode: b.safe_mode === true,
    research_total: num(b.research_total, 0, 100000000),
    last_research: str(b.last_research, 48),
    bridge_agent: str(b.bridge_agent, 48),
    received_at: new Date().toISOString()
  };
}

export class BridgeState {
  constructor(ctx, env) { this.ctx = ctx; }
  async fetch(request) {
    if (request.method === "POST") {
      const v = await request.json();
      await this.ctx.storage.put("latest", v);
      return json({ ok: true });
    }
    const v = await this.ctx.storage.get("latest");
    if (!v) return json({ ok: false, error: "NO_SNAPSHOT_YET" }, 404);
    return json({ ok: true, snapshot: v });
  }
}

function stub(env) {
  const id = env.BRIDGE_STATE.idFromName("redmi5a");
  return env.BRIDGE_STATE.get(id);
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return json({ ok: true });
    const path = new URL(request.url).pathname.replace(/\/+$/, "") || "/";

    if (request.method === "GET" && (path === "/" || path === "/status")) {
      return json({
        ok: true,
        service: "HERMES WORK BRIDGE",
        version: VERSION,
        mode: "DATA_ONLY",
        ai_used: false,
        neurons_used: 0
      });
    }

    if (request.method === "POST" && path === "/v1/bridge/push") {
      if (!auth(request, env)) return json({ ok: false, error: "UNAUTHORIZED" }, 401);
      let body;
      try { body = await request.json(); } catch { return json({ ok: false, error: "INVALID_JSON" }, 400); }
      const snapshot = cleanSnapshot(body || {});
      const r = await stub(env).fetch("https://state.local/latest", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify(snapshot)
      });
      if (!r.ok) return json({ ok: false, error: "STORE_FAILED" }, 502);
      return json({ ok: true, state: "CONNECTED", mode: "DATA_ONLY", ai_used: false, neurons_used: 0 });
    }

    if (request.method === "GET" && path === "/v1/bridge/public") {
      const r = await stub(env).fetch("https://state.local/latest");
      if (!r.ok) return json({ ok: false, error: "NO_SNAPSHOT_YET", ai_used: false, neurons_used: 0 }, 404);
      const saved = await r.json();
      return json({
        ok: true,
        service: "HERMES WORK BRIDGE",
        mode: "READ_ONLY_SANITIZED",
        ai_used: false,
        neurons_used: 0,
        snapshot: saved.snapshot
      });
    }

    return json({ ok: false, error: "NOT_FOUND" }, 404);
  }
};
__HERMES_WORK_BRIDGE_JS__

DEPLOYED=0
USED_ACCOUNT=""
USED_CF_TOKEN=""
CF_LIST="$TMP/cloudflare_files"
{
  printf '%s\n' /data/adb/djaeger_ai/cloudflare.conf /data/adb/djaeger_ai/cloudflare_2.conf /data/adb/djaeger_ai/cloudflare_3.conf
  find /data/adb/djaeger_ai /data/user/0/com.termoneplus/app_HOME /data/media/0/Download /sdcard/Download \
    -maxdepth 7 -type f \( -name 'cloudflare.conf' -o -name 'cloudflare_2.conf' -o -name 'cloudflare_3.conf' \) 2>/dev/null || true
} | awk '!seen[$0]++' > "$CF_LIST"
while IFS= read -r F; do
  [ -r "$F" ] || continue
  unset CLOUDFLARE_ACCOUNT_ID CLOUDFLARE_API_TOKEN
  . "$F" 2>/dev/null || continue
  [ -n "${CLOUDFLARE_ACCOUNT_ID:-}" ] || continue
  [ -n "${CLOUDFLARE_API_TOKEN:-}" ] || continue
  META='{"main_module":"index.js","compatibility_date":"2026-09-19","bindings":[{"type":"durable_object_namespace","name":"BRIDGE_STATE","class_name":"BridgeState"}],"migrations":[{"tag":"v1","new_sqlite_classes":["BridgeState"]}]}'
  rm -f "$RESP"
  curl -sS --connect-timeout 8 -m 90 -X PUT     -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN"     -F "metadata=$META;type=application/json"     -F "index.js=@$SRC;type=application/javascript+module"     "$API/accounts/$CLOUDFLARE_ACCOUNT_ID/workers/scripts/$SCRIPT"     >"$RESP" 2>/dev/null || true
  if grep -Eq '"success"[[:space:]]*:[[:space:]]*true' "$RESP"; then
    DEPLOYED=1
    USED_ACCOUNT="$CLOUDFLARE_ACCOUNT_ID"
    USED_CF_TOKEN="$CLOUDFLARE_API_TOKEN"
    break
  fi
done < "$CF_LIST"

if [ "$DEPLOYED" != 1 ]; then
  echo "WORKER_DEPLOY_FAILED"
  publish "WAITING_DEPLOY_CREDENTIAL" "NO_USABLE_CLOUDFLARE_DEPLOY_CREDENTIAL_FOUND"
  exit 3
fi

printf '{"name":"HERMES_BRIDGE_TOKEN","text":"%s","type":"secret_text"}' "$TOKEN" >"$SECRET"
chmod 600 "$SECRET" 2>/dev/null || true
curl -sS -X PUT   -H "Authorization: Bearer $USED_CF_TOKEN"   -H 'Content-Type: application/json'   --data-binary @"$SECRET"   "$API/accounts/$USED_ACCOUNT/workers/scripts/$SCRIPT/secrets"   >"$RESP" 2>/dev/null || true
if ! grep -Eq '"success"[[:space:]]*:[[:space:]]*true' "$RESP"; then
  echo "SECRET_DEPLOY_FAILED"
  publish "DEPLOY_FAILED" "BRIDGE_SECRET_DEPLOY_FAILED"
  exit 4
fi

# Best effort: ensure workers.dev route is enabled.
curl -sS -X POST   -H "Authorization: Bearer $USED_CF_TOKEN"   -H 'Content-Type: application/json'   --data '{"enabled":true,"previews_enabled":false}'   "$API/accounts/$USED_ACCOUNT/workers/scripts/$SCRIPT/subdomain" >/dev/null 2>&1 || true

sleep 2
STATUS="$(curl -sS --connect-timeout 6 -m 15 "$BASE/status" 2>/dev/null || true)"
if printf '%s' "$STATUS" | grep -q '"HERMES WORK BRIDGE"'; then
  printf '%s\n' "$BASE" >"$STATE/bridge_endpoint"
  chmod 600 "$STATE/bridge_endpoint" 2>/dev/null
  touch "$STATE/bridge_worker_deployed"
  publish "DEPLOYED" "READY_FOR_AGENT"
  echo "BRIDGE_DEPLOY=PASS"
  exit 0
fi

echo "BRIDGE_ENDPOINT_VERIFY_FAILED"
publish "DEPLOYED_WAITING_ENDPOINT" "ENDPOINT_NOT_READY"
exit 5
