package main
import("archive/zip";"crypto/rand";"crypto/sha256";"crypto/tls";"crypto/x509";"encoding/base64";"encoding/hex";"encoding/json";"flag";"fmt";"io";"net";"net/http";"net/url";"os";"path/filepath";"sort";"strconv";"strings";"time")
type S struct{Root,Rel string;Port int;Token string}
func runtimeRelease(s *S)string{v:=filepath.Base(filepath.Clean(s.Rel));if v==""||v=="."||v=="/"{return"UNKNOWN"};return v}
func readenv(p,k string)string{b,_:=os.ReadFile(p);for _,l:=range strings.Split(string(b),"\n"){x:=strings.SplitN(l,"=",2);if len(x)==2&&x[0]==k{return strings.TrimSpace(x[1])}};return ""}
func cfgval(p,k string)string{b,_:=os.ReadFile(p);for _,l:=range strings.Split(string(b),"\n"){l=strings.TrimSpace(l);if l==""||strings.HasPrefix(l,"#"){continue};x:=strings.SplitN(l,"=",2);if len(x)==2&&strings.TrimSpace(x[0])==k{return strings.Trim(strings.TrimSpace(x[1]),"'\\\"")}};return ""}
func (s *S)ensureBridgeKey()string{p:=filepath.Join(s.Root,"state","bridge_key");if v:=strings.TrimSpace(readfile(p));len(v)>=32{return v};b:=make([]byte,32);if _,e:=rand.Read(b);e!=nil{return ""};v:=hex.EncodeToString(b);os.MkdirAll(filepath.Dir(p),0700);if os.WriteFile(p,[]byte(v+"\n"),0600)!=nil{return ""};return v}
func (s *S)bridgeCred()(string,string){ep:=readenv(filepath.Join(s.Rel,"config","work.env"),"BRIDGE_URL");if ep==""{ep="https://hermes-work-bridge.moclomper.workers.dev"};return strings.TrimRight(ep,"/"),s.ensureBridgeKey()}
func (s *S)researchTotal()int{b,_:=os.ReadFile(filepath.Join(s.Root,"data","database","research.jsonl"));n:=0;for _,l:=range strings.Split(strings.TrimSpace(string(b)),"\n"){if strings.TrimSpace(l)!=""{n++}};return n}
func (s *S)bridgeInfo()map[string]any{v:=map[string]any{"state":"STARTING","mode":"DATA_ONLY","ai_used":false,"neurons_used":0};b,e:=os.ReadFile(filepath.Join(s.Root,"state","bridge.json"));if e==nil{_ = json.Unmarshal(b,&v)};return v}
func (s *S)writeBridge(st,reason string,httpCode int){ep,_:=s.bridgeCred();v:=map[string]any{"state":st,"reason":reason,"endpoint":ep,"mode":"DATA_ONLY","ai_used":false,"neurons_used":0,"http_code":httpCode,"updated_at":time.Now().Format(time.RFC3339)};if st=="CONNECTED"{v["last_sync"]=time.Now().Format(time.RFC3339)};b,_:=json.Marshal(v);os.MkdirAll(filepath.Join(s.Root,"state"),0700);tmp:=filepath.Join(s.Root,"state","bridge.json.tmp");_ = os.WriteFile(tmp,b,0600);_ = os.Rename(tmp,filepath.Join(s.Root,"state","bridge.json"))}
func (s *S)bridgeSnapshot()map[string]any{ts,_:=iface("rndis0");worker:="READY";if exists(filepath.Join(s.Root,"state","safe_mode")){worker="SAFE_MODE"}else if exists(filepath.Join(s.Root,"state","worker_paused")){worker="PAUSED"};au:=s.autoUpdateInfo();db:=s.dailyBriefInfo();sp:=s.scriptPrepInfo();fs:=s.scriptInfo();pp:=s.productionInfo();ho:=s.handoffInfo();desk:=s.deskInfo();studio:=s.studioInfo();pub:=s.publicationSummary();fb:=s.performanceSummary();pl:=s.plannerInfo();return map[string]any{"sent_at":time.Now().Format(time.RFC3339),"release":runtimeRelease(s),"configured_release":strings.TrimSpace(readfile(filepath.Join(s.Root,"current_release"))),"tether_state":ts,"temperature_c":temp(),"mem_available_mb":mem(),"worker_state":worker,"safe_mode":exists(filepath.Join(s.Root,"state","safe_mode")),"research_total":s.researchTotal(),"last_research":strings.TrimSpace(readfile(filepath.Join(s.Root,"state","last_research"))),"research_engine":"SUGGEST_MULTI_V2","daily_brief_state":db["state"],"ideas_ready":db["ideas_ready"],"top_opportunity":db["top_opportunity"],"opportunity_engine":"OPPORTUNITY_V1","planner_state":pl["state"],"planner_queue":pl["queue_total"],"next_for_script":pl["next_for_script"],"planner_engine":"PLANNER_V1","script_prep_state":sp["state"],"script_prep_ready":sp["ready"],"script_topic":sp["current_topic"],"script_prep_engine":"SCRIPT_PREP_V1","script_state":fs["state"],"scripts_ready":fs["ready"],"final_script_topic":fs["current_topic"],"script_engine":"SCRIPT_ENGINE_V1","production_pack_state":pp["state"],"production_ready":pp["ready"],"production_topic":pp["current_topic"],"production_engine":"PRODUCTION_PACK_V1","handoff_state":ho["state"],"handoff_queue":ho["queue_total"],"next_handoff_job":ho["next_job"],"handoff_engine":"HANDOFF_V1","production_desk_state":desk["state"],"production_desk_engine":"PRODUCTION_DESK_V1","studio_state":studio["state"],"studio_engine":"AUTO_STUDIO_V1","studio_job":studio["job"],"publication_state":pub["state"],"publications_total":pub["records"],"publication_engine":"PUBLICATION_V1","feedback_state":fb["state"],"performance_records":fb["records"],"strong_signal":fb["strong_signal"],"weak_signal":fb["weak_signal"],"feedback_engine":"FEEDBACK_V1","auto_update_state":au["state"],"auto_update_last_check":au["last_check"],"bridge_agent":"HERMES_WORK_DATA_BRIDGE_v3","ai_used":false,"neurons_used":0}}
func (s *S)ntfyTopic(key string)string{h:=sha256.Sum256([]byte("HERMES_WORK_NTFY:"+key));return "hermes-work-"+hex.EncodeToString(h[:24])}
func (s *S)pushNtfy(key string)(int,string,error){topic:=s.ntfyTopic(key);snap,_:=json.Marshal(s.bridgeSnapshot());u:="https://ntfy.sh/"+topic;req,e:=http.NewRequest("POST",u,strings.NewReader(string(snap)));if e!=nil{return 0,"",e};req.Header.Set("Content-Type","text/plain; charset=utf-8");req.Header.Set("Title","HERMES WORK status");req.Header.Set("Tags","computer");cl:=androidHTTPClient();cl.Timeout=20*time.Second;resp,e:=cl.Do(req);if e!=nil{return 0,"",e};io.Copy(io.Discard,io.LimitReader(resp.Body,4096));resp.Body.Close();readURL:="https://ntfy.sh/"+topic+"/json?poll=1&since=10m";if resp.StatusCode>=200&&resp.StatusCode<300{return resp.StatusCode,readURL,nil};return resp.StatusCode,readURL,fmt.Errorf("ntfy http %d",resp.StatusCode)}
func (s *S)pushCloud(ep,key string)(int,error){b,_:=json.Marshal(s.bridgeSnapshot());req,e:=http.NewRequest("POST",ep+"/v1/bridge/push",strings.NewReader(string(b)));if e!=nil{return 0,e};req.Header.Set("Authorization","Bearer "+key);req.Header.Set("Content-Type","application/json");cl:=androidHTTPClient();cl.Timeout=20*time.Second;resp,e:=cl.Do(req);if e!=nil{return 0,e};body,_:=io.ReadAll(io.LimitReader(resp.Body,4096));resp.Body.Close();if resp.StatusCode>=200&&resp.StatusCode<300{return resp.StatusCode,nil};return resp.StatusCode,fmt.Errorf("cloud http %d %s",resp.StatusCode,strings.TrimSpace(string(body)))}
func (s *S)pushRailway(key string)(int,error){base:=strings.TrimRight(readenv(filepath.Join(s.Rel,"config","work.env"),"RAILWAY_RELAY_URL"),"/");if base==""{return 0,fmt.Errorf("railway relay url missing")};b,_:=json.Marshal(s.bridgeSnapshot());req,e:=http.NewRequest("POST",base+"/ingest",strings.NewReader(string(b)));if e!=nil{return 0,e};req.Header.Set("X-Hermes-Topic",s.ntfyTopic(key));req.Header.Set("Content-Type","application/json");cl:=androidHTTPClient();cl.Timeout=20*time.Second;resp,e:=cl.Do(req);if e!=nil{return 0,e};body,_:=io.ReadAll(io.LimitReader(resp.Body,4096));resp.Body.Close();if resp.StatusCode>=200&&resp.StatusCode<300{return resp.StatusCode,nil};return resp.StatusCode,fmt.Errorf("railway http %d %s",resp.StatusCode,strings.TrimSpace(string(body)))}

