package com.djaeger.whatsappdiagnostic;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    LinearLayout root; TextView summary; TextView log; ExecutorService pool=Executors.newFixedThreadPool(4);
    SharedPreferences p;

    @Override public void onCreate(Bundle b){super.onCreate(b); p=getSharedPreferences("diag",MODE_PRIVATE); build();}

    TextView tv(String s,int sp){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(Color.DKGRAY);v.setPadding(18,12,18,12);return v;}
    Button btn(String s){Button b=new Button(this);b.setText(s);return b;}

    void build(){
        ScrollView sc=new ScrollView(this); root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(22,18,22,30);sc.addView(root);setContentView(sc);
        TextView title=tv("WhatsApp Connection Diagnostic",24);title.setTextColor(Color.rgb(7,94,84));root.addView(title);
        root.addView(tv("Redmi Note 8 Pro • Chat / Voice / Video",16));
        summary=tv("Siap. Jalankan tahap 1.",16);root.addView(summary);

        root.addView(section("1 • CHAT"));
        Button chat=btn("JALANKAN TES CHAT");root.addView(chat);chat.setOnClickListener(v->chatTest());
        Button openChat=btn("BUKA WHATSAPP");root.addView(openChat);openChat.setOnClickListener(v->openWA());

        root.addView(section("2 • VOICE CALL"));
        Button voice=btn("MULAI UKURAN VOICE");root.addView(voice);voice.setOnClickListener(v->liveTest("VOICE"));
        Button voiceOk=btn("VOICE BERHASIL");root.addView(voiceOk);voiceOk.setOnClickListener(v->mark("VOICE",true));
        Button voiceFail=btn("VOICE GAGAL / MENYAMBUNG TERUS");root.addView(voiceFail);voiceFail.setOnClickListener(v->mark("VOICE",false));

        root.addView(section("3 • VIDEO CALL"));
        Button video=btn("MULAI UKURAN VIDEO");root.addView(video);video.setOnClickListener(v->liveTest("VIDEO"));
        Button videoOk=btn("VIDEO BERHASIL");root.addView(videoOk);videoOk.setOnClickListener(v->mark("VIDEO",true));
        Button videoFail=btn("VIDEO GAGAL / PUTUS");root.addView(videoFail);videoFail.setOnClickListener(v->mark("VIDEO",false));

        root.addView(section("HASIL DIAGNOSIS"));
        log=tv("Belum ada hasil.",15);root.addView(log);
        Button refresh=btn("REFRESH HASIL");root.addView(refresh);refresh.setOnClickListener(v->render());
        render();
    }

    TextView section(String s){TextView x=tv(s,18);x.setTextColor(Color.rgb(7,94,84));x.setPadding(8,24,8,8);return x;}

    void chatTest(){
        summary.setText("Chat: sedang menguji DNS + HTTPS WhatsApp...");
        pool.submit(()->{
            long a=System.currentTimeMillis(); boolean dns=false,http=false;String err="";
            try{InetAddress.getByName("web.whatsapp.com");dns=true;}catch(Exception e){err=e.toString();}
            try{HttpURLConnection c=(HttpURLConnection)new URL("https://web.whatsapp.com/").openConnection();c.setConnectTimeout(5000);c.setReadTimeout(5000);c.setRequestMethod("HEAD");int code=c.getResponseCode();http=code>0;c.disconnect();}catch(Exception e){err=e.toString();}
            boolean finalDns=dns, finalHttp=http;runOnUiThread(()->{
                p.edit().putBoolean("chat_dns",finalDns).putBoolean("chat_http",finalHttp).apply();
                summary.setText("CHAT dasar: "+(finalDns&&finalHttp?"PASS":"FAIL")+" • "+(System.currentTimeMillis()-a)+" ms");
                render();
            });
        });
    }

    void liveTest(String kind){
        Intent s=new Intent(this,MonitorService.class);
        if(Build.VERSION.SDK_INT>=26)startForegroundService(s);else startService(s);
        summary.setText(kind+" • pengukuran aktif. Sekarang buka WhatsApp dan lakukan panggilan "+kind.toLowerCase()+". Setelah selesai, kembali ke app lalu tekan hasilnya.");
        openWA();
    }

    void mark(String kind,boolean ok){
        p.edit().putBoolean(kind+"_ok",ok).apply(); stopService(new Intent(this,MonitorService.class)); render();
    }

    void openWA(){
        Intent i=getPackageManager().getLaunchIntentForPackage("com.whatsapp");
        if(i==null){Toast.makeText(this,"WhatsApp tidak ditemukan.",Toast.LENGTH_LONG).show();return;}
        startActivity(i);
    }

    void render(){
        boolean cd=p.getBoolean("chat_dns",false), ch=p.getBoolean("chat_http",false);
        StringBuilder s=new StringBuilder();
        s.append("CHAT: ").append(cd&&ch?"PASS":"BELUM / FAIL").append("\n");
        if(p.contains("VOICE_ok")) s.append("VOICE: ").append(p.getBoolean("VOICE_ok",false)?"PASS":"FAIL").append("\n"); else s.append("VOICE: BELUM DIUJI\n");
        if(p.contains("VIDEO_ok")) s.append("VIDEO: ").append(p.getBoolean("VIDEO_ok",false)?"PASS":"FAIL").append("\n"); else s.append("VIDEO: BELUM DIUJI\n");
        long ms=p.getLong("live_ms",-1); float jit=p.getFloat("jitter",-1); int fail=p.getInt("fail",0);
        s.append("\nJaringan live terakhir: ").append(ms>=0?ms+" ms":"gagal").append(" • jitter ~").append(jit>=0?String.format(Locale.US,"%.1f ms",jit):"-").append(" • gagal ").append(fail);
        s.append("\n\nInterpretasi:\n");
        if(fail>=3 || jit>100) s.append("⚠️ Real-time tidak stabil: fokus packet loss/jitter/NAT/VPN/proxy/operator.\n");
        else if(ms>0) s.append("✅ Jalur HTTPS responsif; bila WhatsApp call tetap gagal, masalah lebih spesifik ke jalur real-time/konfigurasi WhatsApp.\n");
        else s.append("Belum ada cukup sampel live.");
        log.setText(s.toString());
    }
}
