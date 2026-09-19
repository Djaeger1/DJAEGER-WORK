import http from "node:http";
import https from "node:https";
import crypto from "node:crypto";

const PORT = Number(process.env.PORT || 3000);
const TOPIC = process.env.NTFY_TOPIC || "";
const ACCESS_PATH = process.env.ACCESS_PATH || "";
const NTFY = process.env.NTFY_BASE || "https://ntfy.sh";

let directSnapshot = null;
let directReceivedAt = 0;

// DJAEGER WORK remote-link state.
// The device authenticates with the already-existing derived HERMES topic.
// The app uses a separate deterministic key derived from that topic.
// No secret value is logged.
const commandQueue = [];
const pollWaiters = [];
const pending = new Map();
let deviceSeenAt = 0;
let deviceMeta = {};

function send(res, code, obj, extraHeaders={}) {
  const body = JSON.stringify(obj);
  res.writeHead(code, {
    "content-type":"application/json; charset=utf-8",
    "cache-control":"no-store",
    ...extraHeaders
  });
  res.end(body);
}

function remoteClientKey() {
  if (!TOPIC) return "";
  return crypto.createHash("sha256")
    .update("DJAEGER_WORK_REMOTE_CLIENT:" + TOPIC)
    .digest("hex");
}
function deviceAuth(req) {
  return !!TOPIC && req.headers["x-hermes-topic"] === TOPIC;
}
function clientAuth(req) {
  const expected = remoteClientKey();
  const got = String(req.headers["x-djaeger-remote-key"] || "");
  if (!expected || !got || expected.length !== got.length) return false;
  try { return crypto.timingSafeEqual(Buffer.from(expected), Buffer.from(got)); }
  catch { return false; }
}
function deviceFresh() {
  return deviceSeenAt > 0 && Date.now() - deviceSeenAt < 90000;
}
function id() {
  return crypto.randomBytes(16).toString("hex");
}
function cleanHeaderValue(v,max=4096) {
  return String(v ?? "").slice(0,max);
}
function pruneQueue() {
  const now=Date.now();
  for(let i=commandQueue.length-1;i>=0;i--){
    if(commandQueue[i].expires_at <= now) commandQueue.splice(i,1);
  }
}
function handCommand(cmd) {
  pruneQueue();
  while (pollWaiters.length) {
    const waiter = pollWaiters.shift();
    if (!waiter.done) {
      waiter.done=true;
      clearTimeout(waiter.timer);
      waiter.resolve(cmd);
      return;
    }
  }
  commandQueue.push(cmd);
  if (commandQueue.length > 64) commandQueue.shift();
}
function nextCommand(waitMs=25000) {
  pruneQueue();
  if (commandQueue.length) return Promise.resolve(commandQueue.shift());
  return new Promise(resolve=>{
    const waiter={done:false,resolve,timer:null};
    waiter.timer=setTimeout(()=>{
      if(waiter.done)return;
      waiter.done=true;
      const i=pollWaiters.indexOf(waiter);
      if(i>=0)pollWaiters.splice(i,1);
      resolve(null);
    },waitMs);
    pollWaiters.push(waiter);
  });
}
function readBody(req, max=524288) {
  return new Promise((resolve,reject)=>{
    let body=""; let size=0; let finished=false;
    req.setEncoding("utf8");
    req.on("data",chunk=>{
      if(finished)return;
      size+=Buffer.byteLength(chunk);
      if(size>max){finished=true;reject(Object.assign(new Error("payload_too_large"),{code:413}));req.destroy();return;}
      body+=chunk;
    });
    req.on("end",()=>{if(!finished){finished=true;resolve(body)}});
    req.on("error",e=>{if(!finished){finished=true;reject(e)}});
  });
}
function queueProxy(req,u,body) {
  const requestId=id();
  const cmd={
    id:requestId,
    method:req.method,
    path:u.pathname+u.search,
    headers:{
      "content-type":cleanHeaderValue(req.headers["content-type"] || "application/json"),
      "x-hermes-token":cleanHeaderValue(req.headers["x-hermes-token"] || "")
    },
    body:body || "",
    created_at:new Date().toISOString(),
    expires_at:Date.now()+40000
  };
  return new Promise((resolve,reject)=>{
    const timer=setTimeout(()=>{
      pending.delete(requestId);
      reject(new Error("device_response_timeout"));
    },38000);
    pending.set(requestId,{resolve,reject,timer});
    handCommand(cmd);
  });
}