func (s *S)remoteConfig()(string,string,string){base:=strings.TrimRight(readenv(filepath.Join(s.Rel,"config","work.env"),"REMOTE_RELAY_URL"),"/");_,key:=s.bridgeCred();if base==""||key==""{return base,"",""};topic:=s.ntfyTopic(key);h:=sha256.Sum256([]byte("DJAEGER_WORK_REMOTE_CLIENT:"+topic));return base,topic,hex.EncodeToString(h[:])}
func privateRemote(r *http.Request)bool{host,_,e:=net.SplitHostPort(r.RemoteAddr);if e!=nil{host=r.RemoteAddr};ip:=net.ParseIP(strings.TrimSpace(host));return ip!=nil&&(ip.IsLoopback()||ip.IsPrivate())}
func (s *S)remoteInfo(w http.ResponseWriter,r *http.Request){if !privateRemote(r){http.Error(w,"local_pairing_only",http.StatusForbidden);return};base,_,client:=s.remoteConfig();if base==""||client==""{http.Error(w,"remote_not_configured",http.StatusServiceUnavailable);return};js(w,map[string]any{"ok":true,"mode":"AUTO_LOCAL_REMOTE","local_url":fmt.Sprintf("http://192.168.42.129:%d",s.Port),"remote_url":base,"remote_key":client,"release":runtimeRelease(s)})}
func (s *S)remoteDevicePost(base,topic,path string,v any)(int,error){b,_:=json.Marshal(v);req,e:=http.NewRequest("POST",base+path,strings.NewReader(string(b)));if e!=nil{return 0,e};req.Header.Set("X-Hermes-Topic",topic);req.Header.Set("Content-Type","application/json");cl:=androidHTTPClient();cl.Timeout=20*time.Second;resp,e:=cl.Do(req);if e!=nil{return 0,e};io.Copy(io.Discard,io.LimitReader(resp.Body,4096));resp.Body.Close();if resp.StatusCode>=200&&resp.StatusCode<300{return resp.StatusCode,nil};return resp.StatusCode,fmt.Errorf("remote post http %d",resp.StatusCode)}
func (s *S)remotePoll(base,topic string)(map[string]any,error){req,e:=http.NewRequest("GET",base+"/remote/device/poll",nil);if e!=nil{return nil,e};req.Header.Set("X-Hermes-Topic",topic);cl:=androidHTTPClient();cl.Timeout=32*time.Second;resp,e:=cl.Do(req);if e!=nil{return nil,e};defer resp.Body.Close();b,_:=io.ReadAll(io.LimitReader(resp.Body,1048576));if resp.StatusCode<200||resp.StatusCode>=300{return nil,fmt.Errorf("remote poll http %d",resp.StatusCode)};var v map[string]any;if json.Unmarshal(b,&v)!=nil{return nil,fmt.Errorf("remote poll invalid json")};cmd,_:=v["command"].(map[string]any);return cmd,nil}
func (s *S)remoteExecute(cmd map[string]any)map[string]any{id:=fmt.Sprint(cmd["id"]);method:=strings.ToUpper(fmt.Sprint(cmd["method"]));path:=fmt.Sprint(cmd["path"]);if id==""||!strings.HasPrefix(path,"/api/work/")||(method!="GET"&&method!="POST"){return map[string]any{"id":id,"status":400,"content_type":"application/json","body_b64":base64.StdEncoding.EncodeToString([]byte(`{"ok":false,"error":"invalid_remote_command"}`))}};var body io.Reader;if method=="POST"{body=strings.NewReader(fmt.Sprint(cmd["body"]))};u:=fmt.Sprintf("http://127.0.0.1:%d%s",s.Port,path);req,e:=http.NewRequest(method,u,body);if e!=nil{return map[string]any{"id":id,"status":502,"content_type":"application/json","body_b64":base64.StdEncoding.EncodeToString([]byte(`{"ok":false,"error":"request_build_failed"}`))}};if h,_:=cmd["headers"].(map[string]any);h!=nil{if v:=strings.TrimSpace(fmt.Sprint(h["content-type"]));v!=""{req.Header.Set("Content-Type",v)};if v:=strings.TrimSpace(fmt.Sprint(h["x-hermes-token"]));v!=""{req.Header.Set("X-Hermes-Token",v)}};cl:=&http.Client{Timeout:30*time.Second};resp,e:=cl.Do(req);if e!=nil{return map[string]any{"id":id,"status":502,"content_type":"application/json","body_b64":base64.StdEncoding.EncodeToString([]byte(fmt.Sprintf(`{"ok":false,"error":%q}`,e.Error())))}};defer resp.Body.Close();b,_:=io.ReadAll(io.LimitReader(resp.Body,1048576));ct:=resp.Header.Get("Content-Type");if ct==""{ct="application/octet-stream"};return map[string]any{"id":id,"status":resp.StatusCode,"content_type":ct,"body_b64":base64.StdEncoding.EncodeToString(b)}}
func (s *S)remoteLinkLoop(){time.Sleep(12*time.Second);for{base,topic,_:=s.remoteConfig();if base==""||topic==""{time.Sleep(60*time.Second);continue};_,_=s.remoteDevicePost(base,topic,"/remote/device/heartbeat",map[string]any{"device":"REDMI_5A_DJAEGER_WORK","release":runtimeRelease(s)});cmd,e:=s.remotePoll(base,topic);if e!=nil{time.Sleep(5*time.Second);continue};if cmd==nil{continue};result:=s.remoteExecute(cmd);_,_=s.remoteDevicePost(base,topic,"/remote/device/respond",result)}}
func (s *S)bridgeLoop(){time.Sleep(10*time.Second);for{if readenv(filepath.Join(s.Rel,"config","work.env"),"BRIDGE_ENABLED")!="1"{s.writeBridge("DISABLED","CONFIG_DISABLED",0);time.Sleep(5*time.Minute);continue};ep,key:=s.bridgeCred();if key==""{s.writeBridge("RETRYING","LOCAL_BRIDGE_KEY_FAILED",0);time.Sleep(60*time.Second);continue};if code,e:=s.pushRailway(key);e==nil{s.writeBridge("CONNECTED","RAILWAY_DIRECT_DATA_ONLY_0_AI",code);time.Sleep(5*time.Minute);continue};if exists(filepath.Join(s.Root,"state","bridge_worker_deployed")){if code,e:=s.pushCloud(ep,key);e==nil{s.writeBridge("CONNECTED","CLOUDFLARE_DATA_ONLY_0_AI",code);time.Sleep(5*time.Minute);continue}};code,readURL,e:=s.pushNtfy(key);if e==nil{s.writeBridge("CONNECTED","NTFY_DATA_ONLY_0_AI_READ="+readURL,code);time.Sleep(5*time.Minute);continue};s.writeBridge("RETRYING","ALL_RELAYS_FAILED_"+e.Error(),code);time.Sleep(60*time.Second)}}
func (s *S)bridgeStatus(w http.ResponseWriter,r *http.Request){js(w,s.bridgeInfo())}
func (s *S)autoUpdateInfo()map[string]any{v:=map[string]any{"state":"STARTING","automatic":true,"integrity":"SHA256","rollback":true};b,e:=os.ReadFile(filepath.Join(s.Root,"state","autoupdate.json"));if e==nil{_ = json.Unmarshal(b,&v)};return v}
func (s *S)autoUpdateStatus(w http.ResponseWriter,r *http.Request){js(w,s.autoUpdateInfo())}
func (s *S)auth(r *http.Request)bool{return s.Token!=""&&r.Header.Get("X-Hermes-Token")==s.Token}
func js(w http.ResponseWriter,v any){w.Header().Set("Content-Type","application/json");json.NewEncoder(w).Encode(v)}
func readfile(p string)string{b,_:=os.ReadFile(p);return string(b)}
func exists(p string)bool{_,e:=os.Stat(p);return e==nil}
func temp()float64{b,e:=os.ReadFile("/sys/class/power_supply/battery/temp");if e!=nil{return -1};v,_:=strconv.ParseFloat(strings.TrimSpace(string(b)),64);if v>200{v/=10};return v}
func mem()int64{b,_:=os.ReadFile("/proc/meminfo");for _,l:=range strings.Split(string(b),"\n"){if strings.HasPrefix(l,"MemAvailable:"){f:=strings.Fields(l);if len(f)>1{n,_:=strconv.ParseInt(f[1],10,64);return n/1024}}};return -1}
func iface(n string)(string,string){i,e:=net.InterfaceByName(n);if e!=nil{return"DOWN",""};a,_:=i.Addrs();ip:="";for _,x:=range a{if y,ok:=x.(*net.IPNet);ok&&y.IP.To4()!=nil{ip=y.IP.String()}};if ip==""{return"DOWN",""};return"UP",ip}
func (s *S)status(w http.ResponseWriter,r *http.Request){ts,ip:=iface("rndis0");cur:=runtimeRelease(s);configured:=strings.TrimSpace(readfile(filepath.Join(s.Root,"current_release")));if cur==""{cur="UNKNOWN"};bi:=s.bridgeInfo();bst,_:=bi["state"].(string);if bst==""{bst="STARTING"};au:=s.autoUpdateInfo();aus,_:=au["state"].(string);if aus==""{aus="STARTING"};js(w,map[string]any{"service":"HERMES_WORK","control_center":"v2.4.0","release":cur,"configured_release":configured,"auto_update_state":aus,"auto_update":au,"temperature_c":temp(),"mem_available_mb":mem(),"tether_state":ts,"tether_ip":ip,"worker_paused":exists(filepath.Join(s.Root,"state","worker_paused")),"safe_mode":exists(filepath.Join(s.Root,"state","safe_mode")),"bridge_enabled":readenv(filepath.Join(s.Rel,"config","work.env"),"BRIDGE_ENABLED")=="1","bridge_state":bst,"bridge_last_sync":bi["last_sync"],"bridge_mode":"DATA_ONLY","bridge_ai_used":false,"bridge_neurons_used":0,"research_total":s.researchTotal(),"last_research":strings.TrimSpace(readfile(filepath.Join(s.Root,"state","last_research"))),"research_engine":"SUGGEST_MULTI_V2","components":map[string]string{"collector":"READY_V2","dedup":"READY_V1","categorizer":"READY_V1","trend_scoring":"READY_V2","opportunity_engine":"READY_V1","reasoning":"DEFERRED","content_planner":"READY_V3","script_prep":"READY_V1","script_engine":"READY_V1","production_pack":"READY_V1","handoff":"READY_V1","production_desk":"READY_V1","auto_studio":"READY_V1","publication":"READY_V1","channel_connector":"READY_V1","feedback":"READY_V1","knowledge":"READY_FOUNDATION","scheduler":"READY_V2","bridge":bst,"auto_updater":aus}})}
func (s *S)action(w http.ResponseWriter,r *http.Request){if !s.auth(r){http.Error(w,"unauthorized",401);return};var q struct{Action string `json:"action"`};json.NewDecoder(r.Body).Decode(&q);st:=filepath.Join(s.Root,"state");switch q.Action{case"pause":os.WriteFile(filepath.Join(st,"worker_paused"),[]byte(time.Now().Format(time.RFC3339)),0600);case"resume":os.Remove(filepath.Join(st,"worker_paused"));os.Remove(filepath.Join(st,"safe_mode"));case"safe_mode":os.WriteFile(filepath.Join(st,"safe_mode"),[]byte("safe_mode"),0600);os.WriteFile(filepath.Join(st,"worker_paused"),[]byte("safe_mode"),0600);case"backup":os.MkdirAll(filepath.Join(s.Root,"backups"),0700);os.WriteFile(filepath.Join(s.Root,"backups","state-"+time.Now().Format("20060102-150405")+".txt"),[]byte("release="+strings.TrimSpace(readfile(filepath.Join(s.Root,"current_release")))+"\n"),0600);case"rollback":p:=strings.TrimSpace(readfile(filepath.Join(s.Root,"previous_release")));if p==""{http.Error(w,"no previous release",409);return};os.WriteFile(filepath.Join(s.Root,"current_release"),[]byte(p+"\n"),0600);default:http.Error(w,"unknown action",400);return};js(w,map[string]any{"ok":true,"action":q.Action})}
func tail(p string,n int)string{b,_:=os.ReadFile(p);a:=strings.Split(string(b),"\n");if len(a)>n{a=a[len(a)-n:]};return strings.Join(a,"\n")}
func (s *S)diag(w http.ResponseWriter,r *http.Request){if !s.auth(r){http.Error(w,"unauthorized",401);return};ts,ip:=iface("rndis0");js(w,map[string]any{"time":time.Now().Format(time.RFC3339),"temp_c":temp(),"mem_mb":mem(),"rndis":ts,"rndis_ip":ip,"release":strings.TrimSpace(readfile(filepath.Join(s.Root,"current_release"))),"bootstrap_log_tail":tail(filepath.Join(s.Root,"logs","hermesd.log"),60),"work_log_tail":tail(filepath.Join(s.Root,"logs","workd.log"),60)})}
func copyf(src,dst string)error{in,e:=os.Open(src);if e!=nil{return e};defer in.Close();if e=os.MkdirAll(filepath.Dir(dst),0700);e!=nil{return e};out,e:=os.OpenFile(dst,os.O_CREATE|os.O_TRUNC|os.O_WRONLY,0700);if e!=nil{return e};_,e=io.Copy(out,in);ce:=out.Close();if e!=nil{return e};return ce}
func unzipPayload(bundle,stage string)error{z,e:=zip.OpenReader(bundle);if e!=nil{return e};defer z.Close();for _,f:=range z.File{n:=filepath.Clean(f.Name);if n=="manifest.json"||strings.HasPrefix(n,"payload/"){if strings.Contains(n,".."){return fmt.Errorf("unsafe path")};dst:=filepath.Join(stage,n);if f.FileInfo().IsDir(){os.MkdirAll(dst,0700);continue};rc,e:=f.Open();if e!=nil{return e};if e=os.MkdirAll(filepath.Dir(dst),0700);e!=nil{rc.Close();return e};o,e:=os.OpenFile(dst,os.O_CREATE|os.O_TRUNC|os.O_WRONLY,0700);if e!=nil{rc.Close();return e};_,e=io.Copy(o,rc);o.Close();rc.Close();if e!=nil{return e}}};return nil}
func androidHTTPClient()*http.Client{pool:=x509.NewCertPool();loaded:=false;for _,dir:=range []string{"/system/etc/security/cacerts","/apex/com.android.conscrypt/cacerts"}{ents,e:=os.ReadDir(dir);if e!=nil{continue};for _,ent:=range ents{if ent.IsDir(){continue};b,e:=os.ReadFile(filepath.Join(dir,ent.Name()));if e==nil&&pool.AppendCertsFromPEM(b){loaded=true}}};tr:=&http.Transport{};if loaded{tr.TLSClientConfig=&tls.Config{RootCAs:pool,MinVersion:tls.VersionTLS12}};return &http.Client{Timeout:45*time.Second,Transport:tr}}
func (s *S)update(w http.ResponseWriter,r *http.Request){if !s.auth(r){http.Error(w,"unauthorized",401);return};ch:=readenv(filepath.Join(s.Rel,"config","work.env"),"UPDATE_CHANNEL_URL");if ch==""{http.Error(w,"channel missing",500);return};cl:=androidHTTPClient();resp,e:=cl.Get(ch);if e!=nil{http.Error(w,e.Error(),502);return};defer resp.Body.Close();if resp.StatusCode!=200{http.Error(w,"channel http "+resp.Status,502);return};var m struct{Version,Bundle,Sha256 string};if e=json.NewDecoder(io.LimitReader(resp.Body,65536)).Decode(&m);e!=nil||m.Version==""||m.Bundle==""||len(m.Sha256)!=64{http.Error(w,"invalid channel",502);return};base:=ch[:strings.LastIndex(ch,"/")+1];tmp:=filepath.Join(s.Root,"updates",m.Version+".zip.tmp");os.MkdirAll(filepath.Dir(tmp),0700);br,e:=cl.Get(base+m.Bundle);if e!=nil{http.Error(w,e.Error(),502);return};defer br.Body.Close();if br.StatusCode!=200{http.Error(w,"bundle http "+br.Status,502);return};o,e:=os.Create(tmp);if e!=nil{http.Error(w,e.Error(),500);return};h:=sha256.New();_,e=io.Copy(io.MultiWriter(o,h),io.LimitReader(br.Body,32<<20));o.Close();if e!=nil{os.Remove(tmp);http.Error(w,e.Error(),500);return};got:=hex.EncodeToString(h.Sum(nil));if !strings.EqualFold(got,m.Sha256){os.Remove(tmp);http.Error(w,"sha256 mismatch",409);return};stage:=filepath.Join(s.Root,"releases",".stage-"+m.Version);os.RemoveAll(stage);os.MkdirAll(stage,0700);if e=unzipPayload(tmp,stage);e!=nil{os.RemoveAll(stage);http.Error(w,e.Error(),500);return};if !exists(filepath.Join(stage,"manifest.json"))||!exists(filepath.Join(stage,"payload","bin","workd"))||!exists(filepath.Join(stage,"payload","worker","tick.sh")){os.RemoveAll(stage);http.Error(w,"invalid bundle structure",409);return};dest:=filepath.Join(s.Root,"releases",m.Version);next:=dest+".new";os.RemoveAll(next);os.MkdirAll(next,0700);if e=copyf(filepath.Join(stage,"manifest.json"),filepath.Join(next,"manifest.json"));e!=nil{http.Error(w,e.Error(),500);return};filepath.Walk(filepath.Join(stage,"payload"),func(p string,i os.FileInfo,er error)error{if er!=nil||i.IsDir(){return er};rel,_:=filepath.Rel(filepath.Join(stage,"payload"),p);return copyf(p,filepath.Join(next,rel))});os.Chmod(filepath.Join(next,"bin","workd"),0755);os.Chmod(filepath.Join(next,"worker","tick.sh"),0755);cur:=strings.TrimSpace(readfile(filepath.Join(s.Root,"current_release")));if cur!=""&&cur!=m.Version{os.WriteFile(filepath.Join(s.Root,"previous_release"),[]byte(cur+"\n"),0600)};os.RemoveAll(dest);if e=os.Rename(next,dest);e!=nil{http.Error(w,e.Error(),500);return};os.WriteFile(filepath.Join(s.Root,"current_release"),[]byte(m.Version+"\n"),0600);os.RemoveAll(stage);os.Rename(tmp,filepath.Join(s.Root,"updates",m.Version+".zip"));js(w,map[string]any{"ok":true,"state":"INSTALLED","version":m.Version,"sha256":got,"restart":false,"handoff":"scheduled"});if f,er:=os.OpenFile(filepath.Join(s.Root,"logs","handoff.log"),os.O_CREATE|os.O_APPEND|os.O_WRONLY,0600);er==nil{fmt.Fprintln(f,time.Now().Format(time.RFC3339),"handoff",m.Version);f.Close()};go func(){time.Sleep(700*time.Millisecond);p:=filepath.Join(dest,"worker","handoff.sh");if exists(p){os.StartProcess("/system/bin/sh",[]string{"sh",p,s.Root,m.Version,cur},&os.ProcAttr{Files:[]*os.File{nil,nil,nil}})}}()}
func guard(s *S)(bool,string){ts,_:=iface("rndis0");if ts!="UP"{return false,"TETHER_DOWN"};if temp()>=44{return false,"THERMAL_GUARD"};if mem()<220{return false,"LOW_RAM"};if exists(filepath.Join(s.Root,"state","safe_mode"))||exists(filepath.Join(s.Root,"state","worker_paused")){return false,"PAUSED"};return true,"READY"}
func classify(t string)string{x:=strings.ToLower(t);cats:=map[string][]string{"colors":{"color","colour","warna","red","blue","green"},"numbers":{"number","count","angka","counting"},"alphabet":{"alphabet","abc","letter","phonics"},"animals":{"animal","cat","dog","dinosaur","hewan"},"shapes":{"shape","circle","square","bentuk"},"habits":{"habit","brush","wash","sharing","kebiasaan"},"english":{"english","vocabulary","word"},"stories":{"story","stories","tale","cerita"}};for k,ws:=range cats{for _,w:=range ws{if strings.Contains(x,w){return k}}};return "other"}
func (s *S)collect(w http.ResponseWriter,r *http.Request){if !s.auth(r){http.Error(w,"unauthorized",401);return};ok,reason:=guard(s);if !ok{js(w,map[string]any{"ok":false,"state":"GUARDED","reason":reason});return};var q struct{Items []struct{Title string `json:"title"`;Source string `json:"source"`;URL string `json:"url"`;Score float64 `json:"score"`} `json:"items"`};if e:=json.NewDecoder(io.LimitReader(r.Body,1<<20)).Decode(&q);e!=nil{http.Error(w,"invalid json",400);return};dir:=filepath.Join(s.Root,"data","database");os.MkdirAll(dir,0700);p:=filepath.Join(dir,"research.jsonl");seen:=map[string]bool{};if b,e:=os.ReadFile(p);e==nil{for _,l:=range strings.Split(string(b),"\n"){var z map[string]any;if json.Unmarshal([]byte(l),&z)==nil{if u,_:=z["url"].(string);u!=""{seen[u]=true}}}};f,e:=os.OpenFile(p,os.O_CREATE|os.O_APPEND|os.O_WRONLY,0600);if e!=nil{http.Error(w,e.Error(),500);return};defer f.Close();added,dup:=0,0;enc:=json.NewEncoder(f);for _,it:=range q.Items{if it.URL!=""&&seen[it.URL]{dup++;continue};cat:=classify(it.Title);score:=it.Score;if score==0{score=50};enc.Encode(map[string]any{"ts":time.Now().Format(time.RFC3339),"title":it.Title,"source":it.Source,"url":it.URL,"category":cat,"score":score});if it.URL!=""{seen[it.URL]=true};added++};os.WriteFile(filepath.Join(s.Root,"state","last_research"),[]byte(time.Now().Format(time.RFC3339)),0600);js(w,map[string]any{"ok":true,"state":"COLLECTED","received":len(q.Items),"added":added,"duplicates":dup})}
func (s *S)research(w http.ResponseWriter,r *http.Request){b,_:=os.ReadFile(filepath.Join(s.Root,"data","database","research.jsonl"));lines:=strings.Split(strings.TrimSpace(string(b)),"\n");cats:=map[string]int{};total:=0;for _,l:=range lines{if strings.TrimSpace(l)==""{continue};var z map[string]any;if json.Unmarshal([]byte(l),&z)==nil{total++;if x,ok:=z["category"].(string);ok{cats[x]++}}};js(w,map[string]any{"total_items":total,"categories":cats,"last_research":strings.TrimSpace(readfile(filepath.Join(s.Root,"state","last_research")))})}
type AutoItem struct{Title,Source,URL string;Score float64}
func (s *S)storeAuto(items []AutoItem)(int,int,error){dir:=filepath.Join(s.Root,"data","database");os.MkdirAll(dir,0700);p:=filepath.Join(dir,"research.jsonl");seen:=map[string]bool{};if b,e:=os.ReadFile(p);e==nil{for _,l:=range strings.Split(string(b),"\n"){var z map[string]any;if json.Unmarshal([]byte(l),&z)==nil{if u,_:=z["url"].(string);u!=""{seen[u]=true}}}};f,e:=os.OpenFile(p,os.O_CREATE|os.O_APPEND|os.O_WRONLY,0600);if e!=nil{return 0,0,e};defer f.Close();enc:=json.NewEncoder(f);added,dup:=0,0;for _,it:=range items{if it.URL!=""&&seen[it.URL]{dup++;continue};enc.Encode(map[string]any{"ts":time.Now().Format(time.RFC3339),"title":it.Title,"source":it.Source,"url":it.URL,"category":classify(it.Title),"score":it.Score});if it.URL!=""{seen[it.URL]=true};added++};os.WriteFile(filepath.Join(s.Root,"state","last_research"),[]byte(time.Now().Format(time.RFC3339)),0600);return added,dup,nil}
func suggest(q,ds string)([]string,error){u:="https://suggestqueries.google.com/complete/search?client=firefox";if ds!=""{u+="&ds="+url.QueryEscape(ds)};u+="&q="+url.QueryEscape(q);cl:=androidHTTPClient();cl.Timeout=12*time.Second;r,e:=cl.Get(u);if e!=nil{return nil,e};defer r.Body.Close();if r.StatusCode!=200{return nil,fmt.Errorf("suggest http %s",r.Status)};var v []any;if e=json.NewDecoder(io.LimitReader(r.Body,1<<20)).Decode(&v);e!=nil{return nil,e};if len(v)<2{return nil,fmt.Errorf("bad suggest response")};a,ok:=v[1].([]any);if !ok{return nil,fmt.Errorf("bad suggestions")};var out []string;for _,x:=range a{if s,ok:=x.(string);ok&&strings.TrimSpace(s)!=""{out=append(out,strings.TrimSpace(s))}};return out,nil}
func ytSuggest(q string)([]string,error){return suggest(q,"yt")}
func webSuggest(q string)([]string,error){return suggest(q,"")}
func (s *S)autoResearch()(map[string]any,error){
ok,reason:=guard(s);if !ok{return map[string]any{"ok":false,"state":"GUARDED","reason":reason},nil}
seeds:=[]string{
"learn colors for kids","learn numbers for kids","alphabet for kids","phonics for kids","animals for kids","shapes for kids","good habits for kids","english words for kids","short stories for kids",
"belajar warna anak","belajar angka anak","alfabet anak","fonik anak","nama hewan anak","bentuk untuk anak","kebiasaan baik anak","bahasa inggris anak","cerita pendek anak",
"preschool learning","toddler learning","kindergarten learning","early learning activities","kids educational video"}
type aggRow struct{Count,Best int;Sources map[string]bool}
agg:=map[string]*aggRow{};checked:=0;errors:=0
collect:=func(source string,ss []string){for i,t:=range ss{t=strings.TrimSpace(t);if t==""{continue};k:=strings.ToLower(t);z:=agg[k];if z==nil{z=&aggRow{Best:999,Sources:map[string]bool{}};agg[k]=z};z.Count++;if i+1<z.Best{z.Best=i+1};z.Sources[source]=true}}
for _,q:=range seeds{
 if ss,e:=ytSuggest(q);e==nil{checked++;collect("youtube",ss)}else{errors++}
 time.Sleep(80*time.Millisecond)
 if ss,e:=webSuggest(q);e==nil{checked++;collect("web",ss)}else{errors++}
 time.Sleep(80*time.Millisecond)
}
var items []AutoItem
for title,z:=range agg{
 srcCount:=len(z.Sources)
 sc:=42+float64(z.Count*7)+float64(srcCount*10)+float64(12-z.Best)
 if sc>100{sc=100};if sc<1{sc=1}
 pretty:=title
 items=append(items,AutoItem{Title:pretty,Source:"suggest_multi",URL:"search:"+url.QueryEscape(pretty),Score:sc})
}
sort.Slice(items,func(i,j int)bool{return items[i].Score>items[j].Score})
if len(items)>200{items=items[:200]}
added,dup,e:=s.storeAuto(items);if e!=nil{return nil,e}
res:=map[string]any{"ok":true,"state":"RESEARCHED","sources_checked":checked,"source_errors":errors,"found":len(items),"added":added,"duplicates":dup,"engine":"SUGGEST_MULTI_V2","zero_neuron":true}
b,_:=json.Marshal(res);os.WriteFile(filepath.Join(s.Root,"state","last_research_result.json"),b,0600);s.writeDailyBrief()
return res,nil
}
func (s *S)runResearch(w http.ResponseWriter,r *http.Request){if !s.auth(r){http.Error(w,"unauthorized",401);return};res,e:=s.autoResearch();if e!=nil{http.Error(w,e.Error(),502);return};js(w,res)}
func minutesOfDay(hm string)int{p:=strings.Split(hm,":");if len(p)!=2{return 480};h,_:=strconv.Atoi(p[0]);m,_:=strconv.Atoi(p[1]);if h<0||h>23||m<0||m>59{return 480};return h*60+m}
func (s *S)schedulerLoop(){
 time.Sleep(20*time.Second)
 // First boot bootstrap: if database is empty, research immediately once guard is ready.
 if s.researchTotal()==0{
   if ok,_:=guard(s);ok{_,_ = s.autoResearch()}
 } else {
   s.writeDailyBrief()
 }
 studioTick:=0
 for{
   studioTick++;if studioTick>=5{s.pollStudioResult();studioTick=0}
   cfg:=readenv(filepath.Join(s.Rel,"config","work.env"),"RESEARCH_SCHEDULE");if cfg==""{cfg="08:00"}
   now:=time.Now();day:=now.Format("2006-01-02");last:=strings.TrimSpace(readfile(filepath.Join(s.Root,"state","last_auto_research_date")))
   due:=now.Hour()*60+now.Minute()>=minutesOfDay(cfg)
   if due&&last!=day{
     if ok,_:=guard(s);ok{if _,e:=s.autoResearch();e==nil{os.WriteFile(filepath.Join(s.Root,"state","last_auto_research_date"),[]byte(day),0600)}}
   }
   time.Sleep(60*time.Second)
 }
}

