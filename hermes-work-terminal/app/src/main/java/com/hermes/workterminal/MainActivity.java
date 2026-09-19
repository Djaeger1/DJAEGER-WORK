package com.hermes.workterminal;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String DEFAULT_URL = "http://192.168.42.129:8765";
    private static final String PREFS = "hermes_work_terminal";
    private static final String KEY_URL = "base_url";
    private static final String KEY_TOKEN = "admin_token";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private TextView output;
    private TextView status;
    private EditText command;
    private ScrollView scroll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildUi();

        if (prefs.getString(KEY_TOKEN, "").trim().isEmpty()) {
            showSettings(true);
        } else {
            append("HERMES WORK Terminal v0.1\n");
            append("Target: " + baseUrl() + "\n");
            append("Ketik perintah lalu ENTER.\n\n");
            health();
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.setBackgroundColor(Color.rgb(10, 13, 16));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("HERMES WORK TERMINAL");
        title.setTextColor(Color.WHITE);
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        status = new TextView(this);
        status.setText("● CHECKING");
        status.setTextColor(Color.LTGRAY);
        top.addView(status);
        root.addView(top);

        TextView target = new TextView(this);
        target.setText(DEFAULT_URL);
        target.setTextColor(Color.GRAY);
        target.setTextSize(12);
        target.setOnClickListener(v -> showSettings(false));
        root.addView(target);

        scroll = new ScrollView(this);
        output = new TextView(this);
        output.setTextColor(Color.rgb(210, 255, 210));
        output.setTextSize(13);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextIsSelectable(true);
        output.setPadding(0, dp(10), 0, dp(10));
        scroll.addView(output);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        command = new EditText(this);
        command.setHint("root@hermes-work:~#");
        command.setSingleLine(true);
        command.setTextColor(Color.WHITE);
        command.setHintTextColor(Color.GRAY);
        command.setTypeface(Typeface.MONOSPACE);
        command.setImeOptions(EditorInfo.IME_ACTION_GO);
        command.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        command.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER &&
                            event.getAction() == KeyEvent.ACTION_DOWN)) {
                runCommand();
                return true;
            }
            return false;
        });
        row.addView(command, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button run = button("RUN", v -> runCommand());
        row.addView(run);
        root.addView(row);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);

        actions.addView(button("HEALTH", v -> health()), weighted());
        actions.addView(button("LOG", v -> loadLog()), weighted());
        actions.addView(button("CLEAR", v -> output.setText("")), weighted());
        actions.addView(button("SETTINGS", v -> showSettings(false)), weighted());
        actions.addView(button("DASH", v -> openDashboard()), weighted());
        root.addView(actions);

        setContentView(root);
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private Button button(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(11);
        b.setOnClickListener(listener);
        return b;
    }

    private String baseUrl() {
        String v = prefs.getString(KEY_URL, DEFAULT_URL).trim();
        if (v.endsWith("/")) v = v.substring(0, v.length() - 1);
        return v;
    }

    private String token() {
        return prefs.getString(KEY_TOKEN, "").trim();
    }

    private void runCommand() {
        String cmd = command.getText().toString().trim();
        if (cmd.isEmpty()) return;
        command.setText("");
        append("root@hermes-work:~# " + cmd + "\n");
        setStatus("● RUNNING", Color.YELLOW);

        io.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("command", cmd);
                HttpResult r = request("POST", "/api/exec", body.toString(), true);
                if (r.code == 401) {
                    ui(() -> {
                        append("[unauthorized] token salah / belum diisi\n\n");
                        setStatus("● AUTH", Color.YELLOW);
                        showSettings(false);
                    });
                    return;
                }
                String shown = r.body;
                try {
                    JSONObject j = new JSONObject(r.body);
                    int rc = j.optInt("rc", -1);
                    boolean timeout = j.optBoolean("timeout", false);
                    String out = j.optString("output", "");
                    shown = out + (timeout ? "\n[TIMEOUT]" : "") + "\n[rc=" + rc + "]";
                } catch (Exception ignored) {}
                final String f = shown;
                ui(() -> {
                    append(f + "\n\n");
                    setStatus(r.code >= 200 && r.code < 300 ? "● ONLINE" : "● HTTP " + r.code,
                            r.code >= 200 && r.code < 300 ? Color.GREEN : Color.YELLOW);
                });
            } catch (Exception e) {
                ui(() -> {
                    append("[ERROR] " + e.getMessage() + "\n\n");
                    setStatus("● OFFLINE", Color.RED);
                });
            }
        });
    }

    private void health() {
        setStatus("● CHECKING", Color.LTGRAY);
        io.execute(() -> {
            try {
                HttpResult r = request("GET", "/health", null, false);
                ui(() -> {
                    append("[health] " + r.body.trim() + "\n");
                    setStatus((r.code == 200 || r.code == 503) ? "● ONLINE" : "● HTTP " + r.code,
                            (r.code == 200 || r.code == 503) ? Color.GREEN : Color.YELLOW);
                });
            } catch (Exception e) {
                ui(() -> {
                    append("[health error] " + e.getMessage() + "\n");
                    setStatus("● OFFLINE", Color.RED);
                });
            }
        });
    }

    private void loadLog() {
        setStatus("● LOADING LOG", Color.LTGRAY);
        io.execute(() -> {
            try {
                HttpResult r = request("GET", "/api/log?lines=200", null, true);
                ui(() -> {
                    append("\n===== HERMES LOG =====\n" + r.body + "\n======================\n");
                    setStatus(r.code == 200 ? "● ONLINE" : "● HTTP " + r.code,
                            r.code == 200 ? Color.GREEN : Color.YELLOW);
                });
            } catch (Exception e) {
                ui(() -> {
                    append("[log error] " + e.getMessage() + "\n");
                    setStatus("● OFFLINE", Color.RED);
                });
            }
        });
    }

    private void openDashboard() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(baseUrl() + "/")));
        } catch (Exception e) {
            append("[dashboard] " + e.getMessage() + "\n");
        }
    }

    private HttpResult request(String method, String path, String body, boolean auth) throws Exception {
        URL url = new URL(baseUrl() + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(3500);
        c.setReadTimeout(15000);
        c.setUseCaches(false);
        c.setRequestProperty("Accept", "application/json, text/plain, */*");
        if (auth) c.setRequestProperty("X-Hermes-Token", token());

        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] data = body.getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(data.length);
            try (OutputStream os = c.getOutputStream()) {
                os.write(data);
            }
        }

        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = readAll(in);
        c.disconnect();
        return new HttpResult(code, text);
    }

    private String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private void showSettings(boolean firstRun) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        box.setPadding(p, p / 2, p, 0);

        EditText url = new EditText(this);
        url.setHint("Server URL");
        url.setSingleLine(true);
        url.setText(prefs.getString(KEY_URL, DEFAULT_URL));
        box.addView(url);

        EditText tok = new EditText(this);
        tok.setHint("ADMIN TOKEN");
        tok.setSingleLine(true);
        tok.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tok.setText(prefs.getString(KEY_TOKEN, ""));
        box.addView(tok);

        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("HERMES WORK Connection")
                .setView(box)
                .setPositiveButton("SAVE", null)
                .setNegativeButton(firstRun ? "LATER" : "CANCEL", null)
                .create();

        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String u = url.getText().toString().trim();
            String t = tok.getText().toString().trim();
            if (u.isEmpty()) u = DEFAULT_URL;
            if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://" + u;
            prefs.edit().putString(KEY_URL, u).putString(KEY_TOKEN, t).apply();
            d.dismiss();
            append("[settings] target=" + baseUrl() + "\n");
            health();
        }));
        d.show();
    }

    private void append(String s) {
        output.append(s);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void setStatus(String text, int color) {
        status.setText(text);
        status.setTextColor(color);
    }

    private void ui(Runnable r) {
        runOnUiThread(r);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private static class HttpResult {
        final int code;
        final String body;
        HttpResult(int code, String body) {
            this.code = code;
            this.body = body == null ? "" : body;
        }
    }
}
