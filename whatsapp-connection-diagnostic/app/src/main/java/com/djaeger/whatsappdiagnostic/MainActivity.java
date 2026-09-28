package com.djaeger.whatsappdiagnostic;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final int TEAL = Color.rgb(7,94,84);
    private final int GREEN = Color.rgb(37,211,102);
    private final int DARK = Color.rgb(30,41,44);
    private final int MUTED = Color.rgb(92,108,111);
    private LinearLayout root;
    private TextView status;
    private TextView chatResult, voiceResult, videoResult, allResult;
    private EditText chatInput, voiceInput, videoInput, allInput;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(TEAL);
        buildUi();
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }

    private TextView label(String s, float sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s); v.setTextSize(sp); v.setTextColor(color);
        v.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        v.setPadding(dp(2), dp(4), dp(2), dp(4));
        return v;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s); b.setTextSize(13); b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(TEAL); bg.setCornerRadius(dp(12));
        b.setBackground(bg);
        b.setPadding(dp(10), 0, dp(10), 0);
        return b;
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint); e.setTextSize(13); e.setTextColor(DARK); e.setHintTextColor(MUTED);
        e.setGravity(Gravity.TOP|Gravity.START); e.setMinLines(2); e.setMaxLines(7);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE); bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), Color.rgb(215,225,225));
        e.setBackground(bg);
        return e;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(0, dp(6), 0, dp(2));
        return r;
    }

    private void addButton(LinearLayout r, Button b, int weight) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(46), weight);
        p.setMargins(dp(4),0,dp(4),0); r.addView(b,p);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE); bg.setCornerRadius(dp(18)); bg.setStroke(dp(1), Color.rgb(225,232,232));
        c.setBackground(bg);
        c.setElevation(dp(3));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(8), 0, dp(8)); c.setLayoutParams(p);
        return c;
    }

    private TextView resultBox(String initial) {
        TextView t = label(initial, 13, DARK, false);
        t.setTextIsSelectable(true); t.setBackgroundColor(Color.rgb(248,250,250));
        t.setPadding(dp(12), dp(12), dp(12), dp(12));
        return t;
    }

    private void buildUi() {
        ScrollView sc = new ScrollView(this);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(22)); sc.addView(root); setContentView(sc);

        LinearLayout header = card();
        LinearLayout titleRow = new LinearLayout(this); titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = label("◉", 34, GREEN, true);
        titleRow.addView(logo, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout titleCol = new LinearLayout(this); titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.addView(label("WhatsApp Connection Diagnostic", 22, TEAL, true));
        titleCol.addView(label("DJAEGER • Passive Network Test", 13, MUTED, false));
        titleRow.addView(titleCol, new LinearLayout.LayoutParams(0,-2,1));
        header.addView(titleRow);
        header.addView(label("Redmi Note 8 Pro • TANPA TELEPON / VIDEO CALL", 13, DARK, true));
        status = label("Siap. Pilih tes atau jalankan semuanya.", 14, TEAL, true);
        header.addView(status);
        root.addView(header);

        LinearLayout chat = card();
        chat.addView(label("1  CHAT", 19, TEAL, true));
        chat.addView(label("DNS + HTTPS untuk jalur pesan.", 13, MUTED, false));
        Button chatTest = button("▶ TES CHAT"); chat.addView(chatTest);
        chatResult = resultBox("Belum ada hasil CHAT.");
        chat.addView(chatResult);
        chatInput = input("Tempel catatan/hasil CHAT di sini…"); chat.addView(chatInput);
        LinearLayout cr = row();
        Button chatCopy = button("⧉ SALIN"); Button chatPaste = button("↧ TEMPEL");
        addButton(cr, chatCopy,1); addButton(cr, chatPaste,1); chat.addView(cr);
        chatTest.setOnClickListener(v->runChat());
        chatCopy.setOnClickListener(v->copy("CHAT", chatResult.getText().toString()));
        chatPaste.setOnClickListener(v->paste(chatInput));
        root.addView(chat);

        LinearLayout voice = card();
        voice.addView(label("2  VOICE NETWORK", 19, TEAL, true));
        voice.addView(label("UDP/STUN, NAT, packet loss, RTT, dan jitter. Tidak menelepon.", 13, MUTED, false));
        Button voiceTest = button("▶ TES VOICE NETWORK"); voice.addView(voiceTest);
        voiceResult = resultBox("Belum ada hasil VOICE.");
        voice.addView(voiceResult);
        voiceInput = input("Tempel catatan/hasil VOICE di sini…"); voice.addView(voiceInput);
        LinearLayout vr = row();
        Button voiceCopy = button("⧉ SALIN"); Button voicePaste = button("↧ TEMPEL");
        addButton(vr, voiceCopy,1); addButton(vr, voicePaste,1); voice.addView(vr);
        voiceTest.setOnClickListener(v->runVoice());
        voiceCopy.setOnClickListener(v->copy("VOICE", voiceResult.getText().toString()));
        voicePaste.setOnClickListener(v->paste(voiceInput));
        root.addView(voice);

        LinearLayout video = card();
        video.addView(label("3  VIDEO NETWORK", 19, TEAL, true));
        video.addView(label("UDP real-time + download + upload. Tidak melakukan video call.", 13, MUTED, false));
        Button videoTest = button("▶ TES VIDEO NETWORK"); video.addView(videoTest);
        videoResult = resultBox("Belum ada hasil VIDEO.");
        video.addView(videoResult);
        videoInput = input("Tempel catatan/hasil VIDEO di sini…"); video.addView(videoInput);
        LinearLayout vir = row();
        Button videoCopy = button("⧉ SALIN"); Button videoPaste = button("↧ TEMPEL");
        addButton(vir, videoCopy,1); addButton(vir, videoPaste,1); video.addView(vir);
        videoTest.setOnClickListener(v->runVideo());
        videoCopy.setOnClickListener(v->copy("VIDEO", videoResult.getText().toString()));
        videoPaste.setOnClickListener(v->paste(videoInput));
        root.addView(video);

        LinearLayout all = card();
        all.addView(label("HASIL GABUNGAN", 19, TEAL, true));
        all.addView(label("Satu tempat untuk seluruh diagnosis.", 13, MUTED, false));
        Button allTest = button("▶ JALANKAN SEMUA TES"); all.addView(allTest);
        allResult = resultBox("Belum ada hasil gabungan.");
        all.addView(allResult);
        allInput = input("Tempel laporan lengkap / hasil dari aplikasi lain…"); all.addView(allInput);
        LinearLayout ar = row();
        Button allCopy = button("⧉ SALIN SEMUA"); Button allPaste = button("↧ TEMPEL SEMUA");
        addButton(ar, allCopy,1); addButton(ar, allPaste,1); all.addView(ar);
        allTest.setOnClickListener(v->runAll());
        allCopy.setOnClickListener(v->copy("HASIL SEMUA", buildAllCopy()));
        allPaste.setOnClickListener(v->paste(allInput));
        root.addView(all);

        TextView note = label("Catatan: tes ini mengukur karakteristik jaringan yang dibutuhkan WhatsApp; aplikasi tidak membaca trafik WhatsApp yang terenkripsi.", 12, MUTED, false);
        note.setPadding(dp(8), dp(12), dp(8), 0); root.addView(note);
    }

    private void toast(String s) { runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_SHORT).show()); }
    private void copy(String label, String value) {
        ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label,value));
        toast("Hasil "+label+" disalin.");
    }
    private void paste(EditText e) {
        ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount()>0) {
            CharSequence x=cm.getPrimaryClip().getItemAt(0).coerceToText(this); e.setText(x); e.setSelection(e.length());
            toast("Teks ditempel.");
        } else toast("Clipboard kosong.");
    }

    private void setResult(TextView v, String s) { runOnUiThread(()->v.setText(s)); }
    private void postStatus(String s){ runOnUiThread(()->status.setText(s)); }
    private String ok(boolean b){ return b?"PASS":"FAIL"; }

    private void runChat(){
        postStatus("CHAT: menguji DNS + HTTPS…");
        pool.submit(()->{
            long t0=System.nanoTime();
            Check a=resolve("web.whatsapp.com"), b=resolve("whatsapp.net");
            HttpCheck h=https("https://web.whatsapp.com/");
            long ms=(System.nanoTime()-t0)/1_000_000;
            String out="CHAT\nDNS web.whatsapp.com : "+ok(a.ok)+" ("+a.detail+")\nDNS whatsapp.net      : "+ok(b.ok)+" ("+b.detail+")\nHTTPS web.whatsapp    : "+ok(h.ok)+" ("+h.code+", "+h.ms+" ms)\nWaktu total           : "+ms+" ms\n\n"+(a.ok&&b.ok&&h.ok?"HASIL CHAT: PASS\nJalur dasar DNS + HTTPS dapat dijangkau.":"HASIL CHAT: FAIL\nAda kegagalan DNS/TLS/HTTPS.");
            setResult(chatResult,out); postStatus(a.ok&&b.ok&&h.ok?"CHAT: PASS":"CHAT: FAIL");
        });
    }

    private void runVoice(){
        postStatus("VOICE: menguji UDP/STUN 24 sampel…");
        pool.submit(()->{
            StunResult r=stunTest(24,350); HttpCheck h=https("https://web.whatsapp.com/");
            String out=renderStun("VOICE",r)+"\nHTTPS baseline: "+ok(h.ok)+" ("+h.ms+" ms)\n\n"+voiceVerdict(r);
            setResult(voiceResult,out); postStatus(r.received>0?"VOICE NETWORK: "+r.grade:"VOICE NETWORK: UDP FAIL");
        });
    }

    private void runVideo(){
        postStatus("VIDEO: menguji UDP + download + upload…");
        pool.submit(()->{
            StunResult r=stunTest(20,300);
            Transfer d=download("https://postman-echo.com/bytes/1048576",1048576);
            Transfer u=upload("https://postman-echo.com/post",262144);
            String out=renderStun("VIDEO",r)+"\nDOWNLINK 1 MiB  : "+transfer(d)+"\nUPLINK 256 KiB   : "+transfer(u)+"\n\n"+videoVerdict(r,d,u);
            setResult(videoResult,out); postStatus((r.received>0&&d.ok&&u.ok)?"VIDEO NETWORK: "+r.grade:"VIDEO NETWORK: CHECK");
        });
    }

    private void runAll(){
        postStatus("Menjalankan CHAT → VOICE → VIDEO…");
        pool.submit(()->{
            Check a=resolve("web.whatsapp.com"), b=resolve("whatsapp.net"); HttpCheck h=https("https://web.whatsapp.com/");
            StunResult r=stunTest(24,300);
            Transfer d=download("https://postman-echo.com/bytes/1048576",1048576);
            Transfer u=upload("https://postman-echo.com/post",262144);
            String chat="CHAT\nDNS web.whatsapp.com : "+ok(a.ok)+"\nDNS whatsapp.net      : "+ok(b.ok)+"\nHTTPS baseline        : "+ok(h.ok)+" ("+h.ms+" ms)\n\nHASIL CHAT: "+(a.ok&&b.ok&&h.ok?"PASS":"FAIL");
            String voice=renderStun("VOICE",r)+"\nHTTPS baseline: "+ok(h.ok)+" ("+h.ms+" ms)\n\n"+voiceVerdict(r);
            String video=renderStun("VIDEO",r)+"\nDOWNLINK 1 MiB  : "+transfer(d)+"\nUPLINK 256 KiB   : "+transfer(u)+"\n\n"+videoVerdict(r,d,u);
            String all="WHATSAPP CONNECTION DIAGNOSTIC\n\n"+chat+"\n\n"+voice+"\n\n"+video+"\n\nDIAGNOSIS\n"+allVerdict(a,b,h,r,d,u);
            setResult(chatResult,chat); setResult(voiceResult,voice); setResult(videoResult,video); setResult(allResult,all);
            postStatus("SEMUA TES SELESAI");
        });
    }

    private String buildAllCopy(){
        return "WHATSAPP CONNECTION DIAGNOSTIC\n\nCHAT\n"+chatResult.getText()+"\n\nVOICE\n"+voiceResult.getText()+"\n\nVIDEO\n"+videoResult.getText()+"\n\n"+allResult.getText();
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
        return title+"\nUDP response : "+r.received+"/"+r.sent+"\nPacket loss  : "+String.format(Locale.US,"%.1f%%",r.lossPct)+"\nRTT avg      : "+String.format(Locale.US,"%.1f ms",r.avgMs)+"\nRTT p95      : "+String.format(Locale.US,"%.1f ms",r.p95Ms)+"\nJitter       : "+String.format(Locale.US,"%.1f ms",r.jitterMs)+"\nNAT mapping  : "+(r.mapped==null?"tidak terbaca":r.mapped)+"\nGrade        : "+r.grade;
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