type Opportunity struct{
 Rank int `json:"rank"`
 Title string `json:"title"`
 Category string `json:"category"`
 Score float64 `json:"score"`
 Demand string `json:"demand"`
 TargetAge string `json:"target_age"`
 Format string `json:"format"`
 Why string `json:"why"`
 Keywords []string `json:"keywords"`
 SourceURL string `json:"source_url"`
}
func wordsFor(s string)[]string{
 stop:=map[string]bool{"for":true,"kids":true,"kid":true,"anak":true,"untuk":true,"the":true,"a":true,"an":true,"and":true,"with":true,"learn":true,"learning":true,"belajar":true,"video":true,"videos":true,"of":true,"to":true,"in":true}
 clean:=strings.ToLower(s);rep:=strings.NewReplacer("-"," ","_"," ","/"," ",","," ","."," ",":"," ","("," ",")"," ","?"," ","!"," ")
 clean=rep.Replace(clean);seen:=map[string]bool{};out:=[]string{}
 for _,w:=range strings.Fields(clean){if len(w)<3||stop[w]||seen[w]{continue};seen[w]=true;out=append(out,w);if len(out)>=6{break}}
 return out
}
func normTopic(s string)string{
 ws:=wordsFor(s);sort.Strings(ws);return strings.Join(ws," ")
}
func demandBand(sc float64)string{if sc>=82{return"HIGH"};if sc>=65{return"MEDIUM"};return"DISCOVERY"}
func formatFor(cat,title string)string{
 x:=strings.ToLower(title)
 if strings.Contains(x,"song")||strings.Contains(x,"lagu"){return"SONG/CHANT"}
 if strings.Contains(x,"story")||strings.Contains(x,"cerita"){return"SHORT STORY"}
 if cat=="alphabet"||strings.Contains(x,"phonics")||strings.Contains(x,"fonik"){return"REPEAT-AFTER-ME"}
 if cat=="numbers"||cat=="colors"||cat=="shapes"{return"QUIZ + REPETITION"}
 if cat=="animals"{return"NAME + SOUND + GUESS"}
 if cat=="habits"{return"MINI STORY + MODELING"}
 return"SHORT EXPLAINER + REPETITION"
}
func whyFor(sc float64,cat string)string{
 band:=demandBand(sc)
 if band=="HIGH"{return"High demand signal from search suggestions; prioritized for today's queue."}
 if band=="MEDIUM"{return"Repeated search interest with usable educational intent; good candidate for testing."}
 if cat!="other"{return"Relevant educational query in a target category; keep as a discovery test."}
 return"Discovery query kept for exploration; lower priority than core learning categories."
}
func (s *S)opportunityList(limit int)[]Opportunity{
 b,_:=os.ReadFile(filepath.Join(s.Root,"data","database","research.jsonl"))
 type raw struct{Title,Category,URL string;Score float64}
 rows:=[]raw{};seen:=map[string]bool{}
 for _,l:=range strings.Split(strings.TrimSpace(string(b)),"\n"){
  if strings.TrimSpace(l)==""{continue};var z map[string]any;if json.Unmarshal([]byte(l),&z)!=nil{continue}
  title,_:=z["title"].(string);cat,_:=z["category"].(string);u,_:=z["url"].(string);sc,_:=z["score"].(float64)
  if title==""{continue};if cat==""{cat=classify(title)}
  nk:=normTopic(title);if nk==""{nk=strings.ToLower(strings.TrimSpace(title))};if seen[nk]{continue};seen[nk]=true
  // Small deterministic quality adjustment: reward concise, clearly educational queries; penalize generic "other".
  adj:=sc
  wc:=len(strings.Fields(title));if wc>=3&&wc<=9{adj+=3};if cat=="other"{adj-=8};adj+=s.performanceAdjustment(cat);if adj>100{adj=100};if adj<1{adj=1}
  rows=append(rows,raw{Title:title,Category:cat,URL:u,Score:adj})
 }
 sort.Slice(rows,func(i,j int)bool{if rows[i].Score==rows[j].Score{return rows[i].Title<rows[j].Title};return rows[i].Score>rows[j].Score})
 if limit<=0{limit=10}
 picked:=[]raw{};catCount:=map[string]int{}
 // First pass keeps the daily queue diverse.
 for _,r:=range rows{if catCount[r.Category]>=2{continue};picked=append(picked,r);catCount[r.Category]++;if len(picked)>=limit{break}}
 // Second pass fills remaining slots strictly by score.
 if len(picked)<limit{
  used:=map[string]bool{};for _,p:=range picked{used[p.Title]=true}
  for _,r:=range rows{if used[r.Title]{continue};picked=append(picked,r);if len(picked)>=limit{break}}
 }
 out:=[]Opportunity{}
 for i,r:=range picked{out=append(out,Opportunity{Rank:i+1,Title:r.Title,Category:r.Category,Score:r.Score,Demand:demandBand(r.Score),TargetAge:"3-6",Format:formatFor(r.Category,r.Title),Why:whyFor(r.Score,r.Category),Keywords:wordsFor(r.Title),SourceURL:r.URL})}
 return out
}
func (s *S)writeDailyBrief()map[string]any{
 ideas:=s.opportunityList(10);cats:=map[string]int{};high:=0
 for _,it:=range ideas{cats[it.Category]++;if it.Demand=="HIGH"{high++}}
 top:="";if len(ideas)>0{top=ideas[0].Title}
 v:=map[string]any{"state":"READY","engine":"OPPORTUNITY_V1","generated_at":time.Now().Format(time.RFC3339),"target_age":"3-6","research_total":s.researchTotal(),"ideas_ready":len(ideas),"high_demand":high,"categories":cats,"top_opportunity":top,"ideas":ideas,"ai_used":false,"neurons_used":0}
 b,_:=json.MarshalIndent(v,"","  ");os.MkdirAll(filepath.Join(s.Root,"data","planner"),0700);_ = os.WriteFile(filepath.Join(s.Root,"data","planner","daily_brief.json"),b,0600);_ = os.WriteFile(filepath.Join(s.Root,"state","last_daily_brief"),[]byte(time.Now().Format(time.RFC3339)),0600)
 go func(){s.syncPlanner();s.ensureScriptPreps();s.ensureScripts();s.ensureProductionPacks()}()
 return v
}
func (s *S)dailyBriefInfo()map[string]any{
 p:=filepath.Join(s.Root,"data","planner","daily_brief.json");var v map[string]any
 if b,e:=os.ReadFile(p);e==nil&&json.Unmarshal(b,&v)==nil{return v}
 if s.researchTotal()>0{return s.writeDailyBrief()}
 return map[string]any{"state":"WAITING_RESEARCH","engine":"OPPORTUNITY_V1","ideas_ready":0,"ai_used":false,"neurons_used":0}
}
func (s *S)opportunities(w http.ResponseWriter,r *http.Request){js(w,s.dailyBriefInfo())}
type PlanItem struct{
 ID string `json:"id"`
 Title string `json:"title"`
 Category string `json:"category"`
 Priority int `json:"priority"`
 Score float64 `json:"score"`
 Demand string `json:"demand"`
 TargetAge string `json:"target_age"`
 Format string `json:"format"`
 Keywords []string `json:"keywords"`
 Stage string `json:"stage"`
 CreatedAt string `json:"created_at"`
 UpdatedAt string `json:"updated_at"`
}
func planID(title string)string{h:=sha256.Sum256([]byte(strings.ToLower(strings.TrimSpace(title))));return hex.EncodeToString(h[:6])}
func (s *S)plannerPath()string{return filepath.Join(s.Root,"data","planner","queue.json")}
func (s *S)loadPlan()[]PlanItem{b,e:=os.ReadFile(s.plannerPath());if e!=nil{return nil};var a []PlanItem;if json.Unmarshal(b,&a)!=nil{return nil};return a}
func (s *S)savePlan(a []PlanItem)error{os.MkdirAll(filepath.Dir(s.plannerPath()),0700);b,_:=json.MarshalIndent(a,"","  ");tmp:=s.plannerPath()+".tmp";if e:=os.WriteFile(tmp,b,0600);e!=nil{return e};return os.Rename(tmp,s.plannerPath())}
func (s *S)syncPlanner()[]PlanItem{
 ideas:=s.opportunityList(10);a:=s.loadPlan();byID:=map[string]int{};for i:=range a{byID[a[i].ID]=i}
 now:=time.Now().Format(time.RFC3339)
 for _,o:=range ideas{
  id:=planID(o.Title)
  if _,ok:=byID[id];ok{continue}
  a=append(a,PlanItem{ID:id,Title:o.Title,Category:o.Category,Priority:o.Rank,Score:o.Score,Demand:o.Demand,TargetAge:o.TargetAge,Format:o.Format,Keywords:o.Keywords,Stage:"IDEA_READY",CreatedAt:now,UpdatedAt:now})
 }
 // Keep active queue compact: sort unfinished first, then priority/score. Preserve published history but cap file.
 sort.SliceStable(a,func(i,j int)bool{
  ai:=a[i].Stage!="PUBLISHED";aj:=a[j].Stage!="PUBLISHED";if ai!=aj{return ai}
  if a[i].Priority!=a[j].Priority{return a[i].Priority<a[j].Priority}
  return a[i].Score>a[j].Score
 })
 if len(a)>100{a=a[:100]}
 _=s.savePlan(a);return a
}
func planCounts(a []PlanItem)map[string]int{m:=map[string]int{"IDEA_READY":0,"SCRIPT_PREP_READY":0,"SCRIPT_READY":0,"PRODUCTION_READY":0,"PRODUCTION":0,"UPLOAD_READY":0,"PUBLISHED":0,"HOLD":0};for _,x:=range a{m[x.Stage]++};return m}
func (s *S)plannerInfo()map[string]any{
 a:=s.syncPlanner();counts:=planCounts(a);active:=[]PlanItem{}
 for _,x:=range a{if x.Stage!="PUBLISHED"{active=append(active,x)};if len(active)>=20{break}}
 next:="";nextID:="";for _,x:=range a{if x.Stage=="SCRIPT_PREP_READY"{next=x.Title;nextID=x.ID;break}};if next==""{for _,x:=range a{if x.Stage=="IDEA_READY"{next=x.Title;nextID=x.ID;break}}}
 prod:="";prodID:="";for _,x:=range a{if x.Stage=="PRODUCTION_READY"{prod=x.Title;prodID=x.ID;break}};return map[string]any{"state":"READY","engine":"PLANNER_V1","updated_at":time.Now().Format(time.RFC3339),"queue_total":len(a),"active":len(active),"counts":counts,"next_for_script":next,"next_for_script_id":nextID,"next_for_production":prod,"next_for_production_id":prodID,"items":active,"ai_used":false,"neurons_used":0}
}
func validStage(x string)bool{switch x{case"IDEA_READY","SCRIPT_PREP_READY","SCRIPT_READY","PRODUCTION_READY","PRODUCTION","UPLOAD_READY","PUBLISHED","HOLD":return true};return false}

