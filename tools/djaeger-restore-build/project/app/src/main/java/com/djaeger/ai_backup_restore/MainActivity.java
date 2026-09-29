package com.djaeger.ai_backup_restore;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;

public class MainActivity extends Activity {
    private static final int PICK_BACKUP = 4101;
    private TextView selectedText, statusText;
    private Button restoreButton;
    private File stagedBackup;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28,24,28,24);
        root.setBackgroundColor(Color.rgb(11,15,20));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.djaeger_logo);
        head.addView(icon, new LinearLayout.LayoutParams(72,72));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(16,0,0,0);
        titles.addView(text("DJAEGER AI RESTORE",22,Color.WHITE));
        titles.addView(text("Pulihkan /data/adb/djaeger_ai dari backup",14,Color.LTGRAY));
        head.addView(titles, new LinearLayout.LayoutParams(0,-2,1));
        root.addView(head);

        TextView info = text("Pilih file backup .tar.gz secara manual. File tidak ditanam ke APK.",14,Color.rgb(190,200,210));
        info.setPadding(0,20,0,16);
        root.addView(info);

        Button choose = new Button(this);
        choose.setText("PILIH FILE BACKUP");
        choose.setOnClickListener(v -> openPicker());
        root.addView(choose, new LinearLayout.LayoutParams(-1,-2));

        selectedText = text("Belum ada file dipilih.",15,Color.rgb(200,220,230));
        selectedText.setPadding(0,12,0,12);
        root.addView(selectedText);

        restoreButton = new Button(this);
        restoreButton.setText("RESTORE SEKARANG");
        restoreButton.setEnabled(false);
        restoreButton.setOnClickListener(v -> restoreSelected());
        root.addView(restoreButton, new LinearLayout.LayoutParams(-1,-2));

        TextView note = text("Restore membuat rollback backup terlebih dahulu. Reboot tidak diperlukan.",13,Color.rgb(160,170,180));
        note.setPadding(0,12,0,12);
        root.addView(note);

        statusText = text("STATUS: MENUNGGU FILE",13,Color.rgb(140,235,255));
        statusText.setGravity(Gravity.TOP);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.rgb(7,10,14));
        sv.setPadding(12,12,12,12);
        sv.addView(statusText);
        root.addView(sv, new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }

    private TextView text(String s,float size,int color){
        TextView t=new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private void openPicker(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/gzip","application/x-gzip","application/octet-stream","*/*"});
        startActivityForResult(i,PICK_BACKUP);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=PICK_BACKUP || resultCode!=RESULT_OK || data==null || data.getData()==null) return;
        Uri uri=data.getData();
        try{
            String name=displayName(uri);
            if(name==null || !(name.endsWith(".tar.gz") || name.endsWith(".tgz")))
                throw new IllegalArgumentException("File harus berupa .tar.gz atau .tgz");
            File out=new File(getCacheDir(),"selected_backup.tar.gz");
            copyUri(uri,out);
            stagedBackup=out;
            selectedText.setText("DIPILIH:\n"+name+"\nUkuran: "+out.length()+" byte");
            restoreButton.setEnabled(true);
            statusText.setText("STATUS: FILE DIPILIH\nSilakan tekan RESTORE SEKARANG.");
        }catch(Exception e){
            stagedBackup=null;
            restoreButton.setEnabled(false);
            statusText.setText("STATUS: GAGAL MEMBACA FILE\n"+e.getMessage());
            Toast.makeText(this,"File tidak dapat digunakan.",Toast.LENGTH_LONG).show();
        }
    }

    private String displayName(Uri uri){
        Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null);
        if(c!=null){
            try{
                if(c.moveToFirst()) return c.getString(0);
            }finally{ c.close(); }
        }
        return uri.getLastPathSegment();
    }

    private void copyUri(Uri uri,File out)throws Exception{
        try(InputStream in=getContentResolver().openInputStream(uri);
            FileOutputStream fos=new FileOutputStream(out,false)){
            if(in==null) throw new IllegalStateException("Tidak bisa membuka URI");
            byte[] buf=new byte[64*1024];
            int n;
            long total=0;
            while((n=in.read(buf))!=-1){
                fos.write(buf,0,n);
                total+=n;
            }
            if(total<32) throw new IllegalStateException("File terlalu kecil");
        }
    }

    private void restoreSelected(){
        if(stagedBackup==null || !stagedBackup.isFile()){
            statusText.setText("STATUS: PILIH FILE TERLEBIH DAHULU");
            return;
        }
        restoreButton.setEnabled(false);
        statusText.setText("STATUS: MEMINTA AKSES ROOT...\nKernelSU/Magisk akan ditampilkan bila diperlukan.");
        new Thread(() -> {
            try{
                File script=new File(getCacheDir(),"restore.sh");
                copyAsset("restore.sh",script);
                String cmd="chmod 700 "+q(script.getAbsolutePath())+
                        " && sh "+q(script.getAbsolutePath())+
                        " "+q(stagedBackup.getAbsolutePath());
                Process p=new ProcessBuilder("su","-c",cmd)
                        .redirectErrorStream(true).start();
                BufferedReader br=new BufferedReader(new InputStreamReader(p.getInputStream()));
                StringBuilder out=new StringBuilder();
                String line;
                while((line=br.readLine())!=null){
                    out.append(line).append('\n');
                    final String view=out.toString();
                    runOnUiThread(() -> statusText.setText(view));
                }
                int rc=p.waitFor();
                out.append("\nPROCESS_EXIT=").append(rc).append('\n');
                runOnUiThread(() -> {
                    statusText.setText(out.toString());
                    restoreButton.setEnabled(rc!=0);
                    Toast.makeText(this,rc==0?"Restore selesai.":"Restore gagal.",Toast.LENGTH_LONG).show();
                });
            }catch(Exception e){
                runOnUiThread(() -> {
                    statusText.setText("STATUS: EXCEPTION\n"+e);
                    restoreButton.setEnabled(true);
                });
            }
        }).start();
    }

    private void copyAsset(String name,File out)throws Exception{
        try(InputStream in=getAssets().open(name);
            FileOutputStream fos=new FileOutputStream(out,false)){
            byte[] buf=new byte[32*1024];
            int n;
            while((n=in.read(buf))!=-1) fos.write(buf,0,n);
        }
        out.setReadable(true,true);
        out.setWritable(true,true);
        out.setExecutable(true,true);
    }

    private String q(String s){
        return "'"+s.replace("'","'\\''")+"'";
    }
}
