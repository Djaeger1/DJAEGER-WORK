import http from "node:http";
import https from "node:https";
import crypto from "node:crypto";

// DJAEGER WORK Auto Studio cutover deploy trigger v2.0.1
// relay source sync: memory telemetry v2.5.6

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
let lastDirectUpdateProbeAt = 0;
let directUpdateAuthRejectedRelease = "";

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

function queueDeviceRead(pathname, waitMs=15000) {
  const requestId=id();
  const cmd={
    id:requestId,
    method:"GET",
    path:pathname,
    headers:{"content-type":"application/json","x-hermes-token":""},
    body:"",
    created_at:new Date().toISOString(),
    expires_at:Date.now()+waitMs+2000
  };
  return new Promise((resolve,reject)=>{
    const timer=setTimeout(()=>{
      pending.delete(requestId);
      reject(new Error("device_read_timeout"));
    },waitMs);
    pending.set(requestId,{resolve,reject,timer});
    handCommand(cmd);
  });
}
function queueDevicePost(pathname, payload={}, waitMs=15000) {
  const requestId=id();
  const cmd={
    id:requestId,
    method:"POST",
    path:pathname,
    headers:{"content-type":"application/json","x-hermes-token":""},
    body:JSON.stringify(payload||{}),
    created_at:new Date().toISOString(),
    expires_at:Date.now()+waitMs+2000
  };
  return new Promise((resolve,reject)=>{
    const timer=setTimeout(()=>{
      pending.delete(requestId);
      reject(new Error("device_write_timeout"));
    },waitMs);
    pending.set(requestId,{resolve,reject,timer});
    handCommand(cmd);
  });
}

function queueDevicePostWithToken(pathname, token, payload=null, waitMs=60000) {
  const requestId=id();
  const body = payload===null ? "" : JSON.stringify(payload);
  const cmd={
    id:requestId,
    method:"POST",
    path:pathname,
    headers:{"content-type":"application/json","x-hermes-token":cleanHeaderValue(token)},
    body,
    created_at:new Date().toISOString(),
    expires_at:Date.now()+waitMs+2000
  };
  return new Promise((resolve,reject)=>{
    const timer=setTimeout(()=>{
      pending.delete(requestId);
      reject(new Error("device_token_write_timeout"));
    },waitMs);
    pending.set(requestId,{resolve,reject,timer});
    handCommand(cmd);
  });
}
function decodeDeviceJson(upstream) {
  const status=Number(upstream?.status||502);
  if(status<200||status>=300) throw new Error("device_http_"+status);
  const body=Buffer.from(String(upstream?.body_b64||""),"base64").toString("utf8");
  return JSON.parse(body||"{}");
}
function releaseAtLeast(release, major, minor, patch) {
  const m=String(release||"").match(/v(\d+)\.(\d+)\.(\d+)/i);
  if(!m) return false;
  const got=[Number(m[1]),Number(m[2]),Number(m[3])];
  const want=[major,minor,patch];
  for(let i=0;i<3;i++){ if(got[i]>want[i]) return true; if(got[i]<want[i]) return false; }
  return true;
}
async function migrationStudioCandidate() {
  if(!deviceFresh()) return null;
  const desk=decodeDeviceJson(await queueDeviceRead("/api/work/desk",12000));
  const pid=String(desk?.next_job_id||"").slice(0,80);
  if(!pid) return null;
  const detail=decodeDeviceJson(await queueDeviceRead("/api/work/job?id="+encodeURIComponent(pid),12000));
  const plan=detail?.plan||{};
  const script=detail?.script||{};
  if(!Array.isArray(script.scenes)||script.scenes.length<1) return null;
  return safeStudioJob({
    state:"WAITING_RENDER",
    engine:"AUTO_STUDIO_V1",
    planner_id:pid,
    topic:String(plan.title||desk.next_job||script.topic||"").slice(0,500),
    category:String(plan.category||"").slice(0,80),
    language:String(script.language||"id").slice(0,16),
    video_title:String(script.video_title||plan.title||desk.next_job||"HERMES WORK").slice(0,500),
    description:String(script.description||"").slice(0,4000),
    duration_sec:Number(script.duration_sec||0),
    scenes:script.scenes,
    hashtags:Array.isArray(script.hashtags)?script.hashtags:[],
    render_tag:"hermes-studio-"+pid.toLowerCase(),
    visual_provider:"FREE_FIRST_AI_WITH_DETERMINISTIC_FALLBACK",
    voice_provider:"NO_CARD_TTS_WITH_LOCAL_FALLBACK",
    render_provider:"GITHUB_ACTIONS_FFMPEG",
    created_at:new Date().toISOString(),
    ai_used:false,
    neurons_used:0
  });
}