type ScriptPrepScene struct{
 Number int `json:"number"`
 DurationSec int `json:"duration_sec"`
 Purpose string `json:"purpose"`
 NarrationGuide string `json:"narration_guide"`
 VisualGuide string `json:"visual_guide"`
 OnScreenText string `json:"on_screen_text"`
}
type ScriptPrep struct{
 State string `json:"state"`
 Engine string `json:"engine"`
 PlannerID string `json:"planner_id"`
 Topic string `json:"topic"`
 Category string `json:"category"`
 TargetAge string `json:"target_age"`
 DurationSec int `json:"duration_sec"`
 LearningObjective string `json:"learning_objective"`
 Hook string `json:"hook"`
 Format string `json:"format"`
 Keywords []string `json:"keywords"`
 SceneCount int `json:"scene_count"`
 Scenes []ScriptPrepScene `json:"scenes"`
 CTA string `json:"cta"`
 QualityRules []string `json:"quality_rules"`
 GeneratedAt string `json:"generated_at"`
 AIUsed bool `json:"ai_used"`
 NeuronsUsed int `json:"neurons_used"`
}
func objectiveFor(cat,topic string)string{
 switch cat{
 case"numbers":return"Help children recognize and repeat basic numbers using simple visual counting."
 case"colors":return"Help children recognize and name colors through clear object examples."
 case"alphabet":return"Help children recognize letters and connect them with simple sounds or words."
 case"animals":return"Help children identify animals by name, appearance, and familiar sounds."
 case"shapes":return"Help children recognize basic shapes in simple everyday objects."
 case"habits":return"Model one positive habit with a clear cause-and-effect example."
 case"english":return"Teach a small set of easy English words through repetition and visual association."
 case"stories":return"Deliver one simple lesson through a short, easy-to-follow story."
 default:return"Teach the topic '"+topic+"' with one clear learning goal and repeated visual examples."
 }
}
func hookFor(cat string)string{
 switch cat{
 case"numbers":return"Start with a quick counting challenge using bright objects."
 case"colors":return"Open with a mystery object and ask the child to guess its color."
 case"alphabet":return"Open with one big letter and a playful sound question."
 case"animals":return"Open with an animal sound and ask who makes that sound."
 case"shapes":return"Open by finding a hidden shape in a familiar object."
 case"habits":return"Open with a simple everyday problem the character can solve with a good habit."
 case"english":return"Open with a familiar object and ask for its English name."
 case"stories":return"Open with a simple character problem that can be solved within one minute."
 default:return"Open with one simple question that directly introduces the topic."
 }
}
func scenesFor(cat,topic string)[]ScriptPrepScene{
 base:=[]ScriptPrepScene{
  {Number:1,DurationSec:5,Purpose:"HOOK",NarrationGuide:hookFor(cat),VisualGuide:"One uncluttered establishing shot with the recurring character and one large focal object.",OnScreenText:""},
  {Number:2,DurationSec:8,Purpose:"INTRODUCE",NarrationGuide:"Name the learning topic in one short sentence and invite the child to join.",VisualGuide:"Show the key concept large and centered; avoid extra background detail.",OnScreenText:topic},
  {Number:3,DurationSec:10,Purpose:"TEACH_1",NarrationGuide:"Give the first clear example, then repeat the key word or concept once.",VisualGuide:"Use one example at a time with obvious motion or pointing.",OnScreenText:""},
  {Number:4,DurationSec:10,Purpose:"TEACH_2",NarrationGuide:"Give a second example and connect it to the first using the same simple wording.",VisualGuide:"Use a different object or situation while keeping character and style consistent.",OnScreenText:""},
  {Number:5,DurationSec:10,Purpose:"INTERACTIVE_RECALL",NarrationGuide:"Ask one easy question, leave a short pause, then reveal the answer positively.",VisualGuide:"Present two or three large choices with clear spacing.",OnScreenText:"Your turn!"},
  {Number:6,DurationSec:7,Purpose:"RECAP_CTA",NarrationGuide:"Repeat the key lesson once and close with a gentle invitation to learn another topic.",VisualGuide:"Return to the recurring character with the learned examples visible together.",OnScreenText:"Great job!"}}
 return base
}
func (s *S)scriptPrepDir()string{return filepath.Join(s.Root,"data","scripts","prep")}
func (s *S)writeScriptPrep(it PlanItem)(ScriptPrep,error){
 p:=ScriptPrep{State:"READY_FOR_SCRIPT",Engine:"SCRIPT_PREP_V1",PlannerID:it.ID,Topic:it.Title,Category:it.Category,TargetAge:it.TargetAge,DurationSec:50,LearningObjective:objectiveFor(it.Category,it.Title),Hook:hookFor(it.Category),Format:it.Format,Keywords:it.Keywords,CTA:"End with encouragement, not pressure; keep the final line short.",QualityRules:[]string{"Use short sentences and familiar words.","One learning point at a time.","No rapid flashing or cluttered scenes.","Keep character appearance and naming consistent.","Leave brief pauses after questions for child response.","Do not make medical, safety, or factual claims beyond simple preschool learning."},GeneratedAt:time.Now().Format(time.RFC3339),AIUsed:false,NeuronsUsed:0}
 p.Scenes=scenesFor(it.Category,it.Title);p.SceneCount=len(p.Scenes)
 os.MkdirAll(s.scriptPrepDir(),0700);b,_:=json.MarshalIndent(p,"","  ");path:=filepath.Join(s.scriptPrepDir(),it.ID+".json");tmp:=path+".tmp"
 if e:=os.WriteFile(tmp,b,0600);e!=nil{return p,e};if e:=os.Rename(tmp,path);e!=nil{return p,e}
 return p,nil
}
func (s *S)ensureScriptPreps()[]ScriptPrep{
 a:=s.syncPlanner();out:=[]ScriptPrep{};changed:=false
 for i:=range a{
  stage:=a[i].Stage
  if stage=="HOLD"{continue}
  path:=filepath.Join(s.scriptPrepDir(),a[i].ID+".json");var p ScriptPrep
  if b,e:=os.ReadFile(path);e==nil&&json.Unmarshal(b,&p)==nil&&p.PlannerID==a[i].ID{
   out=append(out,p)
   if stage=="IDEA_READY"{a[i].Stage="SCRIPT_PREP_READY";a[i].UpdatedAt=time.Now().Format(time.RFC3339);changed=true}
   continue
  }
  if stage=="IDEA_READY"{
   if np,e:=s.writeScriptPrep(a[i]);e==nil{out=append(out,np);a[i].Stage="SCRIPT_PREP_READY";a[i].UpdatedAt=time.Now().Format(time.RFC3339);changed=true}
  }
 }
 if changed{_ = s.savePlan(a)}
 return out
}
func (s *S)scriptPrepInfo()map[string]any{
 p:=s.ensureScriptPreps();topic:="";pid:="";if len(p)>0{topic=p[0].Topic;pid=p[0].PlannerID}
 return map[string]any{"state":"READY","engine":"SCRIPT_PREP_V1","ready":len(p),"current_topic":topic,"current_planner_id":pid,"packages":p,"ai_used":false,"neurons_used":0}
}
func (s *S)scriptPrep(w http.ResponseWriter,r *http.Request){js(w,s.scriptPrepInfo())}


type FinalScene struct{
 Number int `json:"number"`
 DurationSec int `json:"duration_sec"`
 Purpose string `json:"purpose"`
 VoiceOver string `json:"voice_over"`
 VisualPrompt string `json:"visual_prompt"`
 OnScreenText string `json:"on_screen_text"`
 EditNote string `json:"edit_note"`
}
type ScriptPackage struct{
 State string `json:"state"`
 Engine string `json:"engine"`
 PlannerID string `json:"planner_id"`
 Topic string `json:"topic"`
 Category string `json:"category"`
 Language string `json:"language"`
 TargetAge string `json:"target_age"`
 DurationSec int `json:"duration_sec"`
 VideoTitle string `json:"video_title"`
 Description string `json:"description"`
 Hook string `json:"hook"`
 LearningObjective string `json:"learning_objective"`
 SceneCount int `json:"scene_count"`
 Scenes []FinalScene `json:"scenes"`
 ClosingLine string `json:"closing_line"`
 Keywords []string `json:"keywords"`
 Hashtags []string `json:"hashtags"`
 CharacterRule string `json:"character_rule"`
 GeneratedAt string `json:"generated_at"`
 AIUsed bool `json:"ai_used"`
 NeuronsUsed int `json:"neurons_used"`
}
func scriptLang(topic string)string{
 x:=strings.ToLower(topic)
 id:=[]string{"belajar","anak","angka","warna","hewan","bentuk","kebiasaan","bahasa","cerita","alfabet","fonik","nama "}
 for _,w:=range id{if strings.Contains(x,w){return"id"}}
 return"en"
}
func cleanTopic(t string)string{
 t=strings.TrimSpace(t);if t==""{return"topik belajar"}
 return t
}
func voLines(lang,cat,topic string)[]string{
 topic=cleanTopic(topic)
 if lang=="id"{
  switch cat{
  case"numbers":return []string{"Ayo, kita hitung bersama!","Hari ini kita belajar angka dengan cara yang mudah.","Lihat benda-benda ini. Kita hitung pelan-pelan bersama.","Sekarang coba sekali lagi. Perhatikan jumlah bendanya.","Giliran kamu! Hitung dulu... sudah? Hebat!","Bagus sekali! Kita sudah belajar angka bersama."}
  case"colors":return []string{"Wah, warna apa ini?","Hari ini kita belajar mengenal warna.","Lihat benda ini. Sebutkan warnanya bersama-sama.","Sekarang lihat contoh berikutnya. Warnanya sama atau berbeda?","Giliran kamu! Pilih warna yang benar... bagus!","Hebat! Sekarang kita sudah mengenal warna lebih baik."}
  case"alphabet":return []string{"Huruf apa ini? Ayo tebak!","Hari ini kita belajar huruf dan bunyinya.","Lihat huruf ini. Ucapkan perlahan bersama-sama.","Sekarang kita lihat contoh kata yang memakai huruf ini.","Giliran kamu! Sebutkan hurufnya... bagus sekali!","Hebat! Kita sudah belajar huruf hari ini."}
  case"animals":return []string{"Suara hewan apa itu?","Hari ini kita belajar mengenal hewan.","Lihat hewan ini. Ini namanya apa? Ayo ucapkan bersama.","Sekarang lihat hewan berikutnya dan perhatikan bentuknya.","Giliran kamu! Hewan mana yang benar?","Hebat! Kita sudah mengenal beberapa hewan."}
  case"shapes":return []string{"Bentuk apa yang kamu lihat?","Hari ini kita belajar mengenal bentuk.","Lihat bentuk ini. Perhatikan sisi dan tampilannya.","Sekarang kita cari bentuk yang sama pada benda lain.","Giliran kamu! Pilih bentuk yang cocok.","Bagus sekali! Kita sudah belajar bentuk bersama."}
  case"habits":return []string{"Apa yang sebaiknya kita lakukan?","Hari ini kita belajar satu kebiasaan baik.","Lihat contoh pertama. Kita lakukan dengan pelan dan benar.","Sekarang lihat apa yang terjadi setelah kebiasaan baik dilakukan.","Giliran kamu! Mana pilihan yang baik?","Hebat! Yuk, kita ingat kebiasaan baik ini setiap hari."}
  case"english":return []string{"Tahukah kamu nama benda ini dalam bahasa Inggris?","Hari ini kita belajar kata bahasa Inggris yang mudah.","Dengarkan katanya, lalu ucapkan bersama-sama.","Sekarang kita ulangi dengan contoh lain.","Giliran kamu! Coba ucapkan katanya.","Great job! Hebat, kita sudah belajar kata baru."}
  case"stories":return []string{"Ada cerita kecil untuk kamu hari ini.","Yuk, kita ikuti cerita singkat ini bersama.","Tokoh kita menemukan sebuah masalah kecil.","Ia mencoba cara yang baik untuk menyelesaikannya.","Menurut kamu, apa yang sebaiknya dilakukan?","Bagus sekali! Kita belajar satu hal baik dari cerita ini."}
  default:return []string{"Ayo belajar bersama!","Hari ini kita belajar tentang "+topic+".","Lihat contoh pertama dan perhatikan baik-baik.","Sekarang kita coba contoh berikutnya bersama.","Giliran kamu! Coba jawab dulu... bagus!","Hebat! Kita sudah belajar tentang "+topic+"."}
  }
 }
 switch cat{
 case"numbers":return []string{"Can you count with me?","Today we are learning numbers in a simple way.","Look at these objects. Let's count them slowly together.","Now let's try one more example. Watch the number of objects.","Your turn! Count first... ready? Great job!","Wonderful! We practiced numbers together."}
 case"colors":return []string{"What color is this?","Today we are learning colors.","Look at this object and say the color with me.","Now look at the next example. Is the color the same or different?","Your turn! Choose the correct color... great!","Wonderful! We learned more about colors today."}
 case"alphabet":return []string{"What letter is this?","Today we are learning a letter and its sound.","Look at the letter and say it slowly with me.","Now let's see a simple word that uses this letter.","Your turn! Say the letter... great job!","Wonderful! We practiced a letter today."}
 case"animals":return []string{"What animal makes that sound?","Today we are learning animal names.","Look at this animal. Say its name with me.","Now look at the next animal and notice what it looks like.","Your turn! Which animal is correct?","Wonderful! We learned some animal names."}
 case"shapes":return []string{"What shape can you see?","Today we are learning shapes.","Look closely at this shape.","Now let's find the same shape in another object.","Your turn! Pick the matching shape.","Great job! We practiced shapes together."}
 case"habits":return []string{"What is the good choice here?","Today we are learning one good habit.","Watch the first example and see the good choice.","Now notice what happens after the good habit is used.","Your turn! Which choice is better?","Great job! Let's remember this good habit."}
 case"english":return []string{"Do you know this word?","Today we are learning an easy English word.","Listen to the word, then say it with me.","Now let's repeat it with another example.","Your turn! Say the word out loud.","Great job! You learned a new word."}
 case"stories":return []string{"I have a tiny story for you.","Let's follow this short story together.","Our character finds a small problem.","They try a kind and simple way to solve it.","What do you think they should do?","Great job! We learned something helpful from the story."}
 default:return []string{"Let's learn together!","Today we are learning about "+topic+".","Look at the first example and watch carefully.","Now let's try another example together.","Your turn! Think first... great job!","Wonderful! We learned about "+topic+"."}
 }
}
func videoTitleFor(lang,topic string)string{
 t:=cleanTopic(topic)
 if lang=="id"{return strings.Title(t)+" | Belajar Seru untuk Anak"}
 return strings.Title(t)+" | Fun Learning for Kids"
}
func descriptionFor(lang,topic string)string{
 if lang=="id"{return"Video belajar singkat untuk anak usia 3–6 tahun tentang "+cleanTopic(topic)+". Materi dibuat sederhana, visual, dan mudah diikuti."}
 return"A short learning video for ages 3–6 about "+cleanTopic(topic)+". Simple language, clear visuals, and easy repetition."
}
func hashtagsFor(cat string)[]string{
 base:=[]string{"#KidsLearning","#Preschool","#EarlyLearning"}
 switch cat{
 case"numbers":return append(base,"#NumbersForKids")
 case"colors":return append(base,"#ColorsForKids")
 case"alphabet":return append(base,"#AlphabetForKids")
 case"animals":return append(base,"#AnimalsForKids")
 case"shapes":return append(base,"#ShapesForKids")
 case"habits":return append(base,"#GoodHabits")
 case"english":return append(base,"#EnglishForKids")
 case"stories":return append(base,"#KidsStories")
 }
 return base
}
func (s *S)scriptDir()string{return filepath.Join(s.Root,"data","scripts","final")}
func (s *S)writeFinalScript(p ScriptPrep)(ScriptPackage,error){
 lang:=scriptLang(p.Topic);lines:=voLines(lang,p.Category,p.Topic);sc:=[]FinalScene{}
 for i,g:=range p.Scenes{
  vo:="";if i<len(lines){vo=lines[i]}else{vo=g.NarrationGuide}
  visual:="Create a clean 2D preschool cartoon scene, bright but soft colors, large simple shapes, minimal background clutter. "+g.VisualGuide+" Keep the same recurring character design, clothes, proportions, and facial style across all scenes. No logos, no text except requested on-screen text."
  edit:="Use gentle cuts, readable pacing, no flashing, and leave a short response pause when the scene asks a question."
  sc=append(sc,FinalScene{Number:g.Number,DurationSec:g.DurationSec,Purpose:g.Purpose,VoiceOver:vo,VisualPrompt:visual,OnScreenText:g.OnScreenText,EditNote:edit})
 }
 close:="Great job! See you in the next lesson!";if lang=="id"{close="Hebat! Sampai jumpa di pelajaran berikutnya!"}
 out:=ScriptPackage{State:"SCRIPT_READY",Engine:"SCRIPT_ENGINE_V1",PlannerID:p.PlannerID,Topic:p.Topic,Category:p.Category,Language:lang,TargetAge:p.TargetAge,DurationSec:p.DurationSec,VideoTitle:videoTitleFor(lang,p.Topic),Description:descriptionFor(lang,p.Topic),Hook:lines[0],LearningObjective:p.LearningObjective,SceneCount:len(sc),Scenes:sc,ClosingLine:close,Keywords:p.Keywords,Hashtags:hashtagsFor(p.Category),CharacterRule:"Use one original recurring preschool-friendly character identity consistently across every scene; no imitation of copyrighted characters.",GeneratedAt:time.Now().Format(time.RFC3339),AIUsed:false,NeuronsUsed:0}
 os.MkdirAll(s.scriptDir(),0700);b,_:=json.MarshalIndent(out,"","  ");path:=filepath.Join(s.scriptDir(),p.PlannerID+".json");tmp:=path+".tmp"
 if e:=os.WriteFile(tmp,b,0600);e!=nil{return out,e};if e:=os.Rename(tmp,path);e!=nil{return out,e}
 return out,nil
}
func (s *S)ensureScripts()[]ScriptPackage{
 preps:=s.ensureScriptPreps();plans:=s.syncPlanner();idx:=map[string]int{};for i:=range plans{idx[plans[i].ID]=i}
 out:=[]ScriptPackage{};changed:=false
 for _,p:=range preps{
  i,ok:=idx[p.PlannerID];if !ok{continue};stage:=plans[i].Stage
  if stage=="HOLD"{continue}
  path:=filepath.Join(s.scriptDir(),p.PlannerID+".json");var sp ScriptPackage
  if b,e:=os.ReadFile(path);e==nil&&json.Unmarshal(b,&sp)==nil&&sp.PlannerID==p.PlannerID{
   out=append(out,sp)
   if stage=="SCRIPT_PREP_READY"{plans[i].Stage="SCRIPT_READY";plans[i].UpdatedAt=time.Now().Format(time.RFC3339);changed=true}
   continue
  }
  if stage=="SCRIPT_PREP_READY"{
   if ns,e:=s.writeFinalScript(p);e==nil{out=append(out,ns);plans[i].Stage="SCRIPT_READY";plans[i].UpdatedAt=time.Now().Format(time.RFC3339);changed=true}
  }
 }
 if changed{_ = s.savePlan(plans)}
 return out
}
func (s *S)scriptInfo()map[string]any{
 a:=s.ensureScripts();topic:="";pid:="";if len(a)>0{topic=a[0].Topic;pid=a[0].PlannerID}
 return map[string]any{"state":"READY","engine":"SCRIPT_ENGINE_V1","ready":len(a),"current_topic":topic,"current_planner_id":pid,"packages":a,"ai_used":false,"neurons_used":0}
}
func (s *S)scripts(w http.ResponseWriter,r *http.Request){js(w,s.scriptInfo())}

