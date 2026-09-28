package com.djaeger.whatsappdiagnostic;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import android.graphics.Color;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private LinearLayout root;
    private TextView status, results;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);

    @Override public void onCreate(Bundle b) { super.onCreate(b); buildUi(); }

    private TextView text(String s,int sp){
        TextView v=new TextView(this); v.setText(s); v.setTextSize(sp);
        v.setTextColor(Color.DKGRAY); v.setPadding(16,10,16,10); return v;
    }
    private Button button(String s){ Button b=new Button(this); b.setText(s); return b; }

    private void buildUi(){
        ScrollView sc=new ScrollView(this);
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(18,16,18,28); sc.addView(root); setContentView(sc);

        TextView title=text("WhatsApp Network Diagnostic",24);
        title.setTextColor(Color.rgb(7,94,84)); root.addView(title);
        root.addView(text("Redmi Note 8 Pro • TES TANPA TELEPON / VIDEO CALL",15));
        status=text("Siap. Pilih salah satu tes.",16); root.addView(status);

        root.addView(section("1 • CHAT — jalur pesan / HTTPS"));
        Button c=button("TES CHAT"); root.addView(c); c.setOnClickListener(v->runChat());

        root.addView(section("2 • VOICE — kesiapan UDP real-time"));
        root.addView(text("Tes UDP/STUN, NAT, packet loss, RTT, dan jitter. Tidak menelepon WhatsApp.",14));
        Button vo=button("TES VOICE NETWORK"); root.addView(vo); vo.setOnClickListener(v->runVoice());

        root.addView(section("3 • VIDEO — kesiapan real-time + bandwidth"));
        root.addView(text("Tes UDP real-time + download + upload stabilitas. Tidak melakukan video call.",14));
        Button vi=button("TES VIDEO NETWORK"); root.addView(vi); vi.setOnClickListener(v->runVideo());

        root.addView(section("HASIL"));
        results=text("Belum ada hasil.",15); root.addView(results);

        Button all=button("JALANKAN SEMUA TES"); root.addView(all); all.setOnClickListener(v->runAll());
    }

    private TextView section(String s){ TextView v=text(s,18); v.setTextColor(Color.rgb(7,94,84)); v.setPadding(8,22,8,6); return v; }
    private void postStatus(String s){ runOnUiThread(()->status.setText(s)); }
    private void postResults(String s){ runOnUiThread(()->results.setText(s)); }
    private String ok(boolean b){ return b?"PASS":"FAIL"; }

    private void runChat(){
        postStatus("CHAT: menguji DNS + TCP/TLS + HTTPS...");
        pool.submit(()->{
            long t0=System.nanoTime();
            Check a=resolve("web.whatsapp.com"), b=resolve("whatsapp.net");
            HttpCheck h=https("https://web.whatsapp.com/");
            long ms=(System.nanoTime()-t0)/1_000_000;
            String out="CHAT\n"+
                "DNS web.whatsapp.com : "+ok(a.ok)+" ("+a.detail+")\n"+
                "DNS whatsapp.net      : "+ok(b.ok)+" ("+b.detail+")\n"+
                "HTTPS web.whatsapp    : "+ok(h.ok)+" ("+h.code+", "+h.ms+" ms)\n"+
                "Waktu total           : "+ms+" ms\n\n"+
                (a.ok&&b.ok&&h.ok?"HASIL CHAT: PASS\nJalur dasar DNS + HTTPS dapat dijangkau."
                    :"HASIL CHAT: FAIL\nAda kegagalan DNS/TLS/HTTPS.");
            postResults(out); postStatus(a.ok&&b.ok&&h.ok?"CHAT: PASS":"CHAT: FAIL");
        });
    }

    private void runVoice(){
        postStatus("VOICE: menguji UDP/STUN 24 sampel...");
        pool.submit(()->{
            StunResult r=stunTest(24,350);
            HttpCheck h=https("https://web.whatsapp.com/");
            String out=renderStun("VOICE",r)+"\nHTTPS baseline: "+ok(h.ok)+" ("+h.ms+" ms)\n\n"+voiceVerdict(r);
            postResults(out); postStatus(r.received>0?"VOICE NETWORK: "+r.grade:"VOICE NETWORK: UDP FAIL");
        });
    }

    private void runVideo(){
        postStatus("VIDEO: menguji UDP + download + upload...");
        pool.submit(()->{
            StunResult r=stunTest(20,300);
            Transfer d=download("https://postman-echo.com/bytes/1048576",1048576);
            Transfer u=upload("https://postman-echo.com/post",262144);
            String out=renderStun("VIDEO",r)+"\nDOWNLINK 1 MiB  : "+transfer(d)+"\nUPLINK 256 KiB   : "+transfer(u)+"\n\n"+videoVerdict(r,d,u);
            postResults(out); postStatus((r.received>0&&d.ok&&u.ok)?"VIDEO NETWORK: "+r.grade:"VIDEO NETWORK: CHECK");
        });
    }

    private void runAll(){
        postStatus("Menjalankan CHAT → VOICE → VIDEO...");
        pool.submit(()->{
            Check a=resolve("web.whatsapp.com"), b=resolve("whatsapp.net"); HttpCheck h=https("https://web.whatsapp.com/");
            StunResult r=stunTest(24,300);
            Transfer d=download("https://postman-echo.com/bytes/1048576",1048576);
            Transfer u=upload("https://postman-echo.com/post",262144);
            String out="WHATSAPP CONNECTION DIAGNOSTIC\n\n"+
                "CHAT\nDNS web.whatsapp.com : "+ok(a.ok)+"\nDNS whatsapp.net      : "+ok(b.ok)+"\nHTTPS baseline        : "+ok(h.ok)+" ("+h.ms+" ms)\n\n"+
                renderStun("VOICE / VIDEO UDP",r)+"\nDOWNLINK 1 MiB : "+transfer(d)+"\nUPLINK 256 KiB  : "+transfer(u)+"\n\nDIAGNOSIS\n"+allVerdict(a,b,h,r,d,u);
            postResults(out); postStatus("SEMUA TES SELESAI");
        });
    }

    private String voiceVerdict(StunResult r){
        if(r.received==0) return "HASIL VOICE NETWORK: FAIL\nUDP tidak mendapat respons. Fokus: NAT/firewall/VPN/operator.";
        if(r.lossPct>=5||r.jitterMs>80) return "HASIL VOICE NETWORK: TIDAK STABIL\nPacket loss/jitter tinggi.";
        if(r.lossPct>1||r.jitterMs>40) return "HASIL VOICE NETWORK: MARGINAL\nAda variasi real-time yang signifikan.";
        return "HASIL VOICE NETWORK: SIAP\nUDP real-time terlihat stabil.";
    }

    private String videoVerdict(StunResult r,Transfer d,Transfer u){
        if(r.received==0) return "HASIL VIDEO NETWORK: FAIL\nUDP tidak tersedia.";
        if(!d.ok||!u.ok) return "HASIL VIDEO NETWORK: CHECK\nTransfer uplink/downlink gagal.";
        if(r.lossPct>=3||r.jitterMs>60) return "HASIL VIDEO NETWORK: TIDAK STABIL\nJitter/loss tinggi.";
        return "HASIL VIDEO NETWORK: SIAP\nUDP + transfer dua arah lulus.";
    }

    private String allVerdict(Check a,Check b,HttpCheck h,StunResult r,Transfer d,Transfer u){
        StringBuilder s=new StringBuilder();
        s.append(a.ok&&b.ok&&h.ok?"CHAT: PASS\n":"CHAT: FAIL\n");
        s.append(r.received==0?"VOICE NETWORK: FAIL\n":(r.lossPct>=5||r.jitterMs>80?"VOICE NETWORK: TIDAK STABIL\n":"VOICE NETWORK: PASS\n"));
        s.append(d.ok&&u.ok&&r.received>0&&r.lossPct<3&&r.jitterMs<=60?"VIDEO NETWORK: PASS\n":"VIDEO NETWORK: CHECK\n");
        if(r.received==0) s.append("\nFokus: UDP/NAT/firewall/VPN/operator.");
        else if(r.lossPct>=5||r.jitterMs>80) s.append("\nFokus: packet loss/jitter real-time.");
        else if(!u.ok) s.append("\nFokus: uplink tidak stabil.");
        else if(!d.ok) s.append("\nFokus: downlink tidak stabil.");
        else s.append("\nJalur dasar dan real-time terlihat normal.");
        s.append("\n\nCatatan: ini tes sintetis karakteristik jaringan, bukan membaca trafik terenkripsi WhatsApp.");
        return s.toString();
    }

    private String renderStun(String title,StunResult r){
        return title+"\nUDP response : "+r.received+"/"+r.sent+"\n"+
            "Packet loss  : "+String.format(Locale.US,"%.1f%%",r.lossPct)+"\n"+
            "RTT avg      : "+String.format(Locale.US,"%.1f ms",r.avgMs)+"\n"+
            "RTT p95      : "+String.format(Locale.US,"%.1f ms",r.p95Ms)+"\n"+
            "Jitter       : "+String.format(Locale.US,"%.1f ms",r.jitterMs)+"\n"+
            "NAT mapping  : "+(r.mapped==null?"tidak terbaca":r.mapped)+"\n"+
            "Grade        : "+r.grade;
    }

    private String transfer(Transfer t){
        if(!t.ok) return "FAIL ("+t.error+")";
        return "PASS, "+String.format(Locale.US,"%.2f Mbps",t.mbps)+", "+t.ms+" ms";
    }

    private Check resolve(String host){
        try{long t=System.nanoTime();InetAddress a=InetAddress.getByName(host);return new Check(true,a.getHostAddress(),(System.nanoTime()-t)/1_000_000);}
        catch(Exception e){return new Check(false,e.getClass().getSimpleName(),-1);}
    }

    private HttpCheck https(String url){
        long t=System.nanoTime();
        try{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setRequestMethod("HEAD");c.setConnectTimeout(5000);c.setReadTimeout(5000);c.setInstanceFollowRedirects(false);int code=c.getResponseCode();c.disconnect();return new HttpCheck(code>0,code,(System.nanoTime()-t)/1_000_000);}
        catch(Exception e){return new HttpCheck(false,-1,(System.nanoTime()-t)/1_000_000);}
    }

    private Transfer download(String url,int limit){
        long t=System.nanoTime();int got=0;
        try{HttpURLConnection c=(HttpURLConnection)new URL(url+"?t="+System.nanoTime()).openConnection();c.setConnectTimeout(5000);c.setReadTimeout(10000);c.setUseCaches(false);
            InputStream in=c.getInputStream();byte[] buf=new byte[16384];while(got<limit){int n=in.read(buf,0,Math.min(buf.length,limit-got));if(n<0)break;got+=n;}in.close();c.disconnect();
            long ms=Math.max(1,(System.nanoTime()-t)/1_000_000);return new Transfer(got>=Math.min(limit,1024),ms,(got*8.0)/(ms/1000.0)/1000000.0,null);
        }catch(Exception e){return new Transfer(false,0,0,e.getClass().getSimpleName());}
    }

    private Transfer upload(String url,int size){
        long t=System.nanoTime();
        try{byte[] body=new byte[size];new Random().nextBytes(body);HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(5000);c.setReadTimeout(10000);c.setFixedLengthStreamingMode(body.length);c.setRequestProperty("Content-Type","application/octet-stream");
            OutputStream o=c.getOutputStream();o.write(body);o.flush();o.close();int code=c.getResponseCode();InputStream in=(code>=400?c.getErrorStream():c.getInputStream());if(in!=null)in.close();c.disconnect();
            long ms=Math.max(1,(System.nanoTime()-t)/1_000_000);return new Transfer(code>=200&&code<500,ms,(size*8.0)/(ms/1000.0)/1000000.0,String.valueOf(code));
        }catch(Exception e){return new Transfer(false,0,0,e.getClass().getSimpleName());}
    }

    private StunResult stunTest(int count,int pause){
        String[] hosts={"stun.l.google.com","stun1.l.google.com"};ArrayList<Double> vals=new ArrayList<>();
        String mapped=null;int sent=0,recv=0;Random rng=new Random();
        for(int i=0;i<count;i++){sent++;try{
            InetAddress addr=InetAddress.getByName(hosts[i%2]);byte[] tx=new byte[12];rng.nextBytes(tx);byte[] req=new byte[20];
            req[1]=1;req[4]=0x21;req[5]=0x12;req[6]=(byte)0xA4;req[7]=0x42;System.arraycopy(tx,0,req,8,12);
            DatagramSocket s=new DatagramSocket();s.setSoTimeout(1200);long t=System.nanoTime();s.send(new DatagramPacket(req,20,addr,19302));
            byte[] buf=new byte[2048];DatagramPacket p=new DatagramPacket(buf,buf.length);s.receive(p);long ms=(System.nanoTime()-t)/1000000;s.close();
            if(p.getLength()>=20&&matchesTx(buf,tx)){recv++;vals.add((double)ms);String m=parseMapped(buf,p.getLength());if(m!=null)mapped=m;}
        }catch(Exception ignored){}try{Thread.sleep(pause);}catch(InterruptedException e){Thread.currentThread().interrupt();break;}}
        double avg=0,jit=0,p95=0;if(!vals.isEmpty()){ArrayList<Double> sorted=new ArrayList<>(vals);Collections.sort(sorted);for(double x:sorted)avg+=x;avg/=sorted.size();for(int i=1;i<vals.size();i++)jit+=Math.abs(vals.get(i)-vals.get(i-1));if(vals.size()>1)jit/=vals.size()-1;int ix=Math.max(0,(int)Math.ceil(sorted.size()*0.95)-1);p95=sorted.get(ix);}
        double loss=sent==0?100:(sent-recv)*100.0/sent;String grade=recv==0?"FAIL":(loss>=5||jit>80?"POOR":(loss>1||jit>40?"MARGINAL":"GOOD"));
        return new StunResult(sent,recv,loss,avg,p95,jit,mapped,grade);
    }

    private boolean matchesTx(byte[] b,byte[] tx){if(b.length<20)return false;for(int i=0;i<12;i++)if(b[8+i]!=tx[i])return false;return true;}

    private String parseMapped(byte[] b,int len){
        int pos=20;while(pos+4<=len){int type=((b[pos]&255)<<8)|(b[pos+1]&255);int alen=((b[pos+2]&255)<<8)|(b[pos+3]&255);pos+=4;if(pos+alen>len)break;
            if((type==0x0020||type==1)&&alen>=8&&(b[pos+1]&255)==1){int port=((b[pos+2]&255)<<8)|(b[pos+3]&255);int a=b[pos+4]&255,c=b[pos+5]&255,d=b[pos+6]&255,e=b[pos+7]&255;
                if(type==0x0020){port^=0x2112;a^=0x21;c^=0x12;d^=0xA4;e^=0x42;}return a+"."+c+"."+d+"."+e+":"+port;}
            pos+=((alen+3)/4)*4;}return null;
    }

    static class Check{boolean ok;String detail;long ms;Check(boolean o,String d,long m){ok=o;detail=d;ms=m;}}
    static class HttpCheck{boolean ok;int code;long ms;HttpCheck(boolean o,int c,long m){ok=o;code=c;ms=m;}}
    static class Transfer{boolean ok;long ms;double mbps;String error;Transfer(boolean o,long m,double x,String e){ok=o;ms=m;mbps=x;error=e;}}
    static class StunResult{int sent,received;double lossPct,avgMs,p95Ms,jitterMs;String mapped,grade;
        StunResult(int s,int r,double l,double a,double p,double j,String m,String g){sent=s;received=r;lossPct=l;avgMs=a;p95Ms=p;jitterMs=j;mapped=m;grade=g;}}
}