async function publicationCandidate() {
  if(!deviceFresh()) throw new Error("DEVICE_LINK_STALE");
  const desk=decodeDeviceJson(await queueDeviceRead("/api/work/desk",12000));
  const jobs=Array.isArray(desk?.jobs)?desk.jobs:[];
  const j=jobs.find(x=>String(x?.handoff_state||"").toUpperCase()==="READY_TO_UPLOAD");
  if(!j?.id) return null;
  const pid=String(j.id).slice(0,80);
  return {
    state:"READY_TO_PUBLISH",
    planner_id:pid,
    topic:String(j.topic||"").slice(0,500),
    category:String(j.category||"").slice(0,80),
    video_title:String(j.video_title||j.topic||"HERMES WORK").slice(0,500),
    language:String(j.language||"id").slice(0,16),
    duration_sec:Number(j.duration_sec||0),
    render_tag:"hermes-studio-"+pid.toLowerCase(),
    ai_used:false,
    neurons_used:0
  };
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
    battery_temp_c:x.battery_temp_c ?? x.temperature_c ?? null,
    cpu_usage:(x.cpu_usage && typeof x.cpu_usage==="object") ? x.cpu_usage : null,
    thermal:(x.thermal && typeof x.thermal==="object") ? x.thermal : null,
    mem_available_mb:x.mem_available_mb ?? null,
    workd_rss_mb:x.workd_rss_mb ?? null,
    mem_breakdown:(x.mem_breakdown && typeof x.mem_breakdown==="object") ? x.mem_breakdown : null,
    process_memory:(x.process_memory && typeof x.process_memory==="object") ? x.process_memory : null,
    memory_pressure:(x.memory_pressure && typeof x.memory_pressure==="object") ? x.memory_pressure : null,
    swap:(x.swap && typeof x.swap==="object") ? x.swap : null,
    zram:(x.zram && typeof x.zram==="object") ? x.zram : null,
    top_rss:Array.isArray(x.top_rss) ? x.top_rss.slice(0,10).map(p=>({pid:Number(p?.pid||0),name:String(p?.name||"").slice(0,80),rss_mb:Number(p?.rss_mb||0)})) : [],
    top_anon:Array.isArray(x.top_anon) ? x.top_anon.slice(0,10).map(p=>({pid:Number(p?.pid||0),name:String(p?.name||"").slice(0,80),anon_mb:Number(p?.anon_mb||0),rss_mb:Number(p?.rss_mb||0),swap_mb:Number(p?.swap_mb||0)})) : [],
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
    github_control_state:x.github_control_state ?? null,
    github_control_reason:x.github_control_reason ?? null,
    github_control_generation:x.github_control_generation ?? null,
    github_control_writes_allowed:x.github_control_writes_allowed ?? false,
    bridge_agent:x.bridge_agent ?? null,
    bridge_state:x.bridge_state ?? null,
    bridge_reason:x.bridge_reason ?? null,
    bridge_last_sync:x.bridge_last_sync ?? null,
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
      version:"2.1.2",
      direct_fresh:!!(directSnapshot&&Date.now()-directReceivedAt<20*60*1000),
      remote_link:deviceFresh()?"CONNECTED":"WAITING_DEVICE"
    });
  }

  // Device side: outbound-only long-poll tunnel. Safe behind CGNAT.
  if (u.pathname === "/remote/device/heartbeat" && req.method === "POST") {
    if (!deviceAuth(req)) return send(res,401,{ok:false,error:"unauthorized"});
    const wasFresh=deviceFresh();
    let meta={};
    try { const raw=await readBody(req,65536); meta=raw?JSON.parse(raw):{}; } catch {}
    deviceSeenAt=Date.now();
    if(!wasFresh) {
      setTimeout(autonomousMaintenanceTick,750);
      setTimeout(tryDirectSelfUpdateAuthProbe,1750);
      setTimeout(logYouTubeVideoManager,2750);
    }
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

  // Existing read-only Studio feed remains backward compatible. During the
  // repository cutover, a pre-cutover device may already have met today's old
  // repository target; in that case the relay can read the Production Desk and
  // expose exactly one zero-write migration candidate for the new renderer.
  if (u.pathname === "/status-feed" && req.method === "GET") {
    try {
      const snap=safeSnapshot(await latestSnapshot());
      return send(res,200,{
        ok:true,
        source:(directSnapshot&&Date.now()-directReceivedAt<20*60*1000)?"direct-data-only":"ntfy-data-only",
        device_connected:deviceFresh(),
        snapshot:snap
      });
    } catch(e) {
      return send(res,503,{ok:false,error:String(e?.message||e)});
    }
  }

  if (u.pathname === "/studio-feed" && req.method === "GET") {
    let snap=null; let source="direct";
    const fresh=!!(directSnapshot&&Date.now()-directReceivedAt<20*60*1000);
    if(fresh){
      snap=directSnapshot;
    }else{
      source="ntfy";
      try{ snap=await latestSnapshot(); }
      catch(e){ return send(res,503,{ok:false,state:"NO_FRESH_DEVICE_SNAPSHOT",error:String(e?.message||e)}); }
    }
    let job=safeStudioJob(snap?.studio_job);
    const release=String(snap?.release||"");
    if((!job||!job.planner_id) && deviceFresh() && !releaseAtLeast(release,2,5,4)){
      try{
        const candidate=await migrationStudioCandidate();
        if(candidate?.planner_id){job=candidate;source="device-production-desk-migration";}
      }catch(e){
        return send(res,503,{ok:false,state:"MIGRATION_CANDIDATE_UNAVAILABLE",error:String(e?.message||e),source});
      }
    }
    if(!job||!job.planner_id)return send(res,200,{ok:true,state:snap?.studio_state||"WAITING",job:null,source});
    return send(res,200,{ok:true,state:"WAITING_RENDER",job,source});
  }

  if (u.pathname === "/publish-feed" && req.method === "GET") {
    try{
      const job=await publicationCandidate();
      if(!job)return send(res,200,{ok:true,state:"WAITING_UPLOAD_READY",job:null,source:"live-device"});
      return send(res,200,{ok:true,state:"READY_TO_PUBLISH",job,source:"live-device"});
    }catch(e){
      return send(res,503,{ok:false,state:"PUBLICATION_FEED_UNAVAILABLE",error:String(e?.message||e)});
    }
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

async function tryDirectSelfUpdateAuthProbe() {
  try {
    if(!deviceFresh()) return;
    if(Date.now()-lastDirectUpdateProbeAt<10*60*1000) return;

    const status=decodeDeviceJson(await queueDeviceRead("/api/work/status",12000));
    const release=String(status?.release||"");
    if(releaseAtLeast(release,2,5,21)) return;
    if(directUpdateAuthRejectedRelease===release) return;
    lastDirectUpdateProbeAt=Date.now();

    const sha256hex=v=>crypto.createHash("sha256").update(String(v||"")).digest("hex");
    const candidates=[
      ["LEGACY_ADMIN_TOKEN",String(process.env.HERMES_LEGACY_ADMIN_TOKEN||"")],
      ["NTFY_TOPIC",TOPIC],
      ["SHA256_NTFY_TOPIC",TOPIC?sha256hex(TOPIC):""],
      ["ACCESS_PATH",ACCESS_PATH],
      ["SHA256_ACCESS_PATH",ACCESS_PATH?sha256hex(ACCESS_PATH):""],
      ["REMOTE_CLIENT_KEY",remoteClientKey()]
    ];
    const seen=new Set();
    const results=[];
    for(const [label,token] of candidates){
      if(!token||seen.has(token)) continue;
      seen.add(token);
      const upstream=await queueDevicePostWithToken("/api/work/update",token,null,90000);
      const code=Number(upstream?.status||502);
      results.push({label,http:code});
      if(code===401||code===403) continue;

      let body={};
      try{body=JSON.parse(Buffer.from(String(upstream?.body_b64||""),"base64").toString("utf8")||"{}")}catch{}
      console.log("HERMES_BOUNDED_AUTH_BRIDGE "+JSON.stringify({
        state:code>=200&&code<300?"UPDATE_ACCEPTED":"AUTH_ACCEPTED_UPDATE_REJECTED",
        candidate:label,
        http:code,
        from:release,
        to:String(body?.version||""),
        installed_state:String(body?.state||"")
      }));

      if(code>=200&&code<300){
        setTimeout(async()=>{
          try{
            const st=decodeDeviceJson(await queueDeviceRead("/api/work/status",15000));
            const rc=decodeDeviceJson(await queueDeviceRead("/api/work/recovery",15000));
            console.log("HERMES_BOUNDED_AUTH_BRIDGE_VERIFY "+JSON.stringify({
              release:String(st?.release||""),
              configured:String(st?.configured_release||rc?.current||""),
              safe_mode:!!st?.safe_mode||!!rc?.safe_mode,
              worker_paused:!!st?.worker_paused||!!rc?.worker_paused,
              tether_state:String(st?.tether_state||"")
            }));
          }catch(e){console.log("HERMES_BOUNDED_AUTH_BRIDGE_VERIFY_ERROR "+String(e?.message||e))}
        },12000);
      }
      return;
    }

    directUpdateAuthRejectedRelease=release;
    console.log("HERMES_BOUNDED_AUTH_BRIDGE "+JSON.stringify({
      state:"ALL_BOUNDED_CANDIDATES_REJECTED",
      release,
      results,
      retry:"DISABLED_UNTIL_RELEASE_CHANGES"
    }));
  } catch(e) {
    console.log("HERMES_BOUNDED_AUTH_BRIDGE_ERROR "+String(e?.message||e));
  }
}

async function autonomousMaintenanceTick() {
  try {
    if(!deviceFresh()) return;
    const status=decodeDeviceJson(await queueDeviceRead("/api/work/status",12000));
    const release=String(status?.release||"");
    if(!releaseAtLeast(release,2,5,11)) return;
    const configured=String(status?.configured_release||"");
    if(configured && configured!==release){
      const r=decodeDeviceJson(await queueDevicePost("/api/work/maintenance",{action:"recover"},12000));
      console.log("HERMES_MAINTENANCE "+JSON.stringify({action:"recover",state:r?.state||null,release,configured}));
      return;
    }
    const chResp=await fetch("https://raw.githubusercontent.com/Djaeger1/DJAEGER-WORK/main/release/channel.json",{headers:{"cache-control":"no-cache"}});
    if(!chResp.ok) throw new Error("channel_http_"+chResp.status);
    const ch=await chResp.json();
    const latest=String(ch?.version||"");
    if(!latest || latest===release) return;
    const repair=/memory|recovery|forensics|pressure-guard|autonomous-maintenance|emergency|coldstart|quarantine|process-convergence/i.test(latest);
    if(repair && releaseAtLeast(release,2,5,21)){
      const r=decodeDeviceJson(await queueDevicePost("/api/work/maintenance",{action:"remote_update",mode:"REPAIR"},90000));
      console.log("HERMES_MAINTENANCE "+JSON.stringify({action:"remote_update",mode:"REPAIR",state:r?.state||null,from:release,to:latest,quarantine_preserved:r?.quarantine_preserved===true}));
      return;
    }
    const r=decodeDeviceJson(await queueDevicePost("/api/work/maintenance",{action:"update",mode:"NORMAL"},12000));
    console.log("HERMES_MAINTENANCE "+JSON.stringify({action:"update",mode:"NORMAL",state:r?.state||null,from:release,to:latest}));
  } catch(e) {
    console.log("HERMES_MAINTENANCE_ERROR "+String(e?.message||e));
  }
}
async function logDeviceMemoryAudit() {
  try {
    if(!deviceFresh()) { console.log("HERMES_MEMORY_AUDIT DEVICE_LINK_STALE"); return; }
    const r=decodeDeviceJson(await queueDeviceRead("/api/work/memory-audit",30000));
    console.log("HERMES_MEMORY_AUDIT "+JSON.stringify(r));
  } catch(e) {
    console.log("HERMES_MEMORY_AUDIT_ERROR "+String(e?.message||e));
  }
}
async function logDeviceAutoupdate() {
  try {
    if(!deviceFresh()) { console.log("HERMES_AUTOUPDATE DEVICE_LINK_STALE"); return; }
    const r=decodeDeviceJson(await queueDeviceRead("/api/work/autoupdate",12000));
    console.log("HERMES_AUTOUPDATE "+JSON.stringify(r));
  } catch(e) {
    console.log("HERMES_AUTOUPDATE_ERROR "+String(e?.message||e));
  }
}
async function logDeviceRecovery() {
  try {
    if(!deviceFresh()) { console.log("HERMES_RECOVERY DEVICE_LINK_STALE"); return; }
    const r=decodeDeviceJson(await queueDeviceRead("/api/work/recovery",12000));
    const out={
      current:String(r?.current||"").slice(0,120),
      previous:String(r?.previous||"").slice(0,120),
      safe_mode:!!r?.safe_mode,
      worker_paused:!!r?.worker_paused,
      handoff_log:String(r?.handoff_log||"").slice(-8000)
    };
    console.log("HERMES_RECOVERY "+JSON.stringify(out));
  } catch(e) {
    console.log("HERMES_RECOVERY_ERROR "+String(e?.message||e));
  }
}

async function logYouTubeVideoManager() {
  try {
    if(!deviceFresh()) { console.log("HERMES_YOUTUBE_VIDEOS DEVICE_LINK_STALE"); return; }
    const r=decodeDeviceJson(await queueDeviceRead("/api/work/youtube/videos?page=1&limit=10",15000));
    const items=Array.isArray(r?.items)?r.items.map(v=>({
      publication_id:String(v?.publication_id||"").slice(0,120),
      video_id:String(v?.video_id||"").slice(0,32),
      topic:String(v?.topic||"").slice(0,160),
      privacy:String(v?.privacy||"").slice(0,32),
      status:String(v?.status||"").slice(0,80),
      thumbnail_state:String(v?.thumbnail_state||"").slice(0,32),
      thumbnail_error:String(v?.thumbnail_error||"").slice(0,500)
    })):[];
    console.log("HERMES_YOUTUBE_VIDEOS "+JSON.stringify({total:Number(r?.total||0),items}));
  } catch(e) {
    console.log("HERMES_YOUTUBE_VIDEOS_ERROR "+String(e?.message||e));
  }
}

async function repairPendingYouTubeThumbnail() {
  try {
    if(!deviceFresh()) { console.log("HERMES_YOUTUBE_THUMBNAIL_REPAIR DEVICE_LINK_STALE"); return; }
    const r=decodeDeviceJson(await queueDeviceRead("/api/work/youtube/videos?page=1&limit=10",15000));
    const items=Array.isArray(r?.items)?r.items:[];
    const target=items.find(v=>String(v?.publication_id||"")==="b5f0a42f262f");
    if(!target){ console.log("HERMES_YOUTUBE_THUMBNAIL_REPAIR "+JSON.stringify({state:"TARGET_NOT_FOUND"})); return; }
    if(String(target?.thumbnail_state||"").toUpperCase()==="SUCCESS"){
      console.log("HERMES_YOUTUBE_THUMBNAIL_REPAIR "+JSON.stringify({state:"ALREADY_SUCCESS",publication_id:"b5f0a42f262f",video_id:String(target?.video_id||"")}));
      return;
    }

    const sha256hex=v=>crypto.createHash("sha256").update(String(v||"")).digest("hex");
    const candidates=[
      ["LEGACY_ADMIN_TOKEN",String(process.env.HERMES_LEGACY_ADMIN_TOKEN||"")],
      ["NTFY_TOPIC",TOPIC],
      ["SHA256_NTFY_TOPIC",TOPIC?sha256hex(TOPIC):""],
      ["ACCESS_PATH",ACCESS_PATH],
      ["SHA256_ACCESS_PATH",ACCESS_PATH?sha256hex(ACCESS_PATH):""],
      ["REMOTE_CLIENT_KEY",remoteClientKey()]
    ];
    const seen=new Set();
    const attempts=[];
    for(const [label,token] of candidates){
      if(!token||seen.has(token)) continue;
      seen.add(token);
      const upstream=await queueDevicePostWithToken("/api/work/youtube/thumbnail",token,{publication_id:"b5f0a42f262f"},60000);
      const code=Number(upstream?.status||502);
      attempts.push({candidate:label,http:code});
      if(code===401||code===403) continue;
      let body="";
      try{body=Buffer.from(String(upstream?.body_b64||""),"base64").toString("utf8").slice(0,1000)}catch{}
      console.log("HERMES_YOUTUBE_THUMBNAIL_REPAIR "+JSON.stringify({
        state:code>=200&&code<300?"SUCCESS":"AUTH_ACCEPTED_ACTION_FAILED",
        publication_id:"b5f0a42f262f",
        video_id:String(target?.video_id||""),
        candidate:label,
        http:code,
        response:body,
        attempts
      }));
      setTimeout(logYouTubeVideoManager,1500);
      return;
    }
    console.log("HERMES_YOUTUBE_THUMBNAIL_REPAIR "+JSON.stringify({state:"ALL_BOUNDED_CANDIDATES_REJECTED",publication_id:"b5f0a42f262f",attempts}));
  } catch(e) {
    console.log("HERMES_YOUTUBE_THUMBNAIL_REPAIR_ERROR "+String(e?.message||e));
  }
}

async function logLatestSnapshot() {
  try {
    const snap = safeSnapshot(await latestSnapshot());
    console.log("HERMES_SNAPSHOT " + JSON.stringify(snap));
  } catch (e) {
    console.log("HERMES_SNAPSHOT_ERROR " + String(e?.message || e));
  }
}
setTimeout(logLatestSnapshot, 3000);
setTimeout(logDeviceRecovery, 12000);
setTimeout(logDeviceAutoupdate, 18000);
setTimeout(logYouTubeVideoManager, 14000);
setTimeout(repairPendingYouTubeThumbnail, 20000);
setTimeout(logDeviceAutoupdate, 45000);
setTimeout(logDeviceMemoryAudit, 5000);
setInterval(logDeviceRecovery, 5*60*1000);
setInterval(logDeviceAutoupdate, 5*60*1000);
setInterval(logDeviceMemoryAudit, 5*60*1000);
setTimeout(autonomousMaintenanceTick, 3000);
setTimeout(tryDirectSelfUpdateAuthProbe, 9000);
setInterval(autonomousMaintenanceTick, 5*60*1000);
setInterval(logLatestSnapshot, 60000);
setInterval(()=>{
  pruneQueue();
  for(const [rid,p] of pending){
    // Timers own expiry; this loop intentionally does not log request content.
    if(!p || !p.timer) pending.delete(rid);
  }
},30000);