function safeStudioJob(j) {
  if (!j || typeof j !== "object") return null;
  const scenes = Array.isArray(j.scenes) ? j.scenes.slice(0,12).map(s=>({
    number:Number(s.number||0),
    duration_sec:Number(s.duration_sec||0),
    purpose:String(s.purpose||"").slice(0,240),
    voice_over:String(s.voice_over||"").slice(0,1200),
    visual_prompt:String(s.visual_prompt||"").slice(0,2400),
    on_screen_text:String(s.on_screen_text||"").slice(0,300),
    edit_note:String(s.edit_note||"").slice(0,500)
  })) : [];
  return {
    state:String(j.state||"WAITING_RENDER"),
    engine:"AUTO_STUDIO_V1",
    planner_id:String(j.planner_id||"").slice(0,80),
    topic:String(j.topic||"").slice(0,500),
    category:String(j.category||"").slice(0,80),
    language:String(j.language||"").slice(0,16),
    video_title:String(j.video_title||"").slice(0,500),
    description:String(j.description||"").slice(0,4000),
    duration_sec:Number(j.duration_sec||0),
    scenes,
    hashtags:Array.isArray(j.hashtags)?j.hashtags.slice(0,30).map(v=>String(v).slice(0,100)):[],
    render_tag:String(j.render_tag||"").slice(0,120),
    visual_provider:String(j.visual_provider||"").slice(0,120),
    voice_provider:String(j.voice_provider||"").slice(0,120),
    render_provider:String(j.render_provider||"").slice(0,120),
    created_at:j.created_at||null,
    ai_used:false,
    neurons_used:0
  };
}

function safeSnapshot(x={}) {
  return {
    service:"HERMES_WORK",
    release:x.release ?? null,
    tether_state:x.tether_state ?? null,
    temperature_c:x.temperature_c ?? null,
    mem_available_mb:x.mem_available_mb ?? null,
    worker_state:x.worker_state ?? null,
    safe_mode:x.safe_mode ?? null,
    research_total:x.research_total ?? null,
    last_research:x.last_research ?? null,
    research_engine:x.research_engine ?? null,
    daily_brief_state:x.daily_brief_state ?? null,
    ideas_ready:x.ideas_ready ?? null,
    top_opportunity:x.top_opportunity ?? null,
    opportunity_engine:x.opportunity_engine ?? null,
    planner_state:x.planner_state ?? null,
    planner_queue:x.planner_queue ?? null,
    next_for_script:x.next_for_script ?? null,
    planner_engine:x.planner_engine ?? null,
    script_prep_state:x.script_prep_state ?? null,
    script_prep_ready:x.script_prep_ready ?? null,
    script_topic:x.script_topic ?? null,
    script_prep_engine:x.script_prep_engine ?? null,
    script_state:x.script_state ?? null,
    scripts_ready:x.scripts_ready ?? null,
    final_script_topic:x.final_script_topic ?? null,
    script_engine:x.script_engine ?? null,
    production_pack_state:x.production_pack_state ?? null,
    production_ready:x.production_ready ?? null,
    production_topic:x.production_topic ?? null,
    production_engine:x.production_engine ?? null,
    handoff_state:x.handoff_state ?? null,
    handoff_queue:x.handoff_queue ?? null,
    next_handoff_job:x.next_handoff_job ?? null,
    handoff_engine:x.handoff_engine ?? null,
    production_desk_state:x.production_desk_state ?? null,
    production_desk_engine:x.production_desk_engine ?? null,
    studio_state:x.studio_state ?? null,
    studio_engine:x.studio_engine ?? null,
    studio_job:safeStudioJob(x.studio_job),
    publication_state:x.publication_state ?? null,
    publications_total:x.publications_total ?? null,
    publication_engine:x.publication_engine ?? null,
    feedback_state:x.feedback_state ?? null,
    performance_records:x.performance_records ?? null,
    strong_signal:x.strong_signal ?? null,
    weak_signal:x.weak_signal ?? null,
    feedback_engine:x.feedback_engine ?? null,
    auto_update_state:x.auto_update_state ?? null,
    auto_update_last_check:x.auto_update_last_check ?? null,
    bridge_agent:x.bridge_agent ?? null,
    ai_used:x.ai_used ?? false,
    neurons_used:x.neurons_used ?? 0,
    sent_at:x.sent_at ?? null
  };
}