type ProductionPack struct{
 State string `json:"state"`
 Engine string `json:"engine"`
 PlannerID string `json:"planner_id"`
 Topic string `json:"topic"`
 Language string `json:"language"`
 TargetAge string `json:"target_age"`
 DurationSec int `json:"duration_sec"`
 VideoTitle string `json:"video_title"`
 SceneCount int `json:"scene_count"`
 Files []string `json:"files"`
 DownloadEndpoint string `json:"download_endpoint"`
 GeneratedAt string `json:"generated_at"`
 AIUsed bool `json:"ai_used"`
 NeuronsUsed int `json:"neurons_used"`
}
func srtTime(sec int)string{h:=sec/3600;m:=(sec%3600)/60;s:=sec%60;return fmt.Sprintf("%02d:%02d:%02d,000",h,m,s)}
func (s *S)productionRoot()string{return filepath.Join(s.Root,"data","production")}
func (s *S)productionDir(id string)string{return filepath.Join(s.productionRoot(),"packs",id)}
func (s *S)productionZip(id string)string{return filepath.Join(s.productionRoot(),"exports",id+".zip")}
func safePlanID(id string)bool{if len(id)<6||len(id)>64{return false};for _,r:=range id{if !(r>='0'&&r<='9'||r>='a'&&r<='f'||r>='A'&&r<='F'||r=='-'||r=='_'){return false}};return true}
func productionTexts(sp ScriptPackage)(string,string,string,string){
 var narration,visuals strings.Builder
 elapsed:=0;var srt strings.Builder
 for i,sc:=range sp.Scenes{
  narration.WriteString(fmt.Sprintf("SCENE %d | %ds | %s\n%s\n\n",sc.Number,sc.DurationSec,sc.Purpose,sc.VoiceOver))
  visuals.WriteString(fmt.Sprintf("SCENE %d | %s\n%s\nON-SCREEN TEXT: %s\nEDIT: %s\n\n",sc.Number,sc.Purpose,sc.VisualPrompt,sc.OnScreenText,sc.EditNote))
  start:=elapsed;elapsed+=sc.DurationSec
  srt.WriteString(fmt.Sprintf("%d\n%s --> %s\n%s\n\n",i+1,srtTime(start),srtTime(elapsed),sc.VoiceOver))
 }
 metadata:=fmt.Sprintf("TITLE\n%s\n\nDESCRIPTION\n%s\n\nLANGUAGE\n%s\n\nTARGET AGE\n%s\n\nDURATION\n%d seconds\n\nKEYWORDS\n%s\n\nHASHTAGS\n%s\n\nCHARACTER CONSISTENCY\n%s\n",sp.VideoTitle,sp.Description,sp.Language,sp.TargetAge,sp.DurationSec,strings.Join(sp.Keywords,", "),strings.Join(sp.Hashtags," "),sp.CharacterRule)
 return narration.String(),visuals.String(),srt.String(),metadata
}
func writeSimple(path,body string)error{if e:=os.MkdirAll(filepath.Dir(path),0700);e!=nil{return e};tmp:=path+".tmp";if e:=os.WriteFile(tmp,[]byte(body),0600);e!=nil{return e};return os.Rename(tmp,path)}
func zipFiles(dst,base string,names []string)error{
 if e:=os.MkdirAll(filepath.Dir(dst),0700);e!=nil{return e}
 tmp:=dst+".tmp";o,e:=os.Create(tmp);if e!=nil{return e};zw:=zip.NewWriter(o)
 for _,name:=range names{
  b,e:=os.ReadFile(filepath.Join(base,name));if e!=nil{zw.Close();o.Close();os.Remove(tmp);return e}
  w,e:=zw.Create(name);if e!=nil{zw.Close();o.Close();os.Remove(tmp);return e}
  if _,e=w.Write(b);e!=nil{zw.Close();o.Close();os.Remove(tmp);return e}
 }
 if e=zw.Close();e!=nil{o.Close();os.Remove(tmp);return e};if e=o.Close();e!=nil{os.Remove(tmp);return e}
 return os.Rename(tmp,dst)
}
func (s *S)writeProductionPack(sp ScriptPackage)(ProductionPack,error){
 dir:=s.productionDir(sp.PlannerID);os.MkdirAll(dir,0700)
 narration,visuals,captions,metadata:=productionTexts(sp)
 files:=[]string{"script.json","narration.txt","visual_prompts.txt","captions.srt","metadata.txt","manifest.json"}
 sb,_:=json.MarshalIndent(sp,"","  ");if e:=os.WriteFile(filepath.Join(dir,"script.json"),sb,0600);e!=nil{return ProductionPack{},e}
 if e:=writeSimple(filepath.Join(dir,"narration.txt"),narration);e!=nil{return ProductionPack{},e}
 if e:=writeSimple(filepath.Join(dir,"visual_prompts.txt"),visuals);e!=nil{return ProductionPack{},e}
 if e:=writeSimple(filepath.Join(dir,"captions.srt"),captions);e!=nil{return ProductionPack{},e}
 if e:=writeSimple(filepath.Join(dir,"metadata.txt"),metadata);e!=nil{return ProductionPack{},e}
 p:=ProductionPack{State:"READY_FOR_PRODUCTION",Engine:"PRODUCTION_PACK_V1",PlannerID:sp.PlannerID,Topic:sp.Topic,Language:sp.Language,TargetAge:sp.TargetAge,DurationSec:sp.DurationSec,VideoTitle:sp.VideoTitle,SceneCount:sp.SceneCount,Files:files,DownloadEndpoint:"/api/work/production/download?id="+sp.PlannerID,GeneratedAt:time.Now().Format(time.RFC3339),AIUsed:false,NeuronsUsed:0}
 pb,_:=json.MarshalIndent(p,"","  ");if e:=os.WriteFile(filepath.Join(dir,"manifest.json"),pb,0600);e!=nil{return p,e}
 if e:=zipFiles(s.productionZip(sp.PlannerID),dir,files);e!=nil{return p,e}
 return p,nil
}
func (s *S)ensureProductionPacks()[]ProductionPack{
 scripts:=s.ensureScripts();plans:=s.syncPlanner();idx:=map[string]int{};for i:=range plans{idx[plans[i].ID]=i}
 out:=[]ProductionPack{};changed:=false
 for _,sp:=range scripts{
  i,ok:=idx[sp.PlannerID];if !ok{continue};stage:=plans[i].Stage;if stage=="HOLD"{continue}
  mp:=filepath.Join(s.productionDir(sp.PlannerID),"manifest.json");zp:=s.productionZip(sp.PlannerID);var pp ProductionPack
  if b,e:=os.ReadFile(mp);e==nil&&json.Unmarshal(b,&pp)==nil&&pp.PlannerID==sp.PlannerID&&exists(zp){
   out=append(out,pp);if stage=="SCRIPT_READY"{plans[i].Stage="PRODUCTION_READY";plans[i].UpdatedAt=time.Now().Format(time.RFC3339);changed=true};continue
  }
  if stage=="SCRIPT_READY"||stage=="PRODUCTION_READY"||stage=="PRODUCTION"||stage=="UPLOAD_READY"||stage=="PUBLISHED"{
   if np,e:=s.writeProductionPack(sp);e==nil{out=append(out,np);if stage=="SCRIPT_READY"{plans[i].Stage="PRODUCTION_READY";plans[i].UpdatedAt=time.Now().Format(time.RFC3339);changed=true}}
  }
 }
 if changed{_ = s.savePlan(plans)}
 return out
}
func (s *S)productionInfo()map[string]any{
 a:=s.ensureProductionPacks();topic:="";pid:="";download:="";if len(a)>0{topic=a[0].Topic;pid=a[0].PlannerID;download=a[0].DownloadEndpoint}
 return map[string]any{"state":"READY","engine":"PRODUCTION_PACK_V1","ready":len(a),"current_topic":topic,"current_planner_id":pid,"download_endpoint":download,"packs":a,"ai_used":false,"neurons_used":0}
}
func (s *S)production(w http.ResponseWriter,r *http.Request){js(w,s.productionInfo())}
func (s *S)productionDownload(w http.ResponseWriter,r *http.Request){
 id:=strings.TrimSpace(r.URL.Query().Get("id"));if !safePlanID(id){http.Error(w,"invalid id",400);return}
 p:=s.productionZip(id);if !exists(p){s.ensureProductionPacks()};if !exists(p){http.Error(w,"production pack not found",404);return}
 w.Header().Set("Content-Type","application/zip");w.Header().Set("Content-Disposition","attachment; filename=\"HERMES_WORK_"+id+"_production.zip\"");http.ServeFile(w,r,p)
}


func (s *S)planner(w http.ResponseWriter,r *http.Request){
 if r.Method=="GET"{js(w,s.plannerInfo());return}
 if !s.auth(r){http.Error(w,"unauthorized",401);return}
 var q struct{ID string `json:"id"`;Stage string `json:"stage"`}
 if json.NewDecoder(io.LimitReader(r.Body,65536)).Decode(&q)!=nil||q.ID==""||!validStage(q.Stage){http.Error(w,"invalid planner update",400);return}
 a:=s.syncPlanner();found:=false;now:=time.Now().Format(time.RFC3339)
 for i:=range a{if a[i].ID==q.ID{a[i].Stage=q.Stage;a[i].UpdatedAt=now;found=true;break}}
 if !found{http.Error(w,"plan item not found",404);return}
 if e:=s.savePlan(a);e!=nil{http.Error(w,e.Error(),500);return}
 js(w,map[string]any{"ok":true,"id":q.ID,"stage":q.Stage})
}

func (s *S)brief(w http.ResponseWriter,r *http.Request){js(w,s.dailyBriefInfo())}
func (s *S)schedule(w http.ResponseWriter,r *http.Request){ok,reason:=guard(s);next:=readenv(filepath.Join(s.Rel,"config","work.env"),"RESEARCH_SCHEDULE");js(w,map[string]any{"enabled":true,"schedule":next,"guard_ready":ok,"guard_reason":reason,"last_research":strings.TrimSpace(readfile(filepath.Join(s.Root,"state","last_research")))})}

func (s *S)daily(w http.ResponseWriter,r *http.Request){
 var last any=map[string]any{"state":"NO_RESEARCH_YET"}
 if b,e:=os.ReadFile(filepath.Join(s.Root,"state","last_research_result.json"));e==nil{_ = json.Unmarshal(b,&last)}
 b,_:=os.ReadFile(filepath.Join(s.Root,"data","database","research.jsonl"));cats:=map[string]int{};total:=0
 for _,l:=range strings.Split(strings.TrimSpace(string(b)),"\n"){if l==""{continue};var z map[string]any;if json.Unmarshal([]byte(l),&z)==nil{total++;if x,ok:=z["category"].(string);ok{cats[x]++}}}
 db:=s.dailyBriefInfo()
 js(w,map[string]any{"generated_at":time.Now().Format(time.RFC3339),"last_run":last,"total_research_items":total,"categories":cats,"brief":db,"ideas_ready":db["ideas_ready"],"top_opportunity":db["top_opportunity"],"brief_endpoint":"/api/work/brief"})
}


type HandoffJob struct{
 ID string `json:"id"`
 Topic string `json:"topic"`
 Category string `json:"category"`
 Priority int `json:"priority"`
 Stage string `json:"stage"`
 HandoffState string `json:"handoff_state"`
 ProductionPack string `json:"production_pack"`
 VideoTitle string `json:"video_title"`
 Language string `json:"language"`
 DurationSec int `json:"duration_sec"`
 UpdatedAt string `json:"updated_at"`
}
func handoffState(stage string)string{
 switch stage{
 case"PRODUCTION_READY":return"READY_TO_PRODUCE"
 case"PRODUCTION":return"PRODUCING"
 case"UPLOAD_READY":return"READY_TO_UPLOAD"
 case"PUBLISHED":return"PUBLISHED"
 case"HOLD":return"HOLD"
 default:return"WAITING"
 }
}
func handoffStage(state string)string{
 switch state{
 case"READY_TO_PRODUCE":return"PRODUCTION_READY"
 case"PRODUCING":return"PRODUCTION"
 case"READY_TO_UPLOAD":return"UPLOAD_READY"
 case"PUBLISHED":return"PUBLISHED"
 case"HOLD":return"HOLD"
 default:return""
 }
}
func (s *S)handoffInfo()map[string]any{
 packs:=s.ensureProductionPacks();plans:=s.syncPlanner();pm:=map[string]ProductionPack{};for _,p:=range packs{pm[p.PlannerID]=p}
 jobs:=[]HandoffJob{};counts:=map[string]int{"READY_TO_PRODUCE":0,"PRODUCING":0,"READY_TO_UPLOAD":0,"PUBLISHED":0,"HOLD":0}
 for _,p:=range plans{
  pp,ok:=pm[p.ID];if !ok{continue};hs:=handoffState(p.Stage);if hs=="WAITING"{continue}
  counts[hs]++
  jobs=append(jobs,HandoffJob{ID:p.ID,Topic:p.Title,Category:p.Category,Priority:p.Priority,Stage:p.Stage,HandoffState:hs,ProductionPack:pp.DownloadEndpoint,VideoTitle:pp.VideoTitle,Language:pp.Language,DurationSec:pp.DurationSec,UpdatedAt:p.UpdatedAt})
 }
 sort.SliceStable(jobs,func(i,j int)bool{if jobs[i].HandoffState==jobs[j].HandoffState{return jobs[i].Priority<jobs[j].Priority};return jobs[i].HandoffState<jobs[j].HandoffState})
 next:="";nextID:="";for _,j:=range jobs{if j.HandoffState=="READY_TO_PRODUCE"{next=j.Topic;nextID=j.ID;break}}
 return map[string]any{"state":"READY","engine":"HANDOFF_V1","queue_total":len(jobs),"counts":counts,"next_job":next,"next_job_id":nextID,"jobs":jobs,"ai_used":false,"neurons_used":0}
}
func (s *S)handoff(w http.ResponseWriter,r *http.Request){
 if r.Method=="GET"{js(w,s.handoffInfo());return}
 if r.Method!="POST"{http.Error(w,"method not allowed",405);return}
 if !s.auth(r){http.Error(w,"unauthorized",401);return}
 var q struct{ID string `json:"id"`;State string `json:"state"`}
 if json.NewDecoder(io.LimitReader(r.Body,65536)).Decode(&q)!=nil||!safePlanID(q.ID){http.Error(w,"invalid handoff update",400);return}
 stage:=handoffStage(q.State);if stage==""{http.Error(w,"invalid handoff state",400);return}
 plans:=s.syncPlanner();found:=false;now:=time.Now().Format(time.RFC3339)
 for i:=range plans{if plans[i].ID==q.ID{plans[i].Stage=stage;plans[i].UpdatedAt=now;found=true;break}}
 if !found{http.Error(w,"job not found",404);return}
 if e:=s.savePlan(plans);e!=nil{http.Error(w,e.Error(),500);return}
 js(w,map[string]any{"ok":true,"id":q.ID,"state":q.State,"stage":stage,"handoff":s.handoffInfo()})
}



