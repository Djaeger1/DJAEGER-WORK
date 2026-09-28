package com.djaeger.whatsappdiagnostic;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class MonitorService extends Service {
    private static final String CH="wa_diag";
    private volatile boolean running;
    private Thread worker;

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26) nm.createNotificationChannel(new NotificationChannel(CH,"WhatsApp diagnostic",NotificationManager.IMPORTANCE_LOW));
        startForeground(77, new Notification.Builder(this, CH)
            .setContentTitle("WhatsApp diagnostic")
            .setContentText("Pengukuran real-time aktif")
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setOngoing(true).build());
    }
    @Override public int onStartCommand(Intent i,int flags,int id){
        if(worker==null){ running=true; worker=new Thread(this::loop); worker.start(); }
        return START_NOT_STICKY;
    }
    private void loop(){
        long lastRx=TrafficStats.getTotalRxBytes(), lastTx=TrafficStats.getTotalTxBytes();
        ArrayList<Long> samples=new ArrayList<>();
        while(running){
            long t=System.nanoTime();
            boolean ok=false; long ms=-1;
            try {
                HttpURLConnection c=(HttpURLConnection)new URL("https://web.whatsapp.com/").openConnection();
                c.setConnectTimeout(4000); c.setReadTimeout(4000); c.setRequestMethod("HEAD");
                c.setInstanceFollowRedirects(false);
                long s=System.nanoTime(); c.getResponseCode(); ms=(System.nanoTime()-s)/1_000_000L; ok=true; c.disconnect();
            } catch(Exception ignored){}
            long rx=TrafficStats.getTotalRxBytes(), tx=TrafficStats.getTotalTxBytes();
            long dr=rx-lastRx, dt=tx-lastTx; lastRx=rx; lastTx=tx;
            samples.add(ok?ms:-1L); if(samples.size()>30) samples.remove(0);
            double avg=0; int n=0, fail=0;
            for(long v:samples){ if(v>=0){avg+=v;n++;} else fail++; }
            if(n>0) avg/=n;
            double jitter=0; long prev=-1;
            for(long v:samples) if(v>=0){ if(prev>=0) jitter+=Math.abs(v-prev); prev=v; }
            if(n>1) jitter/=n-1;
            getSharedPreferences("diag",MODE_PRIVATE).edit()
                .putBoolean("live_ok",ok).putLong("live_ms",ms)
                .putLong("rx_delta",dr).putLong("tx_delta",dt)
                .putFloat("jitter",(float)jitter).putInt("samples",samples.size())
                .putInt("fail",fail).putLong("updated",System.currentTimeMillis()).apply();
            try{Thread.sleep(1000);}catch(InterruptedException e){Thread.currentThread().interrupt();}
        }
    }
    @Override public void onDestroy(){ running=false; if(worker!=null) worker.interrupt(); super.onDestroy(); }
    @Override public android.os.IBinder onBind(Intent i){ return null; }
}