function ntfyViaHttps(url) {
  return new Promise((resolve,reject)=>{
    const req=https.get(url,{family:4,headers:{"user-agent":"HERMES-WORK-ChatGPT-Relay/2.0"}},res=>{
      let data="";res.setEncoding("utf8");
      res.on("data",chunk=>{if(data.length<2_000_000)data+=chunk});
      res.on("end",()=>res.statusCode>=200&&res.statusCode<300?resolve(data):reject(new Error("NTFY_HTTP_"+res.statusCode)));
    });
    req.setTimeout(15000,()=>req.destroy(new Error("NTFY_TIMEOUT")));
    req.on("error",reject);
  });
}
function parseNtfy(text) {
  const lines = text.split(/\r?\n/).map(v=>v.trim()).filter(Boolean);
  let latest = null;
  for (const line of lines) {
    try {
      const ev = JSON.parse(line);
      if (!ev || ev.event !== "message" || typeof ev.message !== "string") continue;
      const snap = JSON.parse(ev.message);
      if (snap && typeof snap === "object") latest = snap;
    } catch {}
  }
  return latest;
}
async function latestSnapshot() {
  if (directSnapshot && Date.now()-directReceivedAt < 20*60*1000) return directSnapshot;
  if (!TOPIC) throw new Error("NTFY_TOPIC_NOT_CONFIGURED");
  const url = `${NTFY}/${encodeURIComponent(TOPIC)}/json?poll=1&since=30m`;
  let firstErr = null;
  try {
    const r = await fetch(url, {headers:{"user-agent":"HERMES-WORK-ChatGPT-Relay/2.0"},signal:AbortSignal.timeout(15000)});
    if (!r.ok) throw new Error(`NTFY_HTTP_${r.status}`);
    const latest=parseNtfy(await r.text());
    if (latest) return latest;
    firstErr=new Error("NO_RECENT_SNAPSHOT");
  } catch(e) { firstErr=e; }
  try {
    const latest=parseNtfy(await ntfyViaHttps(url));
    if (latest) return latest;
    throw new Error("NO_RECENT_SNAPSHOT");
  } catch(e) {
    const a=String(firstErr?.cause?.code||firstErr?.code||firstErr?.message||firstErr||"unknown");
    const b=String(e?.cause?.code||e?.code||e?.message||e||"unknown");
    throw new Error("NTFY_UNAVAILABLE primary="+a+" fallback="+b);
  }
}

