package com.hermes.workinstaller;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String DEFAULT_URL = "http://192.168.42.129:8766";
    private static final String BOOTSTRAP_URL = "http://192.168.42.129:8765";
    private static final String CHANNEL_URL = "https://raw.githubusercontent.com/Djaeger1/DJAEGER-Control-Center/hermes-work-release-channel/hermes-work-runtime/channel.json";
    private static final String PREFS = "hermes_work_installer";
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private EditText url, token;
    private TextView status, log;
    private Button install;
    private ScrollView scroll;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.setBackgroundColor(Color.rgb(10, 13, 16));

        TextView title = new TextView(this);
        title.setText("HERMES WORK INSTALLER");
        title.setTextColor(Color.WHITE);
        title.setTextSize(21);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("v1.1.0 · installer + updater dashboard HERMES WORK");
        sub.setTextColor(Color.LTGRAY);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(4), 0, dp(16));
        root.addView(sub);

        url = field("Bootstrap URL", getPreferences(MODE_PRIVATE).getString("url", DEFAULT_URL), false);
        token = field("ADMIN TOKEN", getPreferences(MODE_PRIVATE).getString("token", ""), true);
        root.addView(url);
        root.addView(token);

        status = new TextView(this);
        status.setText("Siap.");
        status.setTextColor(Color.rgb(120, 220, 120));
        status.setPadding(0, dp(10), 0, dp(10));
        root.addView(status);

        install = new Button(this);
        install.setText("INSTALL / UPDATE DASHBOARD HERMES WORK");
        install.setOnClickListener(v -> startInstall());
        root.addView(install);

        scroll = new ScrollView(this);
        log = new TextView(this);
        log.setTextColor(Color.rgb(210,255,210));
        log.setTextSize(13);
        log.setTextIsSelectable(true);
        log.setPadding(0, dp(12), 0, 0);
        scroll.addView(log);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
    }

    private EditText field(String hint, String value, boolean secret) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.GRAY);
        e.setSingleLine(true);
        if (secret) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return e;
    }

    private void startInstall() {
        final String runtime = cleanBase(url.getText().toString());
        final String tok = token.getText().toString().trim();
        if (tok.isEmpty()) { status("ADMIN TOKEN wajib diisi.", Color.YELLOW); return; }
        getPreferences(MODE_PRIVATE).edit().putString("url", runtime).putString("token", tok).apply();
        install.setEnabled(false); log.setText(""); status("Memulai instalasi...", Color.YELLOW);

        io.execute(() -> {
            try {
                append("1/5 Cek HERMES WORK runtime...\n");
                HttpResult st;
                try { st = request(runtime + "/api/work/status", "GET", null, null, 4000, 8000); }
                catch (Exception first) {
                    append("Runtime belum aktif, mencoba bootstrap recovery...\n");
                    JSONObject body = new JSONObject();
                    body.put("command", "ROOT=/data/adb/hermes_work; VER=$(cat $ROOT/current_release 2>/dev/null); REL=$ROOT/releases/$VER; [ -x \"$REL/bin/workd\" ] || exit 7; nohup \"$REL/bin/workd\" --root \"$ROOT\" --release \"$REL\" >>\"$ROOT/logs/workd.log\" 2>&1 & echo $! > \"$ROOT/state/workd.pid\"; sleep 2");
                    HttpResult ex = request(BOOTSTRAP_URL + "/api/exec", "POST", body.toString().getBytes(StandardCharsets.UTF_8), tok, 5000, 15000);
                    if (ex.code < 200 || ex.code >= 300) throw new Exception("Bootstrap recovery HTTP " + ex.code + "\n" + ex.text());
                    st = request(runtime + "/api/work/status", "GET", null, null, 5000, 10000);
                }
                if (st.code != 200) throw new Exception("Runtime HTTP " + st.code);
                append("OK runtime online\n");

                append("2/5 Update ke release terbaru...\n");
                HttpResult up = request(runtime + "/api/work/update", "POST", new byte[0], tok, 8000, 60000);
                if (up.code < 200 || up.code >= 300) throw new Exception("Update HTTP " + up.code + "\n" + up.text());
                append(up.text().trim() + "\n");

                append("3/5 Aktifkan release baru...\n");
                Thread.sleep(1200);
                JSONObject body = new JSONObject();
                body.put("command", "ROOT=/data/adb/hermes_work; VER=$(cat $ROOT/current_release 2>/dev/null); PREV=$(cat $ROOT/previous_release 2>/dev/null); REL=$ROOT/releases/$VER; chmod 755 \"$REL/bin/workd\" \"$REL/worker/tick.sh\" \"$REL/worker/handoff.sh\" 2>/dev/null; if [ -x \"$REL/worker/handoff.sh\" ]; then sh \"$REL/worker/handoff.sh\" \"$ROOT\" \"$VER\" \"$PREV\"; fi");
                HttpResult ex = request(BOOTSTRAP_URL + "/api/exec", "POST", body.toString().getBytes(StandardCharsets.UTF_8), tok, 5000, 30000);
                if (ex.code < 200 || ex.code >= 300) throw new Exception("Handoff HTTP " + ex.code + "\n" + ex.text());
                append("Handoff selesai\n");

                append("4/5 Health check dashboard...\n");
                Thread.sleep(2500);
                HttpResult fin = request(runtime + "/api/work/status", "GET", null, null, 5000, 12000);
                if (fin.code != 200) throw new Exception("Health HTTP " + fin.code);
                JSONObject sj = new JSONObject(fin.text());
                String version = sj.optString("release", "UNKNOWN");
                append("Active release: " + version + "\n");

                append("5/5 Selesai. Dashboard: " + runtime + "/\n");
                status("SELESAI · " + version + " aktif", Color.GREEN);
                append("\nINSTALL SUCCESS\n");
            } catch (Exception e) {
                status("GAGAL · " + e.getMessage(), Color.RED);
                append("\nERROR: " + e.getMessage() + "\n");
            } finally {
                runOnUiThread(() -> install.setEnabled(true));
            }
        });
    }

    private HttpResult multipartUpload(String target, String tok, byte[] data, String filename) throws Exception {
        String boundary = "----HermesWork" + System.currentTimeMillis();
        HttpURLConnection c = (HttpURLConnection) new URL(target).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(8000);
        c.setReadTimeout(45000);
        c.setDoOutput(true);
        c.setUseCaches(false);
        c.setRequestProperty("X-Hermes-Token", tok);
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        try (OutputStream os = c.getOutputStream()) {
            String head = "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"bundle\"; filename=\"" + filename + "\"\r\n" +
                    "Content-Type: application/zip\r\n\r\n";
            os.write(head.getBytes(StandardCharsets.UTF_8));
            os.write(data);
            os.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        }
        int code = c.getResponseCode();
        byte[] out = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream(), 2 * 1024 * 1024);
        c.disconnect();
        return new HttpResult(code, out);
    }

    private HttpResult request(String target, String method, byte[] body, String tok, int connectMs, int readMs) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(target).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(connectMs);
        c.setReadTimeout(readMs);
        c.setUseCaches(false);
        c.setRequestProperty("Accept", "*/*");
        if (tok != null && !tok.isEmpty()) c.setRequestProperty("X-Hermes-Token", tok);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setFixedLengthStreamingMode(body.length);
            try (OutputStream os = c.getOutputStream()) { os.write(body); }
        }
        int code = c.getResponseCode();
        byte[] out = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream(), 32 * 1024 * 1024);
        c.disconnect();
        return new HttpResult(code, out);
    }

    private byte[] readAll(InputStream in, int max) throws Exception {
        if (in == null) return new byte[0];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int total = 0, n;
        try (InputStream x = in) {
            while ((n = x.read(buf)) != -1) {
                total += n;
                if (total > max) throw new Exception("Response terlalu besar");
                out.write(buf, 0, n);
            }
        }
        return out.toByteArray();
    }

    private String sha256(byte[] b) throws Exception {
        byte[] h = MessageDigest.getInstance("SHA-256").digest(b);
        StringBuilder s = new StringBuilder();
        for (byte x : h) s.append(String.format(Locale.US, "%02x", x & 0xff));
        return s.toString();
    }

    private String cleanBase(String s) {
        s = s.trim();
        if (s.isEmpty()) s = DEFAULT_URL;
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://" + s;
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private void append(String s) {
        runOnUiThread(() -> {
            log.append(s);
            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void status(String s, int color) {
        runOnUiThread(() -> {
            status.setText(s);
            status.setTextColor(color);
        });
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    static class HttpResult {
        final int code;
        final byte[] body;
        HttpResult(int code, byte[] body) { this.code = code; this.body = body == null ? new byte[0] : body; }
        String text() { return new String(body, StandardCharsets.UTF_8); }
    }
}