func (s *S)deskInfo()map[string]any{
 h:=s.handoffInfo();p:=s.publicationSummary()
 return map[string]any{"state":"READY","engine":"PRODUCTION_DESK_V1","queue_total":h["queue_total"],"counts":h["counts"],"next_job":h["next_job"],"next_job_id":h["next_job_id"],"jobs":h["jobs"],"publication_state":p["state"],"publications":p["records"],"ai_used":false,"neurons_used":0}
}
func (s *S)desk(w http.ResponseWriter,r *http.Request){js(w,s.deskInfo())}
func (s *S)jobDetail(w http.ResponseWriter,r *http.Request){
 id:=strings.TrimSpace(r.URL.Query().Get("id"));if !safePlanID(id){http.Error(w,"invalid id",400);return}
 plans:=s.syncPlanner();var plan *PlanItem
 for i:=range plans{if plans[i].ID==id{plan=&plans[i];break}}
 if plan==nil{http.Error(w,"job not found",404);return}
 var script ScriptPackage;hasScript:=false
 if b,e:=os.ReadFile(filepath.Join(s.scriptDir(),id+".json"));e==nil&&json.Unmarshal(b,&script)==nil{hasScript=true}
 var pack ProductionPack;hasPack:=false
 if b,e:=os.ReadFile(filepath.Join(s.productionDir(id),"manifest.json"));e==nil&&json.Unmarshal(b,&pack)==nil{hasPack=true}
 pubs:=[]PublicationRecord{};for _,p:=range s.loadPublications(){if p.PlannerID==id{pubs=append(pubs,p)}}
 var sv any=nil;if hasScript{sv=script};var pv any=nil;if hasPack{pv=pack}
 js(w,map[string]any{"state":"READY","engine":"JOB_DETAIL_V1","plan":plan,"script":sv,"production_pack":pv,"publications":pubs,"ai_used":false,"neurons_used":0})
}


type StudioJob struct{
 State string `json:"state"`
 Engine string `json:"engine"`
 PlannerID string `json:"planner_id"`
 Topic string `json:"topic"`
 Category string `json:"category"`
 Language string `json:"language"`
 VideoTitle string `json:"video_title"`
 Description string `json:"description"`
 DurationSec int `json:"duration_sec"`
 Scenes []FinalScene `json:"scenes"`
 Hashtags []string `json:"hashtags"`
 RenderTag string `json:"render_tag"`
 VisualProvider string `json:"visual_provider"`
 VoiceProvider string `json:"voice_provider"`
 RenderProvider string `json:"render_provider"`
 CreatedAt string `json:"created_at"`
 AIUsed bool `json:"ai_used"`
 NeuronsUsed int `json:"neurons_used"`
}
type StudioResult struct{
 State string `json:"state"`
 PlannerID string `json:"planner_id"`
 RenderTag string `json:"render_tag"`
 VideoURL string `json:"video_url"`
 ThumbnailURL string `json:"thumbnail_url"`
 MetadataURL string `json:"metadata_url"`
 CompletedAt string `json:"completed_at"`
 Engine string `json:"engine"`
 AIUsed bool `json:"ai_used"`
 NeuronsUsed int `json:"neurons_used"`
}
func (s *S)studioRoot()string{return filepath.Join(s.Root,"data","studio")}
func (s *S)studioPendingPath()string{return filepath.Join(s.studioRoot(),"pending.json")}
func (s *S)studioResultPath(id string)string{return filepath.Join(s.studioRoot(),"results",id+".json")}
func studioTag(id string)string{return"hermes-studio-"+strings.ToLower(id)}
func (s *S)loadStudioPending()(StudioJob,bool){
 var j StudioJob;b,e:=os.ReadFile(s.studioPendingPath());if e!=nil||json.Unmarshal(b,&j)!=nil||!safePlanID(j.PlannerID){return j,false};return j,true
}
func (s *S)saveStudioPending(j StudioJob)error{
 b,_:=json.MarshalIndent(j,"","  ");return writeSimple(s.studioPendingPath(),string(b)+"\n")
}
func (s *S)clearStudioPending(){os.Remove(s.studioPendingPath())}
func (s *S)loadStudioResult(id string)(StudioResult,bool){
 var x StudioResult;b,e:=os.ReadFile(s.studioResultPath(id));if e!=nil||json.Unmarshal(b,&x)!=nil||x.PlannerID!=id{return x,false};return x,true
}
func (s *S)studioRenderedToday()bool{return strings.TrimSpace(readfile(filepath.Join(s.Root,"state","last_studio_render_date")))==time.Now().Format("2006-01-02")}
func (s *S)buildStudioJob()(StudioJob,bool){
 plans:=s.syncPlanner()
 scripts:=s.ensureScripts();sm:=map[string]ScriptPackage{};for _,x:=range scripts{sm[x.PlannerID]=x}
 for _,p:=range plans{
  if p.Stage!="PRODUCTION_READY"{continue}
  sp,ok:=sm[p.ID];if !ok{continue}
  j:=StudioJob{State:"WAITING_RENDER",Engine:"AUTO_STUDIO_V1",PlannerID:p.ID,Topic:p.Title,Category:p.Category,Language:sp.Language,VideoTitle:sp.VideoTitle,Description:sp.Description,DurationSec:sp.DurationSec,Scenes:sp.Scenes,Hashtags:sp.Hashtags,RenderTag:studioTag(p.ID),VisualProvider:"FREE_FIRST_AI_WITH_DETERMINISTIC_FALLBACK",VoiceProvider:"NO_CARD_TTS_WITH_LOCAL_FALLBACK",RenderProvider:"GITHUB_ACTIONS_FFMPEG",CreatedAt:time.Now().Format(time.RFC3339),AIUsed:false,NeuronsUsed:0}
  return j,true
 }
 return StudioJob{},false
}
func (s *S)studioInfo()map[string]any{
 if j,ok:=s.loadStudioPending();ok{
  if r,done:=s.loadStudioResult(j.PlannerID);done{return map[string]any{"state":"RENDERED","engine":"AUTO_STUDIO_V1","job":j,"result":r,"daily_target":1,"ai_used":false,"neurons_used":0}}
  return map[string]any{"state":"WAITING_RENDER","engine":"AUTO_STUDIO_V1","job":j,"daily_target":1,"ai_used":false,"neurons_used":0}
 }
 if s.studioRenderedToday(){return map[string]any{"state":"DAILY_TARGET_MET","engine":"AUTO_STUDIO_V1","job":nil,"daily_target":1,"ai_used":false,"neurons_used":0}}
 j,ok:=s.buildStudioJob();if !ok{return map[string]any{"state":"WAITING_PRODUCTION_JOB","engine":"AUTO_STUDIO_V1","job":nil,"daily_target":1,"ai_used":false,"neurons_used":0}}
 os.MkdirAll(filepath.Dir(s.studioPendingPath()),0700);_ = s.saveStudioPending(j)
 return map[string]any{"state":"WAITING_RENDER","engine":"AUTO_STUDIO_V1","job":j,"daily_target":1,"ai_used":false,"neurons_used":0}
}
func (s *S)studio(w http.ResponseWriter,r *http.Request){js(w,s.studioInfo())}
func (s *S)pollStudioResult(){
 j,ok:=s.loadStudioPending();if !ok{return}
 u:="https://api.github.com/repos/Djaeger1/DJAEGER-Control-Center/releases/tags/"+url.PathEscape(j.RenderTag)
 req,e:=http.NewRequest("GET",u,nil);if e!=nil{return};req.Header.Set("User-Agent","HERMES-WORK-Auto-Studio/1.0")
 cl:=androidHTTPClient();cl.Timeout=20*time.Second;resp,e:=cl.Do(req);if e!=nil{return};defer resp.Body.Close();if resp.StatusCode!=200{return}
 var gh struct{Assets []struct{Name string `json:"name"`;URL string `json:"browser_download_url"`} `json:"assets"`;PublishedAt string `json:"published_at"`}
 if json.NewDecoder(io.LimitReader(resp.Body,2<<20)).Decode(&gh)!=nil{return}
 video:="";thumb:="";meta:=""
 for _,a:=range gh.Assets{switch{case strings.HasSuffix(strings.ToLower(a.Name),".mp4"):video=a.URL;case strings.Contains(strings.ToLower(a.Name),"thumbnail")&&(strings.HasSuffix(strings.ToLower(a.Name),".jpg")||strings.HasSuffix(strings.ToLower(a.Name),".png")):thumb=a.URL;case strings.HasSuffix(strings.ToLower(a.Name),".json"):meta=a.URL}}
 if video==""{return}
 done:=gh.PublishedAt;if done==""{done=time.Now().Format(time.RFC3339)}
 r:=StudioResult{State:"RENDERED",PlannerID:j.PlannerID,RenderTag:j.RenderTag,VideoURL:video,ThumbnailURL:thumb,MetadataURL:meta,CompletedAt:done,Engine:"AUTO_STUDIO_V1",AIUsed:false,NeuronsUsed:0}
 b,_:=json.MarshalIndent(r,"","  ");os.MkdirAll(filepath.Dir(s.studioResultPath(j.PlannerID)),0700);if writeSimple(s.studioResultPath(j.PlannerID),string(b)+"\n")!=nil{return}
 plans:=s.syncPlanner();changed:=false;now:=time.Now().Format(time.RFC3339)
 for i:=range plans{if plans[i].ID==j.PlannerID{plans[i].Stage="UPLOAD_READY";plans[i].UpdatedAt=now;changed=true;break}}
 if changed{_ = s.savePlan(plans)}
 _ = os.WriteFile(filepath.Join(s.Root,"state","last_studio_render_date"),[]byte(time.Now().Format("2006-01-02")+"\n"),0600)
 s.clearStudioPending()
}
type PublicationRecord struct{
 PlannerID string `json:"planner_id"`
 Topic string `json:"topic"`
 Platform string `json:"platform"`
 ExternalID string `json:"external_id"`
 URL string `json:"url"`
 Channel string `json:"channel"`
 Status string `json:"status"`
 PublishedAt string `json:"published_at"`
 RecordedAt string `json:"recorded_at"`
}
func (s *S)publicationPath()string{return filepath.Join(s.Root,"data","channel","publications.jsonl")}
func (s *S)loadPublications()[]PublicationRecord{
 b,e:=os.ReadFile(s.publicationPath());if e!=nil{return nil};out:=[]PublicationRecord{}
 for _,l:=range strings.Split(strings.TrimSpace(string(b)),"\n"){
  if strings.TrimSpace(l)==""{continue};var p PublicationRecord
  if json.Unmarshal([]byte(l),&p)==nil&&p.PlannerID!=""{out=append(out,p)}
 }
 return out
}
func (s *S)appendPublication(p PublicationRecord)error{
 if p.RecordedAt==""{p.RecordedAt=time.Now().Format(time.RFC3339)}
 if p.PublishedAt==""{p.PublishedAt=p.RecordedAt}
 if p.Status==""{p.Status="PUBLISHED"}
 os.MkdirAll(filepath.Dir(s.publicationPath()),0700)
 b,_:=json.Marshal(p);f,e:=os.OpenFile(s.publicationPath(),os.O_CREATE|os.O_APPEND|os.O_WRONLY,0600);if e!=nil{return e};defer f.Close()
 _,e=f.Write(append(b,'\n'));return e
}
func (s *S)publicationSummary()map[string]any{
 a:=s.loadPublications();plats:=map[string]int{};latestTopic:="";latestURL:="";latestAt:=""
 for _,p:=range a{plats[p.Platform]++;if p.RecordedAt>=latestAt{latestAt=p.RecordedAt;latestTopic=p.Topic;latestURL=p.URL}}
 state:="READY_WAITING_PUBLICATION";if len(a)>0{state="CONNECTED"}
 return map[string]any{"state":state,"engine":"PUBLICATION_V1","records":len(a),"platforms":plats,"latest_topic":latestTopic,"latest_url":latestURL,"latest_at":latestAt,"ai_used":false,"neurons_used":0}
}
func validPlatform(x string)bool{
 switch strings.ToLower(strings.TrimSpace(x)){case"youtube","youtube_shorts","tiktok","instagram","facebook","other":return true};return false
}
func (s *S)publication(w http.ResponseWriter,r *http.Request){
 if r.Method=="GET"{js(w,s.publicationSummary());return}
 if r.Method!="POST"{http.Error(w,"method not allowed",405);return}
 if !s.auth(r){http.Error(w,"unauthorized",401);return}
 var p PublicationRecord
 if json.NewDecoder(io.LimitReader(r.Body,131072)).Decode(&p)!=nil||!safePlanID(p.PlannerID)||!validPlatform(p.Platform){http.Error(w,"invalid publication data",400);return}
 p.Platform=strings.ToLower(strings.TrimSpace(p.Platform))
 plans:=s.syncPlanner();found:=false;now:=time.Now().Format(time.RFC3339)
 for i:=range plans{
  if plans[i].ID==p.PlannerID{
   found=true
   if p.Topic==""{p.Topic=plans[i].Title}
   plans[i].Stage="PUBLISHED";plans[i].UpdatedAt=now
   break
  }
 }
 if !found{http.Error(w,"planner item not found",404);return}
 if e:=s.appendPublication(p);e!=nil{http.Error(w,e.Error(),500);return}
 if e:=s.savePlan(plans);e!=nil{http.Error(w,e.Error(),500);return}
 js(w,map[string]any{"ok":true,"publication":p,"summary":s.publicationSummary(),"feedback_endpoint":"/api/work/performance"})
}
type ChannelImport struct{
 PlannerID string `json:"planner_id"`
 Views int64 `json:"views"`
 RetentionPct float64 `json:"retention_pct"`
 CTRPct float64 `json:"ctr_pct"`
 WatchTimeMin float64 `json:"watch_time_min"`
 Likes int64 `json:"likes"`
 Comments int64 `json:"comments"`
 CapturedAt string `json:"captured_at"`
}
func (s *S)channelImport(w http.ResponseWriter,r *http.Request){
 if r.Method!="POST"{http.Error(w,"method not allowed",405);return}
 if !s.auth(r){http.Error(w,"unauthorized",401);return}
 var q struct{Platform string `json:"platform"`;Records []ChannelImport `json:"records"`}
 if json.NewDecoder(io.LimitReader(r.Body,1<<20)).Decode(&q)!=nil||!validPlatform(q.Platform)||len(q.Records)==0||len(q.Records)>100{http.Error(w,"invalid channel import",400);return}
 plans:=s.syncPlanner();pm:=map[string]PlanItem{};for _,p:=range plans{pm[p.ID]=p}
 added:=0
 for _,x:=range q.Records{
  p,ok:=pm[x.PlannerID];if !ok||x.Views<0||x.RetentionPct<0||x.RetentionPct>100||x.CTRPct<0||x.CTRPct>100||x.WatchTimeMin<0{continue}
  rec:=PerformanceRecord{PlannerID:x.PlannerID,Topic:p.Title,Category:p.Category,Views:x.Views,RetentionPct:x.RetentionPct,CTRPct:x.CTRPct,WatchTimeMin:x.WatchTimeMin,Likes:x.Likes,Comments:x.Comments,Source:"channel_import:"+strings.ToLower(strings.TrimSpace(q.Platform)),CapturedAt:x.CapturedAt}
  if s.appendPerformance(rec)==nil{added++}
 }
 js(w,map[string]any{"ok":true,"platform":strings.ToLower(strings.TrimSpace(q.Platform)),"received":len(q.Records),"added":added,"summary":s.performanceSummary()})
}