const server = http.createServer(async (req,res)=>{
  const u = new URL(req.url, "http://localhost");

  if (u.pathname === "/health") {
    return send(res,200,{
      ok:true,
      service:"DJAEGER_WORK_REMOTE_RELAY",
      version:"2.0.0",
      direct_fresh:!!(directSnapshot&&Date.now()-directReceivedAt<20*60*1000),
      remote_link:deviceFresh()?"CONNECTED":"WAITING_DEVICE"
    });
  }

  // Device side: outbound-only long-poll tunnel. Safe behind CGNAT.
  if (u.pathname === "/remote/device/heartbeat" && req.method === "POST") {
    if (!deviceAuth(req)) return send(res,401,{ok:false,error:"unauthorized"});
    let meta={};
    try { const raw=await readBody(req,65536); meta=raw?JSON.parse(raw):{}; } catch {}
    deviceSeenAt=Date.now();
    deviceMeta={
      release:String(meta.release||"").slice(0,80),
      device:String(meta.device||"REDMI_5A").slice(0,80),
      seen_at:new Date(deviceSeenAt).toISOString()
    };
    return send(res,200,{ok:true,state:"CONNECTED"});
  }

  if (u.pathname === "/remote/device/poll" && req.method === "GET") {
    if (!deviceAuth(req)) return send(res,401,{ok:false,error:"unauthorized"});
    deviceSeenAt=Date.now();
    const cmd=await nextCommand(25000);
    return send(res,200,{ok:true,command:cmd});
  }

  if (u.pathname === "/remote/device/respond" && req.method === "POST") {
    if (!deviceAuth(req)) return send(res,401,{ok:false,error:"unauthorized"});
    deviceSeenAt=Date.now();
    try {
      const raw=await readBody(req,1_500_000);
      const v=JSON.parse(raw||"{}");
      const p=pending.get(String(v.id||""));
      if(!p)return send(res,404,{ok:false,error:"unknown_request"});
      pending.delete(String(v.id));
      clearTimeout(p.timer);
      p.resolve({
        status:Math.max(100,Math.min(599,Number(v.status||502))),
        content_type:String(v.content_type||"application/json; charset=utf-8").slice(0,200),
        body_b64:String(v.body_b64||"")
      });
      return send(res,200,{ok:true});
    } catch(e) {
      return send(res,e?.code===413?413:400,{ok:false,error:String(e?.message||"invalid_response")});
    }
  }

  if (u.pathname === "/remote/info" && req.method === "GET") {
    if (!clientAuth(req)) return send(res,401,{ok:false,error:"unauthorized"});
    return send(res,200,{
      ok:true,
      service:"DJAEGER_WORK_REMOTE",
      device_connected:deviceFresh(),
      device:deviceMeta,
      queued:commandQueue.length,
      pending:pending.size
    });
  }

  // Client side: same DJAEGER WORK API paths. The relay never exposes arbitrary ports
  // or shell access; only /api/work/* is eligible for tunneling.
  if (u.pathname.startsWith("/api/work/")) {
    if (!clientAuth(req)) return send(res,401,{ok:false,error:"unauthorized"});
    if (!["GET","POST"].includes(req.method)) return send(res,405,{ok:false,error:"method_not_allowed"});
    if (!deviceFresh()) return send(res,503,{ok:false,error:"device_offline"});
    try {
      const body=req.method==="POST"?await readBody(req,524288):"";
      const upstream=await queueProxy(req,u,body);
      let decoded;
      try { decoded=Buffer.from(upstream.body_b64||"","base64"); }
      catch { return send(res,502,{ok:false,error:"invalid_device_response"}); }
      res.writeHead(upstream.status,{
        "content-type":upstream.content_type,
        "cache-control":"no-store",
        "x-djaeger-route":"REMOTE"
      });
      res.end(decoded);
      return;
    } catch(e) {
      const code=e?.code===413?413:504;
      return send(res,code,{ok:false,error:String(e?.message||"remote_proxy_failed")});
    }
  }

  // Existing read-only relay remains backward compatible.
  if (u.pathname === "/studio-feed" && req.method === "GET") {
    const fresh=!!(directSnapshot&&Date.now()-directReceivedAt<20*60*1000);
    if(!fresh)return send(res,503,{ok:false,state:"NO_FRESH_DEVICE_SNAPSHOT"});
    const job=safeStudioJob(directSnapshot.studio_job);
    if(!job||!job.planner_id)return send(res,200,{ok:true,state:directSnapshot.studio_state||"WAITING",job:null});
    return send(res,200,{ok:true,state:directSnapshot.studio_state||"WAITING_RENDER",job});
  }

  if (u.pathname === "/ingest" && req.method === "POST") {
    if (!TOPIC || req.headers["x-hermes-topic"] !== TOPIC) return send(res,401,{ok:false,error:"unauthorized"});
    try {
      const body=await readBody(req,262144);
      const raw=JSON.parse(body);
      directSnapshot=safeSnapshot(raw);directReceivedAt=Date.now();
      return send(res,200,{ok:true,source:"direct-data-only",received_at:new Date(directReceivedAt).toISOString()});
    } catch(e) {
      return send(res,e?.code===413?413:400,{ok:false,error:e?.code===413?"payload_too_large":"invalid_json"});
    }
  }

  if (!ACCESS_PATH || u.pathname !== "/" + ACCESS_PATH) {
    return send(res,404,{error:"not_found"});
  }
  try {
    const snap = await latestSnapshot();
    return send(res,200,{ok:true,source:(directSnapshot&&Date.now()-directReceivedAt<20*60*1000)?"direct-data-only":"ntfy-data-only",snapshot:safeSnapshot(snap)});
  } catch (e) {
    return send(res,503,{ok:false,error:String(e?.message || e)});
  }
});

server.listen(PORT,"0.0.0.0",()=>console.log(`relay listening on ${PORT} remote-link=enabled`));

async function logLatestSnapshot() {
  try {
    const snap = safeSnapshot(await latestSnapshot());
    console.log("HERMES_SNAPSHOT " + JSON.stringify(snap));
  } catch (e) {
    console.log("HERMES_SNAPSHOT_ERROR " + String(e?.message || e));
  }
}
setTimeout(logLatestSnapshot, 3000);
setInterval(logLatestSnapshot, 60000);
setInterval(()=>{
  pruneQueue();
  for(const [rid,p] of pending){
    // Timers own expiry; this loop intentionally does not log request content.
    if(!p || !p.timer) pending.delete(rid);
  }
},30000);