type PerformanceRecord struct{
 PlannerID string `json:"planner_id"`
 Topic string `json:"topic"`
 Category string `json:"category"`
 Views int64 `json:"views"`
 RetentionPct float64 `json:"retention_pct"`
 CTRPct float64 `json:"ctr_pct"`
 WatchTimeMin float64 `json:"watch_time_min"`
 Likes int64 `json:"likes"`
 Comments int64 `json:"comments"`
 Source string `json:"source"`
 CapturedAt string `json:"captured_at"`
}
func (s *S)performancePath()string{return filepath.Join(s.Root,"data","channel","performance.jsonl")}
func (s *S)loadPerformance()[]PerformanceRecord{
 b,e:=os.ReadFile(s.performancePath());if e!=nil{return nil};out:=[]PerformanceRecord{}
 for _,l:=range strings.Split(strings.TrimSpace(string(b)),"\n"){if strings.TrimSpace(l)==""{continue};var p PerformanceRecord;if json.Unmarshal([]byte(l),&p)==nil&&p.PlannerID!=""{out=append(out,p)}}
 return out
}
func (s *S)appendPerformance(p PerformanceRecord)error{
 if p.CapturedAt==""{p.CapturedAt=time.Now().Format(time.RFC3339)};if p.Source==""{p.Source="manual_or_connector"}
 os.MkdirAll(filepath.Dir(s.performancePath()),0700);b,_:=json.Marshal(p);f,e:=os.OpenFile(s.performancePath(),os.O_CREATE|os.O_APPEND|os.O_WRONLY,0600);if e!=nil{return e};defer f.Close();_,e=f.Write(append(b,'\n'));return e
}
func perfSignal(p PerformanceRecord)string{
 if p.RetentionPct>=55{return"STRONG"}
 if p.RetentionPct>0&&p.RetentionPct<35{return"WEAK"}
 return"NEUTRAL"
}
func (s *S)performanceSummary()map[string]any{
 a:=s.loadPerformance();strong:=0;weak:=0;neutral:=0;views:=int64(0);watch:=0.0;retSum:=0.0;retN:=0;ctrSum:=0.0;ctrN:=0
 cats:=map[string]map[string]float64{}
 for _,p:=range a{
  views+=p.Views;watch+=p.WatchTimeMin
  if p.RetentionPct>0{retSum+=p.RetentionPct;retN++}
  if p.CTRPct>0{ctrSum+=p.CTRPct;ctrN++}
  switch perfSignal(p){case"STRONG":strong++;case"WEAK":weak++;default:neutral++}
  if p.Category!=""{
   z:=cats[p.Category];if z==nil{z=map[string]float64{"records":0,"retention_sum":0,"retention_n":0};cats[p.Category]=z}
   z["records"]++
   if p.RetentionPct>0{z["retention_sum"]+=p.RetentionPct;z["retention_n"]++}
  }
 }
 avgRet:=0.0;if retN>0{avgRet=retSum/float64(retN)};avgCTR:=0.0;if ctrN>0{avgCTR=ctrSum/float64(ctrN)}
 catOut:=map[string]any{};for k,z:=range cats{ar:=0.0;if z["retention_n"]>0{ar=z["retention_sum"]/z["retention_n"]};catOut[k]=map[string]any{"records":int(z["records"]),"avg_retention_pct":ar}}
 state:="WAITING_REAL_DATA";if len(a)>0{state="LEARNING"}
 return map[string]any{"state":state,"engine":"FEEDBACK_V1","records":len(a),"strong_signal":strong,"weak_signal":weak,"neutral_signal":neutral,"views":views,"watch_time_min":watch,"avg_retention_pct":avgRet,"avg_ctr_pct":avgCTR,"categories":catOut,"ai_used":false,"neurons_used":0}
}
func (s *S)performanceAdjustment(cat string)float64{
 a:=s.loadPerformance();sum:=0.0;n:=0
 for _,p:=range a{if p.Category!=cat||p.RetentionPct<=0{continue};n++;if p.RetentionPct>=55{sum+=4}else if p.RetentionPct<35{sum-=3}}
 if n==0{return 0};adj:=sum/float64(n);if adj>8{adj=8};if adj < -8{adj=-8};return adj
}
func (s *S)performance(w http.ResponseWriter,r *http.Request){
 if r.Method=="GET"{js(w,s.performanceSummary());return}
 if r.Method!="POST"{http.Error(w,"method not allowed",405);return}
 if !s.auth(r){http.Error(w,"unauthorized",401);return}
 var p PerformanceRecord
 if json.NewDecoder(io.LimitReader(r.Body,131072)).Decode(&p)!=nil||!safePlanID(p.PlannerID)||p.Views<0||p.RetentionPct<0||p.RetentionPct>100||p.CTRPct<0||p.CTRPct>100||p.WatchTimeMin<0{http.Error(w,"invalid performance data",400);return}
 plans:=s.syncPlanner();found:=false
 for i:=range plans{if plans[i].ID==p.PlannerID{found=true;if p.Topic==""{p.Topic=plans[i].Title};if p.Category==""{p.Category=plans[i].Category};if plans[i].Stage!="PUBLISHED"{plans[i].Stage="PUBLISHED";plans[i].UpdatedAt=time.Now().Format(time.RFC3339)};break}}
 if !found{http.Error(w,"planner item not found",404);return}
 if e:=s.appendPerformance(p);e!=nil{http.Error(w,e.Error(),500);return};_ = s.savePlan(plans)
 js(w,map[string]any{"ok":true,"signal":perfSignal(p),"summary":s.performanceSummary()})
}
func (s *S)channel(w http.ResponseWriter,r *http.Request){
 pub:=s.publicationSummary();ps:=s.performanceSummary();records,_:=ps["records"].(int)
 if records==0{js(w,map[string]any{"state":"READY_WAITING_ANALYTICS","views":"NOT_AVAILABLE","retention":"NOT_AVAILABLE","ctr":"NOT_AVAILABLE","watch_time":"NOT_AVAILABLE","best_topic":"NOT_AVAILABLE","weak_topic":"NOT_AVAILABLE","publication_state":pub["state"],"publications":pub["records"],"feedback":"READY_WAITING_REAL_DATA","connector":"CHANNEL_CONNECTOR_V1"});return}
 best:="";weak:="";bestR:=-1.0;weakR:=101.0
 for _,p:=range s.loadPerformance(){if p.RetentionPct>bestR{bestR=p.RetentionPct;best=p.Topic};if p.RetentionPct>0&&p.RetentionPct<weakR{weakR=p.RetentionPct;weak=p.Topic}}
 js(w,map[string]any{"state":"FEEDBACK_CONNECTED","views":ps["views"],"retention":ps["avg_retention_pct"],"ctr":ps["avg_ctr_pct"],"watch_time":ps["watch_time_min"],"best_topic":best,"weak_topic":weak,"records":records,"publication_state":pub["state"],"publications":pub["records"],"engine":"CHANNEL_CONNECTOR_V1"})
}
func (s *S)knowledge(w http.ResponseWriter,r *http.Request){
 b,_:=os.ReadFile(filepath.Join(s.Root,"data","database","research.jsonl"));seen:=map[string]bool{};cats:=map[string]int{};total:=0
 for _,l:=range strings.Split(strings.TrimSpace(string(b)),"\n"){if l==""{continue};var z map[string]any;if json.Unmarshal([]byte(l),&z)!=nil{continue};total++;if u,_:=z["url"].(string);u!=""{seen[u]=true};if x,_:=z["category"].(string);x!=""{cats[x]++}}
 ps:=s.performanceSummary();plans:=s.syncPlanner();notProduced:=0;for _,p:=range plans{if p.Stage!="PUBLISHED"{notProduced++}}
 js(w,map[string]any{"research_items":total,"unique_keys":len(seen),"categories":cats,"produced":ps["records"],"published":s.publicationSummary()["records"],"successful":ps["strong_signal"],"underperforming":ps["weak_signal"],"ideas_not_produced":notProduced,"feedback_state":ps["state"],"feedback_engine":"FEEDBACK_V1","publication_engine":"PUBLICATION_V1"})
}
func (s *S)recovery(w http.ResponseWriter,r *http.Request){js(w,map[string]any{"current":strings.TrimSpace(readfile(filepath.Join(s.Root,"current_release"))),"previous":strings.TrimSpace(readfile(filepath.Join(s.Root,"previous_release"))),"safe_mode":exists(filepath.Join(s.Root,"state","safe_mode")),"worker_paused":exists(filepath.Join(s.Root,"state","worker_paused")),"handoff_log":tail(filepath.Join(s.Root,"logs","handoff.log"),20)})}
func (s *S)index(w http.ResponseWriter,r *http.Request){w.Header().Set("Content-Type","text/html; charset=utf-8");io.WriteString(w,page)}
func main(){root:=flag.String("root","/data/adb/hermes_work","");rel:=flag.String("release","","");flag.Parse();p:=8766;if x:=readenv(filepath.Join(*rel,"config","work.env"),"WORK_PORT");x!=""{p,_=strconv.Atoi(x)};s:=&S{Root:*root,Rel:*rel,Port:p,Token:readenv(filepath.Join(*root,"config.env"),"ADMIN_TOKEN")};m:=http.NewServeMux();m.HandleFunc("/",s.index);m.HandleFunc("/api/work/status",s.status);m.HandleFunc("/api/work/action",s.action);m.HandleFunc("/api/work/diagnostics",s.diag);m.HandleFunc("/api/work/update",s.update);m.HandleFunc("/api/work/collect",s.collect);m.HandleFunc("/api/work/research",s.research);m.HandleFunc("/api/work/brief",s.brief);m.HandleFunc("/api/work/opportunities",s.opportunities);m.HandleFunc("/api/work/planner",s.planner);m.HandleFunc("/api/work/script-prep",s.scriptPrep);m.HandleFunc("/api/work/scripts",s.scripts);m.HandleFunc("/api/work/production",s.production);m.HandleFunc("/api/work/production/download",s.productionDownload);m.HandleFunc("/api/work/handoff",s.handoff);m.HandleFunc("/api/work/desk",s.desk);m.HandleFunc("/api/work/job",s.jobDetail);m.HandleFunc("/api/work/studio",s.studio);m.HandleFunc("/api/work/publication",s.publication);m.HandleFunc("/api/work/channel/import",s.channelImport);m.HandleFunc("/api/work/performance",s.performance);m.HandleFunc("/api/work/schedule",s.schedule);m.HandleFunc("/api/work/run-research",s.runResearch);m.HandleFunc("/api/work/daily",s.daily);m.HandleFunc("/api/work/channel",s.channel);m.HandleFunc("/api/work/knowledge",s.knowledge);m.HandleFunc("/api/work/recovery",s.recovery);m.HandleFunc("/api/work/bridge",s.bridgeStatus);m.HandleFunc("/api/work/autoupdate",s.autoUpdateStatus);m.HandleFunc("/api/work/remote",s.remoteInfo);go s.schedulerLoop();go s.bridgeLoop();go s.remoteLinkLoop();http.ListenAndServe(fmt.Sprintf("0.0.0.0:%d",p),m)}
const page=`<!doctype html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>HERMES WORK</title>
<style>
:root{--bg:#0b0f14;--panel:#141a22;--panel2:#0f151c;--line:#283140;--text:#edf2f7;--muted:#8d99a8;--ok:#4fd17b;--warn:#e4b54d;--bad:#ff6b6b;--accent:#5ea1ff}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font:14px system-ui,-apple-system,sans-serif}main{max-width:1180px;margin:auto;padding:18px}h1{font-size:24px;margin:4px 0}.sub{color:var(--muted);margin-bottom:18px}.nav{display:flex;gap:8px;overflow:auto;padding-bottom:8px;position:sticky;top:0;background:rgba(11,15,20,.94);backdrop-filter:blur(8px);z-index:3}.nav a{color:var(--text);text-decoration:none;background:#171e27;border:1px solid var(--line);padding:8px 11px;border-radius:999px;white-space:nowrap}.card{padding:16px;margin:12px 0;border:1px solid var(--line);background:var(--panel);border-radius:16px}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(170px,1fr));gap:10px}.box{background:var(--panel2);border:1px solid var(--line);border-radius:12px;padding:12px}.k{font-size:11px;color:var(--muted);text-transform:uppercase;letter-spacing:.06em}.v{font-size:18px;font-weight:750;margin-top:4px}.small{font-size:12px;color:var(--muted)}.ok{color:var(--ok)}.warn{color:var(--warn)}.bad{color:var(--bad)}.accent{color:var(--accent)}button,input{background:#1b2430;color:var(--text);border:1px solid var(--line);border-radius:10px;padding:10px 12px;margin:4px}button{cursor:pointer}.primary{background:#2468d8;border-color:#347ce8}.danger{background:#5a2528}pre{white-space:pre-wrap;background:#080c11;padding:12px;border-radius:10px;max-height:260px;overflow:auto}.pipeline{display:flex;flex-wrap:wrap;gap:7px}.step{padding:7px 9px;border-radius:9px;background:#0d131a;border:1px solid var(--line)}table{width:100%;border-collapse:collapse}td,th{text-align:left;padding:8px;border-bottom:1px solid var(--line)}@media(max-width:600px){main{padding:12px}.v{font-size:16px}}
</style></head>
<body><main>
<h1>HERMES WORK <span id="online" class="warn">CONNECTING</span></h1>
<div class="sub">Digital Work Control Center · Redmi 5A · modem-first worker</div>
<div class="nav">
<a href="#system">System</a><a href="#today">Today's Work</a><a href="#trends">Search & Trends</a><a href="#planner">Content Planner</a><a href="#desk">Production Desk</a><a href="#channel">My Channel</a><a href="#knowledge">Knowledge</a><a href="#automation">Automation</a><a href="#recovery">Recovery</a>
</div>

<section id="system" class="card"><h3>System / Overview</h3><div class="grid">
<div class="box"><div class="k">Modem / Tether</div><div id="tether" class="v">-</div></div>
<div class="box"><div class="k">Temperature</div><div id="temp" class="v">-</div></div>
<div class="box"><div class="k">Available RAM</div><div id="ram" class="v">-</div></div>
<div class="box"><div class="k">Worker</div><div id="worker" class="v">-</div></div>
<div class="box"><div class="k">Release</div><div id="release" class="v">-</div></div>
<div class="box"><div class="k">ChatGPT / Bridge</div><div id="bridge" class="v">-</div></div>
</div><p class="small">Modem priority lock: workload pauses when tether is down, temperature ≥44°C, RAM &lt;220 MB, Safe Mode, or worker pause.</p></section>

<section id="today" class="card"><h3>Today's Work</h3><div class="grid">
<div class="box"><div class="k">Sources checked</div><div id="sources" class="v">-</div></div>
<div class="box"><div class="k">Found</div><div id="found" class="v">-</div></div>
<div class="box"><div class="k">New</div><div id="newitems" class="v">-</div></div>
<div class="box"><div class="k">Duplicates</div><div id="dups" class="v">-</div></div>
<div class="box"><div class="k">Last research</div><div id="lastresearch" class="v small">-</div></div>
</div><button class="primary" id="research">RUN RESEARCH NOW</button><pre id="researchout">Ready.</pre></section>

<section id="trends" class="card"><h3>What People Need / Search</h3><div id="catgrid" class="grid"></div><p class="small">Data is based on collected research. No fake trend counts are displayed.</p></section>

<section class="card"><h3>Work Pipeline</h3><div class="pipeline">
<span class="step">Research</span><span class="step">Dedup</span><span class="step">Categorize</span><span class="step">Score</span><span class="step">Content Brief</span><span class="step">Production</span><span class="step">Published</span><span class="step">Performance</span>
</div><div id="components" class="grid" style="margin-top:12px"></div></section>

<section id="planner" class="card"><h3>Content Opportunities / Planner</h3><div class="grid">
<div class="box"><div class="k">Ideas Ready</div><div id="ideas" class="v">-</div></div>
<div class="box"><div class="k">Script Prep Ready</div><div id="scriptprepready" class="v">-</div></div><div class="box"><div class="k">Scripts Ready</div><div id="scriptsready" class="v">-</div></div>
<div class="box"><div class="k">Production Pack Ready</div><div id="productionready" class="v">-</div></div><div class="box"><div class="k">Production</div><div id="production" class="v">-</div></div><div class="box"><div class="k">Upload Ready</div><div id="uploadready" class="v">-</div></div>
<div class="box"><div class="k">Published</div><div id="published" class="v">-</div></div>
<div class="box"><div class="k">Next For Script</div><div id="nextscript" class="v small">-</div></div><div class="box"><div class="k">Final Script Topic</div><div id="finalscripttopic" class="v small">-</div></div><div class="box"><div class="k">Production Topic</div><div id="productiontopic" class="v small">-</div><a id="productiondownload" href="#" style="display:none;color:var(--accent)">DOWNLOAD PACK</a></div>
</div><div id="brief"></div><div id="plannerqueue"></div></section>


<section id="desk" class="card"><h3>Production Desk</h3>
<div class="grid">
<div class="box"><div class="k">Next Job</div><div id="desknext" class="v small">-</div></div>
<div class="box"><div class="k">Ready to Produce</div><div id="deskready" class="v">-</div></div>
<div class="box"><div class="k">Producing</div><div id="deskproducing" class="v">-</div></div>
<div class="box"><div class="k">Ready to Upload</div><div id="deskupload" class="v">-</div></div>
</div>
<div id="deskqueue" style="margin-top:12px"></div>
<div class="box" style="margin-top:12px">
<div class="k">Record Publication</div>
<input id="pubjob" placeholder="Job ID" readonly>
<select id="pubplatform" style="background:#1b2430;color:var(--text);border:1px solid var(--line);border-radius:10px;padding:10px 12px;margin:4px">
<option value="youtube">YouTube</option><option value="youtube_shorts">YouTube Shorts</option><option value="tiktok">TikTok</option><option value="instagram">Instagram</option><option value="facebook">Facebook</option><option value="other">Other</option>
</select>
<input id="puburl" placeholder="Published URL">
<input id="pubexternal" placeholder="Video / post ID (optional)">
<button class="primary" id="pubsubmit">RECORD PUBLISHED</button>
<div id="pubmsg" class="small">Select a READY_TO_UPLOAD job.</div>
</div>
<div class="box" style="margin-top:12px">
<div class="k">Real Performance Feedback</div>
<input id="perfjob" placeholder="Published Job ID" readonly>
<input id="perfviews" type="number" min="0" placeholder="Views">
<input id="perfret" type="number" min="0" max="100" step="0.1" placeholder="Retention %">
<input id="perfctr" type="number" min="0" max="100" step="0.1" placeholder="CTR %">
<input id="perfwatch" type="number" min="0" step="0.1" placeholder="Watch time (minutes)">
<button id="perfsubmit">SAVE REAL METRICS</button>
<div id="perfmsg" class="small">Only real channel data is accepted here.</div>
</div>
</section>

<section id="channel" class="card"><h3>My Channel</h3><div class="grid">
<div class="box"><div class="k">Connection</div><div id="channelstate" class="v">-</div></div><div class="box"><div class="k">Published Records</div><div id="publicationcount" class="v">-</div></div>
<div class="box"><div class="k">Views</div><div id="views" class="v">-</div></div>
<div class="box"><div class="k">Retention</div><div id="retention" class="v">-</div></div>
<div class="box"><div class="k">CTR / Engagement</div><div id="ctr" class="v">-</div></div>
<div class="box"><div class="k">Watch Time</div><div id="watchtime" class="v">-</div></div>
<div class="box"><div class="k">Best Topic</div><div id="besttopic" class="v">-</div></div>
</div><p class="small">Channel statistics remain NOT_CONNECTED until real channel performance data is supplied.</p></section>

<section id="knowledge" class="card"><h3>Memory / Knowledge</h3><div class="grid">
<div class="box"><div class="k">Research Items</div><div id="knowledgeitems" class="v">-</div></div>
<div class="box"><div class="k">Unique Keys</div><div id="uniques" class="v">-</div></div>
<div class="box"><div class="k">Successful</div><div id="successful" class="v">-</div></div>
<div class="box"><div class="k">Underperforming</div><div id="underperf" class="v">-</div></div>
<div class="box"><div class="k">Ideas Not Produced</div><div id="notproduced" class="v">-</div></div>
</div></section>

<section id="automation" class="card"><h3>Automation</h3><div class="grid">
<div class="box"><div class="k">Morning Research</div><div id="sched" class="v">-</div></div>
<div class="box"><div class="k">Guard</div><div id="guard" class="v">-</div></div>
<div class="box"><div class="k">Dedup</div><div class="v ok">READY</div></div>
<div class="box"><div class="k">Categorization</div><div class="v ok">READY</div></div>
<div class="box"><div class="k">Trend Scoring</div><div class="v ok">READY_V1</div></div>
<div class="box"><div class="k">Local Reasoning</div><div class="v warn">DEFERRED</div></div>
</div></section>

<section id="recovery" class="card"><h3>System & Recovery</h3><input id="token" type="password" placeholder="Admin token">
<button class="primary" id="update">UPDATE NOW</button><button id="backup">BACKUP</button><button class="danger" id="safe">SAFE MODE</button><button id="resume">RESUME</button><button id="rollback">ROLLBACK</button><button id="diag">DIAGNOSTICS</button>
<div class="grid" style="margin-top:10px"><div class="box"><div class="k">Current</div><div id="currel" class="v">-</div></div><div class="box"><div class="k">Last Good</div><div id="prevrel" class="v">-</div></div></div>
<pre id="out">Ready.</pre></section>

</main><script>
const $=x=>document.getElementById(x);
function h(){return {'X-Hermes-Token':$('token').value.trim(),'Content-Type':'application/json'}}
function txt(x){return (x===null||x===undefined||x==='')?'-':String(x)}
async function getj(p,o){let r=await fetch(p,o);if(!r.ok)throw new Error(await r.text());return await r.json()}
async function status(){
 try{
  let j=await getj('/api/work/status');
  $('online').textContent='● ONLINE';$('online').className='ok';
  $('tether').textContent=j.tether_state+' '+j.tether_ip;$('tether').className='v '+(j.tether_state==='UP'?'ok':'bad');
  $('temp').textContent=Number(j.temperature_c).toFixed(1)+' °C';$('ram').textContent=j.mem_available_mb+' MB';
  $('worker').textContent=j.safe_mode?'SAFE MODE':(j.worker_paused?'PAUSED':'READY');$('release').textContent=j.release;$('bridge').textContent=j.bridge_enabled?'CONNECTED':'OFF';
  $('components').innerHTML=Object.entries(j.components).map(([k,v])=>'<div class="box"><div class="k">'+k.replaceAll('_',' ')+'</div><div class="v '+(String(v).includes('NOT')||String(v).includes('DEFER')?'warn':'ok')+'">'+v+'</div></div>').join('');
 }catch(e){$('online').textContent='● OFFLINE';$('online').className='bad'}
}
async function researchData(){
 try{
  let d=await getj('/api/work/daily');let lr=d.last_run||{};
  $('sources').textContent=txt(lr.sources_checked);$('found').textContent=txt(lr.found);$('newitems').textContent=txt(lr.added);$('dups').textContent=txt(lr.duplicates);
  let r=await getj('/api/work/research');$('lastresearch').textContent=txt(r.last_research);
  let cats=d.categories||{};$('catgrid').innerHTML=Object.entries(cats).sort((a,b)=>b[1]-a[1]).map(([k,v])=>'<div class="box"><div class="k">'+k+'</div><div class="v">'+v+'</div></div>').join('')||'<div class="box"><div class="v warn">NO DATA YET</div></div>';
  let b=await getj('/api/work/brief');let ideas=b.ideas||[];$('ideas').textContent=ideas.length;$('brief').innerHTML=ideas.length?'<table><tr><th>Score</th><th>Category</th><th>Idea</th></tr>'+ideas.map(x=>'<tr><td>'+Math.round(x.Score||x.score||0)+'</td><td>'+txt(x.Category||x.category)+'</td><td>'+txt(x.Title||x.title)+'</td></tr>').join('')+'</table>':'<p class="warn">No ideas yet. Run research.</p>';
 }catch(e){$('researchout').textContent='Research data error: '+e}
}
async function plannerData(){
 try{
  let p=await getj('/api/work/planner');let cc=p.counts||{};
  $('scriptprepready').textContent=txt(cc.SCRIPT_PREP_READY??0);$('scriptsready').textContent=txt(cc.SCRIPT_READY??0);$('productionready').textContent=txt(cc.PRODUCTION_READY??0);$('production').textContent=txt(cc.PRODUCTION??0);$('uploadready').textContent=txt(cc.UPLOAD_READY??0);$('published').textContent=txt(cc.PUBLISHED??0);$('nextscript').textContent=txt(p.next_for_script);
  let a=p.items||[];$('plannerqueue').innerHTML=a.length?'<table><tr><th>#</th><th>Stage</th><th>Demand</th><th>Topic</th></tr>'+a.slice(0,10).map(x=>'<tr><td>'+txt(x.priority)+'</td><td>'+txt(x.stage)+'</td><td>'+txt(x.demand)+'</td><td>'+txt(x.title)+'</td></tr>').join('')+'</table>':'<p class="warn">Planner queue empty.</p>';
 }catch(e){$('plannerqueue').innerHTML='<p class="warn">Planner error: '+e+'</p>'}
}
async function scriptData(){
 try{let s=await getj('/api/work/scripts');$('finalscripttopic').textContent=txt(s.current_topic)}catch(e){}
}
async function productionData(){
 try{
  let p=await getj('/api/work/production');$('productiontopic').textContent=txt(p.current_topic);
  let a=$('productiondownload');if(p.download_endpoint){a.href=p.download_endpoint;a.style.display='inline'}else{a.style.display='none'}
 }catch(e){}
}

function escHtml(x){return String(x??'').replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;')}
async function handoffSet(id,state){
 try{
  let r=await fetch('/api/work/handoff',{method:'POST',headers:h(),body:JSON.stringify({id:id,state:state})});
  if(!r.ok)throw new Error(await r.text());
  await Promise.all([deskData(),plannerData()])
 }catch(e){$('pubmsg').textContent='Handoff error: '+e}
}
function selectDeskJob(id,title){
 $('pubjob').value=id;$('perfjob').value=id;$('pubmsg').textContent='Selected: '+title
}
async function deskData(){
 try{
  let d=await getj('/api/work/desk'),cc=d.counts||{},jobs=d.jobs||[];
  $('desknext').textContent=txt(d.next_job);$('deskready').textContent=txt(cc.READY_TO_PRODUCE??0);$('deskproducing').textContent=txt(cc.PRODUCING??0);$('deskupload').textContent=txt(cc.READY_TO_UPLOAD??0);
  let rows=jobs.slice(0,20).map(j=>{
   let id=encodeURIComponent(j.id),topic=encodeURIComponent(j.topic||'');
   let actions='<a href="'+escHtml(j.production_pack)+'" style="color:var(--accent)">PACK</a> ';
   if(j.handoff_state==='READY_TO_PRODUCE')actions+='<button class="deskact" data-id="'+id+'" data-state="PRODUCING">START</button>';
   if(j.handoff_state==='PRODUCING')actions+='<button class="deskact" data-id="'+id+'" data-state="READY_TO_UPLOAD">READY UPLOAD</button>';
   if(j.handoff_state==='READY_TO_UPLOAD'||j.handoff_state==='PUBLISHED')actions+='<button class="deskselect" data-id="'+id+'" data-topic="'+topic+'">SELECT</button>';
   return '<tr><td>'+escHtml(j.priority)+'</td><td>'+escHtml(j.handoff_state)+'</td><td>'+escHtml(j.topic)+'</td><td>'+actions+'</td></tr>'
  }).join('');
  $('deskqueue').innerHTML=rows?'<table><tr><th>#</th><th>State</th><th>Topic</th><th>Action</th></tr>'+rows+'</table>':'<p class="warn">No production jobs.</p>';
 }catch(e){$('deskqueue').innerHTML='<p class="warn">Production Desk error: '+escHtml(e)+'</p>'}
}
async function submitPublication(){
 let id=$('pubjob').value.trim(),url=$('puburl').value.trim(),platform=$('pubplatform').value,external_id=$('pubexternal').value.trim();
 if(!id){$('pubmsg').textContent='Select a READY_TO_UPLOAD job first.';return}
 try{
  let r=await fetch('/api/work/publication',{method:'POST',headers:h(),body:JSON.stringify({planner_id:id,platform:platform,url:url,external_id:external_id})});
  if(!r.ok)throw new Error(await r.text());
  $('pubmsg').textContent='Publication recorded.';$('perfjob').value=id;
  await Promise.all([deskData(),plannerData(),channel(),knowledge()])
 }catch(e){$('pubmsg').textContent='Publication error: '+e}
}
async function submitPerformance(){
 let id=$('perfjob').value.trim();if(!id){$('perfmsg').textContent='Select a published job first.';return}
 let body={planner_id:id,views:Number($('perfviews').value||0),retention_pct:Number($('perfret').value||0),ctr_pct:Number($('perfctr').value||0),watch_time_min:Number($('perfwatch').value||0),source:'dashboard_real_data'};
 try{
  let r=await fetch('/api/work/performance',{method:'POST',headers:h(),body:JSON.stringify(body)});
  if(!r.ok)throw new Error(await r.text());
  $('perfmsg').textContent='Real metrics saved to Feedback Engine.';
  await Promise.all([channel(),knowledge()])
 }catch(e){$('perfmsg').textContent='Metrics error: '+e}
}
async function channel(){
 try{let j=await getj('/api/work/channel');$('channelstate').textContent=j.state;$('publicationcount').textContent=txt(j.publications??0);$('views').textContent=txt(j.views||j.metrics?.views);$('retention').textContent=txt(j.retention||j.metrics?.retention);$('ctr').textContent=txt(j.ctr||j.metrics?.ctr);$('watchtime').textContent=txt(j.watch_time||j.metrics?.watch_time);$('besttopic').textContent=txt(j.best_topic||j.metrics?.best_topic)}catch(e){}
}
async function knowledge(){
 try{let j=await getj('/api/work/knowledge');$('knowledgeitems').textContent=j.research_items;$('uniques').textContent=j.unique_keys;$('successful').textContent=txt(j.successful);$('underperf').textContent=txt(j.underperforming);$('notproduced').textContent=txt(j.ideas_not_produced)}catch(e){}
}
async function automation(){
 try{let j=await getj('/api/work/schedule');$('sched').textContent=j.enabled?j.schedule:'OFF';$('guard').textContent=j.guard_ready?'READY':j.guard_reason;$('guard').className='v '+(j.guard_ready?'ok':'warn')}catch(e){}
}
async function recovery(){
 try{let j=await getj('/api/work/recovery');$('currel').textContent=txt(j.current);$('prevrel').textContent=txt(j.previous)}catch(e){}
}
async function refresh(){await status();await Promise.all([researchData(),plannerData(),scriptData(),productionData(),deskData(),channel(),knowledge(),automation(),recovery()])}
async function act(a){let r=await fetch('/api/work/action',{method:'POST',headers:h(),body:JSON.stringify({action:a})});$('out').textContent=await r.text();refresh()}
$('backup').onclick=()=>act('backup');$('safe').onclick=()=>act('safe_mode');$('resume').onclick=()=>act('resume');$('rollback').onclick=()=>act('rollback');
$('diag').onclick=async()=>{$('out').textContent=await(await fetch('/api/work/diagnostics',{headers:h()})).text()};
$('research').onclick=async()=>{$('researchout').textContent='Running research...';let r=await fetch('/api/work/run-research',{method:'POST',headers:h()});$('researchout').textContent=await r.text();researchData()};
$('update').onclick=async()=>{$('out').textContent='Updating...';let r=await fetch('/api/work/update',{method:'POST',headers:h()});$('out').textContent=await r.text()};$('pubsubmit').onclick=submitPublication;$('perfsubmit').onclick=submitPerformance;$('deskqueue').onclick=e=>{let b=e.target.closest('button');if(!b)return;let id=decodeURIComponent(b.dataset.id||'');if(b.classList.contains('deskact'))handoffSet(id,b.dataset.state);if(b.classList.contains('deskselect'))selectDeskJob(id,decodeURIComponent(b.dataset.topic||''))};
refresh();setInterval(status,5000);setInterval(()=>Promise.all([researchData(),plannerData(),scriptData(),productionData(),deskData(),automation(),recovery()]),30000);
</script></body></html>`