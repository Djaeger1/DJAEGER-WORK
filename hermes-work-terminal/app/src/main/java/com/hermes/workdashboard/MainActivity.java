package com.hermes.workdashboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(2, 8, 18);
    private static final int PANEL = Color.rgb(7, 20, 34);
    private static final int PANEL2 = Color.rgb(8, 24, 39);
    private static final int LINE = Color.rgb(22, 55, 87);
    private static final int TEXT = Color.rgb(245, 247, 251);
    private static final int MUTED = Color.rgb(148, 162, 183);
    private static final int OK = Color.rgb(57, 231, 122);
    private static final int WARN = Color.rgb(229, 181, 77);
    private static final int BAD = Color.rgb(255, 105, 117);
    private static final int ACCENT = Color.rgb(63, 145, 255);

    private static final String DEFAULT_RUNTIME = "http://192.168.42.129:8766";
    private static final String DEFAULT_BOOTSTRAP = "http://192.168.42.129:8765";
    private static final String PREFS = "hermes_work_dashboard";
    private static final String PREF_REMOTE_URL = "remote_url";
    private static final String PREF_REMOTE_KEY = "remote_key";

    // Parallel UI I/O prevents one slow local request from blocking every dashboard card.
    private final ExecutorService io = Executors.newFixedThreadPool(4);
    private SharedPreferences prefs;

    private FrameLayout content;
    private TextView online;
    private TextView pageTitle;
    private LinearLayout navBar;
    private int currentPage = 0;
    private boolean destroyed = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildShell();
        showPage(0);
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(9), dp(12), dp(9));
        top.setBackground(gradientBg(Color.rgb(5, 18, 32), Color.rgb(3, 12, 23), 0));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.djaeger_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(54), dp(54));
        logoLp.setMargins(0, 0, dp(10), 0);
        top.addView(logo, logoLp);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView brand = text("DJAEGER WORK", 20, TEXT, true);
        TextView tagline = text("KERJA · RISET · TUMBUH", 8, MUTED, true);
        tagline.setLetterSpacing(0.16f);
        pageTitle = text("KERJA", 9, ACCENT, true);
        pageTitle.setPadding(0, dp(3), 0, 0);
        titles.addView(brand);
        titles.addView(tagline);
        titles.addView(pageTitle);
        top.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView refresh = text("↻", 22, TEXT, false);
        refresh.setGravity(Gravity.CENTER);
        refresh.setClickable(true);
        refresh.setFocusable(true);
        refresh.setBackground(solidBg(Color.rgb(10, 27, 47), Color.rgb(26, 64, 105), dp(10)));
        refresh.setOnClickListener(v -> showPage(currentPage));
        LinearLayout.LayoutParams refreshLp = new LinearLayout.LayoutParams(dp(38), dp(38));
        refreshLp.setMargins(dp(4), 0, dp(8), 0);
        top.addView(refresh, refreshLp);

        online = text("● MEMERIKSA", 11, MUTED, true);
        online.setGravity(Gravity.CENTER);
        online.setPadding(dp(10), 0, dp(10), 0);
        online.setBackground(solidBg(Color.rgb(25, 28, 34), Color.rgb(48, 58, 72), dp(20)));
        top.addView(online, new LinearLayout.LayoutParams(-2, dp(38)));
        root.addView(top);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        HorizontalScrollView navScroll = new HorizontalScrollView(this);
        navScroll.setHorizontalScrollBarEnabled(false);
        navScroll.setFillViewport(true);
        navScroll.setBackgroundColor(Color.rgb(5, 17, 29));
        navBar = new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setGravity(Gravity.CENTER);
        navBar.setPadding(dp(3), dp(5), dp(3), dp(6));
        navScroll.addView(navBar, new HorizontalScrollView.LayoutParams(-1, -2));

        String[] labels = {"KERJA", "RISET", "RENCANA", "WAWASAN", "SISTEM", "PEMBARUAN"};
        for (int i = 0; i < labels.length; i++) {
            final int p = i;
            Button b = navButton(labels[i]);
            b.setOnClickListener(v -> showPage(p));
            navBar.addView(b, new LinearLayout.LayoutParams(0, dp(50), 1f));
        }
        root.addView(navScroll);
        setContentView(root);
    }

    private void showPage(int page) {
        currentPage = page;
        String[] names = {"KERJA", "RISET", "RENCANA", "WAWASAN", "SISTEM", "PEMBARUAN"};
        pageTitle.setText(names[page]);
        updateNav();

        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(12), dp(14), dp(18));
        scroll.addView(body);
        content.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        switch (page) {
            case 0: buildWork(body); break;
            case 1: buildResearch(body); break;
            case 2: buildPlanner(body); break;
            case 3: buildInsights(body); break;
            case 4: buildSystem(body); break;
            case 5: buildUpdate(body); break;
        }
        refreshOnlineOnly();
    }

    private void updateNav() {
        for (int i = 0; i < navBar.getChildCount(); i++) {
            Button b = (Button) navBar.getChildAt(i);
            boolean active = i == currentPage;
            b.setTextColor(active ? Color.WHITE : MUTED);
            b.setBackground(active
                    ? gradientBg(Color.rgb(35, 89, 166), Color.rgb(25, 67, 128), dp(10))
                    : solidBg(Color.TRANSPARENT, Color.TRANSPARENT, dp(10)));
        }
    }

    private void buildWork(LinearLayout body) {
        body.addView(sectionTitle("Pekerjaan Hari Ini", "Fokus utama: apa yang harus dikerjakan hari ini."));

        LinearLayout health = card();
        health.addView(text("STATUS PEKERJA", 11, MUTED, true));
        LinearLayout healthRow = row();
        TextView modem = metric(healthRow, "MODEM", "…");
        TextView worker = metric(healthRow, "PEKERJA", "…");
        health.addView(healthRow);
        body.addView(health);

        LinearLayout stats = card();
        stats.addView(text("RISET HARI INI", 11, MUTED, true));
        LinearLayout r1 = row();
        TextView sources = metric(r1, "SUMBER", "—");
        TextView found = metric(r1, "DITEMUKAN", "—");
        stats.addView(r1);
        LinearLayout r2 = row();
        TextView added = metric(r2, "BARU", "—");
        TextView dup = metric(r2, "DUPLIKAT", "—");
        stats.addView(r2);
        TextView last = text("Riset terakhir: —", 12, MUTED, false);
        last.setPadding(0, dp(8), 0, 0);
        stats.addView(last);

        Button run = actionButton("JALANKAN RISET SEKARANG", true);
        stats.addView(run);
        TextView runOut = mono("Siap.");
        stats.addView(runOut);
        body.addView(stats);

        LinearLayout briefCard = card();
        briefCard.addView(text("RINGKASAN HARIAN", 11, MUTED, true));

        TextView topLabel = text("PELUANG TERATAS", 9, ACCENT, true);
        topLabel.setPadding(0, dp(10), 0, dp(3));
        briefCard.addView(topLabel);

        TextView topTitle = text("Belum ada ringkasan.", 18, TEXT, true);
        topTitle.setPadding(0, 0, 0, dp(8));
        briefCard.addView(topTitle);

        LinearLayout briefMetrics = row();
        TextView briefScore = metric(briefMetrics, "SKOR PRIORITAS", "—");
        TextView briefDemand = metric(briefMetrics, "PERMINTAAN", "—");
        TextView briefTrend = metric(briefMetrics, "TREN", "—");
        briefCard.addView(briefMetrics);

        TextView whyTitle = text("MENGAPA INI PENTING", 9, MUTED, true);
        whyTitle.setPadding(0, dp(8), 0, dp(2));
        briefCard.addView(whyTitle);
        TextView briefWhy = text("—", 12, TEXT, false);
        briefWhy.setLineSpacing(0, 1.15f);
        briefCard.addView(briefWhy);

        TextView formatTitle = text("FORMAT DISARANKAN", 9, MUTED, true);
        formatTitle.setPadding(0, dp(10), 0, dp(2));
        briefCard.addView(formatTitle);
        TextView briefFormat = text("—", 12, ACCENT, true);
        briefCard.addView(briefFormat);

        TextView actionTitle = text("LANGKAH BERIKUTNYA", 9, MUTED, true);
        actionTitle.setPadding(0, dp(10), 0, dp(2));
        briefCard.addView(actionTitle);
        TextView briefAction = text("—", 12, OK, true);
        briefAction.setLineSpacing(0, 1.15f);
        briefCard.addView(briefAction);

        TextView otherTitle = text("IDE TERATAS", 9, MUTED, true);
        otherTitle.setPadding(0, dp(12), 0, dp(3));
        briefCard.addView(otherTitle);
        TextView briefList = text("Belum ada ide.", 12, TEXT, false);
        briefList.setLineSpacing(0, 1.18f);
        briefCard.addView(briefList);

        TextView briefGenerated = text("Dibuat: —", 10, MUTED, false);
        briefGenerated.setPadding(0, dp(10), 0, 0);
        briefCard.addView(briefGenerated);

        body.addView(briefCard);

        run.setOnClickListener(v -> {
            run.setEnabled(false);
            runOut.setText("Riset berjalan…");
            apiAsync("POST", "/api/work/run-research", null, true, (code, s) -> {
                run.setEnabled(true);
                runOut.setText(s.trim());
                loadWorkData(modem, worker, sources, found, added, dup, last,
                        topTitle, briefScore, briefDemand, briefTrend, briefWhy,
                        briefFormat, briefAction, briefList, briefGenerated);
            });
        });

        loadWorkData(modem, worker, sources, found, added, dup, last,
                topTitle, briefScore, briefDemand, briefTrend, briefWhy,
                briefFormat, briefAction, briefList, briefGenerated);
    }

    private void loadWorkData(TextView modem, TextView worker, TextView sources, TextView found,
                              TextView added, TextView dup, TextView last,
                              TextView topTitle, TextView briefScore, TextView briefDemand,
                              TextView briefTrend, TextView briefWhy, TextView briefFormat,
                              TextView briefAction, TextView briefList, TextView briefGenerated) {
        apiAsync("GET", "/api/work/status", null, false, (code, s) -> {
            try {
                JSONObject j = new JSONObject(s);
                setMetric(modem, j.optString("tether_state", "—"), "UP".equals(j.optString("tether_state")) ? OK : BAD);
                String w = j.optBoolean("safe_mode") ? "SAFE MODE" : (j.optBoolean("worker_paused") ? "PAUSED" : "READY");
                setMetric(worker, w, "READY".equals(w) ? OK : WARN);
            } catch (Exception ignored) {}
        });

        apiAsync("GET", "/api/work/daily", null, false, (code, s) -> {
            try {
                JSONObject j = new JSONObject(s);
                JSONObject lr = j.optJSONObject("last_run");
                if (lr != null) {
                    setMetric(sources, val(lr, "sources_checked"), TEXT);
                    setMetric(found, val(lr, "found"), TEXT);
                    setMetric(added, val(lr, "added"), OK);
                    setMetric(dup, val(lr, "duplicates"), MUTED);
                }
            } catch (Exception ignored) {}
        });

        apiAsync("GET", "/api/work/research", null, false, (code, s) -> {
            try { last.setText("Riset terakhir: " + formatWibTime(new JSONObject(s).optString("last_research"))); } catch (Exception ignored) {}
        });

        apiAsync("GET", "/api/work/brief", null, false, (code, s) -> {
            try {
                JSONObject j = new JSONObject(s);
                JSONArray a = j.optJSONArray("ideas");
                if (a == null || a.length() == 0) {
                    topTitle.setText("Belum ada ide. Jalankan riset.");
                    setMetric(briefScore, "—", MUTED);
                    setMetric(briefDemand, "—", MUTED);
                    setMetric(briefTrend, "—", MUTED);
                    briefWhy.setText("Belum ada data.");
                    briefFormat.setText("—");
                    briefAction.setText("Jalankan riset untuk membuat Ringkasan Harian.");
                    briefList.setText("Belum ada ide.");
                    briefGenerated.setText("Dibuat: —");
                    return;
                }

                JSONObject top = a.getJSONObject(0);
                String title = dash(top.optString("Title", top.optString("title")));
                String category = localizeCategory(top.optString("Category", top.optString("category")));
                double score = top.optDouble("Score", top.optDouble("score", 0));
                String demand = top.optString("Demand", top.optString("demand", "DISCOVERY"));
                String why = top.optString("Why", top.optString("why", "Belum ada alasan."));
                String format = top.optString("Format", top.optString("format", "—"));

                topTitle.setText(category.isEmpty() ? title : title + "  ·  " + category);
                int scoreColor = score >= 82 ? OK : (score >= 65 ? WARN : MUTED);
                setMetric(briefScore, Math.round(score) + "/100", scoreColor);
                setMetric(briefDemand, demand, "HIGH".equalsIgnoreCase(demand) ? OK : ("MEDIUM".equalsIgnoreCase(demand) ? WARN : MUTED));

                String prevTitle = prefs.getString("daily_brief_top_title", "");
                float prevScore = prefs.getFloat("daily_brief_top_score", -1f);
                String trend;
                int trendColor;
                if (!title.equals(prevTitle) || prevScore < 0) {
                    trend = "BARU";
                    trendColor = ACCENT;
                } else if (score > prevScore + 0.9f) {
                    trend = "↑ NAIK";
                    trendColor = OK;
                } else if (score < prevScore - 0.9f) {
                    trend = "↓ TURUN";
                    trendColor = BAD;
                } else {
                    trend = "→ STABIL";
                    trendColor = MUTED;
                }
                setMetric(briefTrend, trend, trendColor);
                prefs.edit().putString("daily_brief_top_title", title).putFloat("daily_brief_top_score", (float) score).apply();

                briefWhy.setText(localizeWhy(why));
                briefFormat.setText(localizeFormat(format));

                String action;
                if ("HIGH".equalsIgnoreCase(demand)) {
                    action = "Prioritaskan hari ini → lanjutkan ke RENCANA dan siapkan naskah.";
                } else if ("MEDIUM".equalsIgnoreCase(demand)) {
                    action = "Layak diuji → jadwalkan di RENCANA lalu bandingkan performanya.";
                } else {
                    action = "Uji eksplorasi → validasi lagi sebelum masuk produksi.";
                }
                briefAction.setText(action);

                StringBuilder list = new StringBuilder();
                for (int i = 0; i < Math.min(3, a.length()); i++) {
                    JSONObject o = a.getJSONObject(i);
                    String t = dash(o.optString("Title", o.optString("title")));
                    String cat = localizeCategory(o.optString("Category", o.optString("category")));
                    double sc = o.optDouble("Score", o.optDouble("score", 0));
                    list.append(i + 1).append(". ").append(t);
                    if (!cat.isEmpty()) list.append("  ·  ").append(cat);
                    list.append("  [").append(Math.round(sc)).append("]");
                    if (i + 1 < Math.min(3, a.length())) list.append("\n");
                }
                briefList.setText(list.toString());

                String generated = j.optString("generated_at", "");
                briefGenerated.setText("Dibuat: " + (generated.isEmpty() ? "—" : formatWibTime(generated)));
            } catch (Exception e) {
                topTitle.setText("Ringkasan Harian belum dapat dibaca.");
                briefAction.setText("Coba segarkan setelah riset selesai.");
            }
        });
    }

    private void buildResearch(LinearLayout body) {
        body.addView(sectionTitle("Analisis Riset", "Apa yang dicari dan dibutuhkan, lalu dikelompokkan menjadi topik."));

        LinearLayout summary = card();
        summary.addView(text("RINGKASAN RISET", 11, MUTED, true));
        LinearLayout r = row();
        TextView total = metric(r, "TOTAL ITEM", "—");
        TextView last = metric(r, "PROSES TERAKHIR", "—");
        summary.addView(r);
        body.addView(summary);

        LinearLayout catCard = card();
        catCard.addView(text("KATEGORI TOPIK", 11, MUTED, true));
        LinearLayout cats = new LinearLayout(this);
        cats.setOrientation(LinearLayout.VERTICAL);
        catCard.addView(cats);
        body.addView(catCard);

        LinearLayout searchCard = card();
        searchCard.addView(text("YANG DICARI / DIBUTUHKAN ORANG", 11, MUTED, true));
        TextView top = text("Belum ada data.", 14, TEXT, false);
        top.setPadding(0, dp(8), 0, 0);
        searchCard.addView(top);
        body.addView(searchCard);

        apiAsync("GET", "/api/work/research", null, false, (code, s) -> {
            try {
                JSONObject j = new JSONObject(s);
                setMetric(total, String.valueOf(j.optInt("total_items")), TEXT);
                String lr = dash(j.optString("last_research"));
                setMetric(last, lr.length() > 10 ? lr.substring(0, 10) : lr, MUTED);
                JSONObject c = j.optJSONObject("categories");
                if (c != null) renderCategories(cats, c);
            } catch (Exception e) { cats.addView(text("Data belum tersedia.", 13, MUTED, false)); }
        });

        apiAsync("GET", "/api/work/brief", null, false, (code, s) -> {
            try {
                JSONArray a = new JSONObject(s).optJSONArray("ideas");
                if (a == null || a.length() == 0) return;
                StringBuilder x = new StringBuilder();
                for (int i = 0; i < Math.min(8, a.length()); i++) {
                    JSONObject o = a.getJSONObject(i);
                    double score = o.optDouble("Score", o.optDouble("score", 0));
                    x.append("• ").append(dash(o.optString("Title", o.optString("title"))))
                            .append("   [").append(Math.round(score)).append("]\n");
                }
                top.setText(x.toString().trim());
            } catch (Exception ignored) {}
        });
    }

    private void renderCategories(LinearLayout target, JSONObject c) {
        target.removeAllViews();
        try {
            Iterator<String> it = c.keys();
            boolean any = false;
            while (it.hasNext()) {
                any = true;
                String k = it.next();
                LinearLayout line = row();
                TextView name = text(localizeCategory(k).toUpperCase(Locale.US), 13, TEXT, true);
                TextView count = text(String.valueOf(c.optInt(k)), 15, ACCENT, true);
                line.addView(name, new LinearLayout.LayoutParams(0, dp(38), 1f));
                line.addView(count, new LinearLayout.LayoutParams(dp(70), dp(38)));
                line.setGravity(Gravity.CENTER_VERTICAL);
                target.addView(line);
            }
            if (!any) target.addView(text("BELUM ADA DATA", 13, WARN, true));
        } catch (Exception e) {
            target.addView(text("BELUM ADA DATA", 13, WARN, true));
        }
    }

    private void buildPlanner(LinearLayout body) {
        body.addView(sectionTitle("Perencana Konten", "Dari hasil riset menjadi prioritas konten yang siap dikerjakan."));

        LinearLayout pipe = card();
        pipe.addView(text("ALUR KERJA", 11, MUTED, true));
        pipe.addView(text("Riset  →  Deduplikasi  →  Kategorisasi  →  Skor", 13, TEXT, true));
        pipe.addView(text("Ide  →  Naskah  →  Produksi  →  Terbit  →  Performa", 13, TEXT, true));
        body.addView(pipe);

        LinearLayout state = card();
        state.addView(text("STATUS PRODUKSI", 11, MUTED, true));
        LinearLayout r1 = row();
        TextView ideas = metric(r1, "IDE SIAP", "—");
        TextView scripts = metric(r1, "NASKAH SIAP", "BELUM TERHUBUNG");
        state.addView(r1);
        LinearLayout r2 = row();
        TextView produced = metric(r2, "SUDAH DIPRODUKSI", "BELUM TERHUBUNG");
        TextView uploaded = metric(r2, "SUDAH DIUNGGAH", "BELUM TERHUBUNG");
        state.addView(r2);
        scripts.setTextColor(WARN); produced.setTextColor(WARN); uploaded.setTextColor(WARN);
        body.addView(state);

        LinearLayout listCard = card();
        listCard.addView(text("PELUANG TERATAS", 11, MUTED, true));
        TextView list = text("Belum ada ide.", 14, TEXT, false);
        list.setPadding(0, dp(8), 0, 0);
        listCard.addView(list);
        body.addView(listCard);

        apiAsync("GET", "/api/work/brief", null, false, (code, s) -> {
            try {
                JSONArray a = new JSONObject(s).optJSONArray("ideas");
                int n = a == null ? 0 : a.length();
                setMetric(ideas, String.valueOf(n), n > 0 ? OK : MUTED);
                if (n == 0) { list.setText("Belum ada ide. Jalankan riset."); return; }
                StringBuilder x = new StringBuilder();
                for (int i = 0; i < Math.min(10, n); i++) {
                    JSONObject o = a.getJSONObject(i);
                    String title = dash(o.optString("Title", o.optString("title")));
                    String cat = dash(o.optString("Category", o.optString("category")));
                    double score = o.optDouble("Score", o.optDouble("score", 0));
                    x.append(i + 1).append(". ").append(title)
                            .append("\n   ").append(localizeCategory(cat)).append("  ·  skor ").append(Math.round(score)).append("\n\n");
                }
                list.setText(x.toString().trim());
            } catch (Exception ignored) {}
        });
    }

    private void buildInsights(LinearLayout body) {
        body.addView(sectionTitle("Wawasan & Memori", "Performa kanal dan pengetahuan yang sudah dikumpulkan DJAEGER WORK."));

        LinearLayout channel = card();
        channel.addView(text("KANAL SAYA", 11, MUTED, true));
        LinearLayout r1 = row();
        TextView conn = metric(r1, "KONEKSI", "—");
        TextView views = metric(r1, "TAYANGAN", "—");
        channel.addView(r1);
        LinearLayout r2 = row();
        TextView retention = metric(r2, "RETENSI", "—");
        TextView ctr = metric(r2, "CTR / INTERAKSI", "—");
        channel.addView(r2);
        LinearLayout r3 = row();
        TextView watch = metric(r3, "WAKTU TONTON", "—");
        TextView best = metric(r3, "TOPIK TERBAIK", "—");
        channel.addView(r3);
        body.addView(channel);

        LinearLayout memory = card();
        memory.addView(text("MEMORI / PENGETAHUAN", 11, MUTED, true));
        LinearLayout m1 = row();
        TextView items = metric(m1, "ITEM RISET", "—");
        TextView unique = metric(m1, "KUNCI UNIK", "—");
        memory.addView(m1);
        LinearLayout m2 = row();
        TextView success = metric(m2, "BERHASIL", "—");
        TextView under = metric(m2, "KINERJA RENDAH", "—");
        memory.addView(m2);
        LinearLayout m3 = row();
        TextView pending = metric(m3, "BELUM DIPRODUKSI", "—");
        TextView cats = metric(m3, "KATEGORI", "—");
        memory.addView(m3);
        body.addView(memory);

        apiAsync("GET", "/api/work/channel", null, false, (code, s) -> {
            try {
                JSONObject j = new JSONObject(s);
                setMetric(conn, dash(j.optString("state")), "CONNECTED".equals(j.optString("state")) ? OK : WARN);
                JSONObject m = j.optJSONObject("metrics");
                if (m == null) m = j;
                setMetric(views, value(m, "views"), TEXT);
                setMetric(retention, value(m, "retention"), TEXT);
                setMetric(ctr, value(m, "ctr"), TEXT);
                setMetric(watch, value(m, "watch_time"), TEXT);
                setMetric(best, value(m, "best_topic"), TEXT);
            } catch (Exception ignored) {}
        });
        apiAsync("GET", "/api/work/knowledge", null, false, (code, s) -> {
            try {
                JSONObject j = new JSONObject(s);
                setMetric(items, value(j, "research_items"), TEXT);
                setMetric(unique, value(j, "unique_keys"), TEXT);
                setMetric(success, value(j, "successful"), WARN);
                setMetric(under, value(j, "underperforming"), WARN);
                setMetric(pending, value(j, "ideas_not_produced"), WARN);
                JSONObject c = j.optJSONObject("categories");
                setMetric(cats, c == null ? "0" : String.valueOf(c.length()), TEXT);
            } catch (Exception ignored) {}
        });
    }

    private void buildSystem(LinearLayout body) {
        body.addView(sectionTitle("Sistem", "Status pekerja, prioritas modem, pengaman sumber daya, dan otomatisasi."));

        LinearLayout device = card();
        device.addView(text("REDMI 5A / MODEM", 11, MUTED, true));
        LinearLayout r1 = row();
        TextView tether = metric(r1, "TETHER", "—");
        TextView ip = metric(r1, "IP", "—");
        device.addView(r1);
        LinearLayout r2 = row();
        TextView temp = metric(r2, "SUHU", "—");
        TextView ram = metric(r2, "RAM BEBAS", "—");
        device.addView(r2);
        LinearLayout r3 = row();
        TextView worker = metric(r3, "PEKERJA", "—");
        TextView bridge = metric(r3, "BRIDGE", "—");
        device.addView(r3);
        body.addView(device);

        LinearLayout auto = card();
        auto.addView(text("OTOMATISASI", 11, MUTED, true));
        LinearLayout a1 = row();
        TextView schedule = metric(a1, "RISET PAGI", "—");
        TextView guard = metric(a1, "PENGAMAN", "—");
        auto.addView(a1);
        auto.addView(text("Deduplikasi · SIAP", 13, OK, true));
        auto.addView(text("Kategorisasi · SIAP", 13, OK, true));
        auto.addView(text("Penilaian Tren · SIAP_V1", 13, OK, true));
        auto.addView(text("Telemetri Bridge · HANYA DATA · 0 AI / 0 Neuron", 13, OK, true));
        auto.addView(text("Penalaran Lokal · DITUNDA", 13, WARN, true));
        body.addView(auto);

        LinearLayout yt = card();
        yt.addView(text("KONEKSI YOUTUBE", 11, MUTED, true));
        TextView ytState = text("Status OAuth: memeriksa…", 13, WARN, true);
        ytState.setPadding(0, dp(6), 0, dp(2));
        yt.addView(ytState);
        TextView ytInfo = text("Hubungkan sekali menggunakan kredensial OAuth yang sudah dibuat. Secret dan refresh token tidak akan ditampilkan kembali.", 11, MUTED, false);
        ytInfo.setPadding(0, 0, 0, dp(4));
        yt.addView(ytInfo);

        EditText ytClient = field("OAuth Client ID",
                "910486209919-ruoq63jcu23rf2nrgn4hc7ou7j3cn3la.apps.googleusercontent.com", false);
        EditText ytSecret = field("OAuth Client Secret", "", true);
        EditText ytRefresh = field("Refresh Token", "", true);
        yt.addView(ytClient);
        yt.addView(ytSecret);
        yt.addView(ytRefresh);
        Button ytConnect = actionButton("HUBUNGKAN YOUTUBE", true);
        yt.addView(ytConnect);
        TextView ytOut = mono("Menunggu kredensial.");
        yt.addView(ytOut);
        Button ytPublish = actionButton("UJI UPLOAD PRIVATE", false);
        yt.addView(ytPublish);
        Button ytUnlisted = actionButton("UBAH KE UNLISTED", false);
        Button ytPublic = actionButton("PUBLIKASIKAN KE YOUTUBE", true);
        ytUnlisted.setVisibility(View.GONE); ytPublic.setVisibility(View.GONE);
        TextView ytVideoSummary = text("Video: memeriksa…", 12, MUTED, false); yt.addView(ytVideoSummary);
        Button ytManage = actionButton("KELOLA VIDEO", true); yt.addView(ytManage);
        TextView ytPublishOut = mono("Publisher: memeriksa…"); yt.addView(ytPublishOut);
        final String[] ytVideoId = {""};
        ytManage.setOnClickListener(v -> showYouTubeVideoManager());
        body.addView(yt);

        apiAsync("GET", "/api/work/youtube/videos?page=1&limit=10", null, false, (code, response) -> {
            if (code >= 200 && code < 300) try { JSONObject j=new JSONObject(response); JSONObject c=j.optJSONObject("counts"); int total=j.optInt("total",0); ytVideoSummary.setText("Video tersimpan: "+total+(c==null?"":" · PRIVATE "+c.optInt("private",0)+" · UNLISTED "+c.optInt("unlisted",0)+" · PUBLIC "+c.optInt("public",0))); } catch(Exception ignored){}
        });

        apiAsync("GET", "/api/work/youtube/oauth", null, false, (code, response) -> {
            try {
                JSONObject j = new JSONObject(response);
                String state = j.optString("state", "NOT_CONFIGURED");
                ytState.setText("Status OAuth: " + localizeStatus(state));
                ytState.setTextColor("VERIFIED".equals(state) ? OK : WARN);
                String verified = j.optString("verified_at", "");
                if (!verified.isEmpty()) {
                    String scope = j.optString("scope", "https://www.googleapis.com/auth/youtube.force-ssl");
                    if (scope.startsWith("https://www.googleapis.com/auth/")) scope = scope.substring("https://www.googleapis.com/auth/".length());
                    ytOut.setText("Terverifikasi: " + formatWibTime(verified) + "\nScope: " + scope);
                }
            } catch (Exception e) {
                ytState.setText("Status OAuth: belum tersedia");
                ytState.setTextColor(WARN);
            }
        });

        apiAsync("GET", "/api/work/youtube/publish", null, false, (code, response) -> {
            if (code >= 200 && code < 300) {
                try {
                    JSONObject j = new JSONObject(response);
                    String state = j.optString("state", "IDLE");
                    String topic = j.optString("next_topic", "");
                    ytVideoId[0] = j.optString("video_id", "");
                    String msg = "Publisher: " + localizeStatus(state);
                    if (!topic.isEmpty()) msg += "\nSiap diuji: " + topic;
                    if ("SUCCESS".equals(state)) {
                        String privacy = j.optString("privacy", "private").toUpperCase(Locale.US);
                        String status = j.optString("status", "");
                        if (status.startsWith("PRIVACY_")) msg += "\nPrivasi YouTube: " + privacy + ".";
                        else msg += "\nUpload " + privacy + " berhasil.";
                        String url = j.optString("url", "");
                        if (!url.isEmpty()) msg += "\n" + url;
                    } else if ("FAILED".equals(state)) {
                        msg += "\n" + j.optString("error", "Upload gagal.");
                    } else if ("UPLOADING".equals(state)) {
                        msg += "\nVideo sedang diunggah sebagai PRIVATE.";
                    }
                    ytPublishOut.setText(msg);
                } catch (Exception ignored) {}
            } else {
                ytPublishOut.setText("Publisher belum tersedia di runtime.");
            }
        });

        ytUnlisted.setOnClickListener(v -> {
            if (ytVideoId[0].isEmpty()) {
                ytPublishOut.setText("Belum ada video upload yang dapat diubah.");
                return;
            }
            try {
                JSONObject payload = new JSONObject();
                payload.put("video_id", ytVideoId[0]);
                payload.put("privacy", "unlisted");
                apiAsync("POST", "/api/work/youtube/privacy", payload.toString(), true, (code, response) -> {
                    if (code >= 200 && code < 300) ytPublishOut.setText("Privasi YouTube diubah ke UNLISTED.");
                    else ytPublishOut.setText("Gagal mengubah privasi. HTTP " + code + "\n" + response.trim());
                });
            } catch (Exception e) {
                ytPublishOut.setText("Gagal menyiapkan perubahan privasi.");
            }
        });

        ytPublic.setOnClickListener(v -> {
            if (ytVideoId[0].isEmpty()) {
                ytPublishOut.setText("Belum ada video upload yang dapat dipublikasikan.");
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("Publikasikan video?")
                    .setMessage("Video akan berubah dari PRIVATE/UNLISTED menjadi PUBLIC di YouTube.")
                    .setNegativeButton("BATAL", null)
                    .setPositiveButton("PUBLIKASIKAN", (d, which) -> {
                        try {
                            JSONObject payload = new JSONObject();
                            payload.put("video_id", ytVideoId[0]);
                            payload.put("privacy", "public");
                            apiAsync("POST", "/api/work/youtube/privacy", payload.toString(), true, (code, response) -> {
                                if (code >= 200 && code < 300) ytPublishOut.setText("Video sekarang PUBLIC di YouTube.");
                                else ytPublishOut.setText("Gagal mempublikasikan. HTTP " + code + "\n" + response.trim());
                            });
                        } catch (Exception e) {
                            ytPublishOut.setText("Gagal menyiapkan publikasi.");
                        }
                    })
                    .show();
        });

        ytPublish.setOnClickListener(v -> {
            if (token().isEmpty()) {
                ytPublishOut.setText("TOKEN ADMIN belum tersimpan. Buka PEMBARUAN → KONEKSI.");
                return;
            }
            ytPublish.setEnabled(false);
            ytPublishOut.setText("Memulai uji upload PRIVATE…");
            try {
                JSONObject payload = new JSONObject();
                payload.put("privacy", "private");
                apiAsync("POST", "/api/work/youtube/publish", payload.toString(), true, (code, response) -> {
                    ytPublish.setEnabled(true);
                    if (code == 202 || (code >= 200 && code < 300)) {
                        ytPublishOut.setText("Upload PRIVATE dimulai di Redmi 5A. Tekan tombol refresh di kanan atas beberapa saat lagi untuk melihat hasil.");
                    } else {
                        ytPublishOut.setText("Uji upload gagal dimulai. HTTP " + code + "\n" + response.trim());
                    }
                });
            } catch (Exception e) {
                ytPublish.setEnabled(true);
                ytPublishOut.setText("Gagal menyiapkan uji upload.");
            }
        });

        ytConnect.setOnClickListener(v -> {
            String client = ytClient.getText().toString().trim();
            String secret = ytSecret.getText().toString().trim();
            String refreshToken = ytRefresh.getText().toString().trim();
            if (token().isEmpty()) {
                ytOut.setText("TOKEN ADMIN belum tersimpan. Buka menu PEMBARUAN → KONEKSI, simpan TOKEN ADMIN, lalu kembali ke SISTEM.");
                return;
            }
            if (client.isEmpty() || secret.isEmpty() || refreshToken.isEmpty()) {
                ytOut.setText("Client ID, Client Secret, dan Refresh Token wajib diisi.");
                return;
            }
            ytConnect.setEnabled(false);
            ytOut.setText("Memverifikasi ke Google…");
            try {
                JSONObject payload = new JSONObject();
                payload.put("client_id", client);
                payload.put("client_secret", secret);
                payload.put("refresh_token", refreshToken);
                apiAsync("POST", "/api/work/youtube/oauth", payload.toString(), true, (code, response) -> {
                    ytConnect.setEnabled(true);
                    if (code >= 200 && code < 300) {
                        ytSecret.setText("");
                        ytRefresh.setText("");
                        ytState.setText("Status OAuth: TERVERIFIKASI");
                        ytState.setTextColor(OK);
                        ytOut.setText("YouTube terhubung. Secret disimpan aman di Redmi 5A dan tidak ditampilkan kembali.");
                    } else {
                        ytOut.setText("Gagal menghubungkan YouTube. HTTP " + code + "\n" + response.trim());
                    }
                });
            } catch (Exception e) {
                ytConnect.setEnabled(true);
                ytOut.setText("Gagal menyiapkan data OAuth.");
            }
        });

        LinearLayout comps = card();
        comps.addView(text("KOMPONEN", 11, MUTED, true));
        TextView comp = text("Memuat…", 13, TEXT, false);
        comp.setPadding(0, dp(8), 0, 0);
        comps.addView(comp);
        body.addView(comps);

        apiAsync("GET", "/api/work/status", null, false, (code, s) -> {
            try {
                JSONObject j = new JSONObject(s);
                setMetric(tether, dash(j.optString("tether_state")), "UP".equals(j.optString("tether_state")) ? OK : BAD);
                setMetric(ip, dash(j.optString("tether_ip")), TEXT);
                setMetric(temp, String.format(Locale.US, "%.1f °C", j.optDouble("temperature_c")), j.optDouble("temperature_c") >= 44 ? BAD : OK);
                setMetric(ram, j.optInt("mem_available_mb") + " MB", j.optInt("mem_available_mb") < 220 ? BAD : OK);
                String w = j.optBoolean("safe_mode") ? "SAFE MODE" : (j.optBoolean("worker_paused") ? "PAUSED" : "READY");
                setMetric(worker, w, "READY".equals(w) ? OK : WARN);
                String bs = j.optString("bridge_state", j.optBoolean("bridge_enabled") ? "STARTING" : "OFF");
                setMetric(bridge, bs, "CONNECTED".equals(bs) ? OK : ("OFF".equals(bs) ? MUTED : WARN));
                JSONObject c = j.optJSONObject("components");
                if (c != null) {
                    StringBuilder x = new StringBuilder();
                    Iterator<String> it = c.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        x.append(k.toUpperCase(Locale.US).replace('_',' ')).append("  ·  ").append(localizeStatus(c.optString(k))).append("\n");
                    }
                    comp.setText(x.toString().trim());
                }
            } catch (Exception ignored) {}
        });
        apiAsync("GET", "/api/work/schedule", null, false, (code, s) -> {
            try {
                JSONObject j = new JSONObject(s);
                setMetric(schedule, j.optBoolean("enabled") ? dash(j.optString("schedule")) : "OFF", TEXT);
                setMetric(guard, j.optBoolean("guard_ready") ? "READY" : dash(j.optString("guard_reason")), j.optBoolean("guard_ready") ? OK : WARN);
            } catch (Exception ignored) {}
        });
    }

    private void showYouTubeVideoManager() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(8), dp(12), dp(8));

        EditText search = field("Cari judul / ID video", "", false);
        box.addView(search);

        LinearLayout filters = row();
        Button filter = actionButton("FILTER: SEMUA", false);
        Button find = actionButton("CARI", false);
        setVideoManagerRowButton(filter, true);
        setVideoManagerRowButton(find, false);
        filters.addView(filter);
        filters.addView(find);
        box.addView(filters);

        TextView info = text("Memuat video…", 12, MUTED, false);
        box.addView(info);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        box.addView(scroll, new LinearLayout.LayoutParams(-1, dp(390)));

        LinearLayout pager = row();
        Button prev = actionButton("‹ SEBELUMNYA", false);
        Button next = actionButton("BERIKUTNYA ›", false);
        setVideoManagerRowButton(prev, true);
        setVideoManagerRowButton(next, false);
        prev.setEnabled(false);
        next.setEnabled(false);
        pager.addView(prev);
        pager.addView(next);
        box.addView(pager);

        final int[] page = {1};
        final String[] privacy = {"all"};
        final Runnable[] load = {null};

        load[0] = () -> {
            String q = search.getText().toString().trim();
            String pathApi = "/api/work/youtube/videos?page=" + page[0] + "&limit=10&privacy=" + privacy[0];
            if (!q.isEmpty()) {
                try { pathApi += "&q=" + java.net.URLEncoder.encode(q, "UTF-8"); }
                catch (Exception ignored) {}
            }
            apiAsync("GET", pathApi, null, false, (code, response) -> {
                list.removeAllViews();
                if (code < 200 || code >= 300) {
                    info.setText("Gagal memuat daftar video. HTTP " + code);
                    prev.setEnabled(page[0] > 1);
                    next.setEnabled(false);
                    return;
                }
                try {
                    JSONObject j = new JSONObject(response);
                    JSONArray items = j.optJSONArray("items");
                    JSONObject counts = j.optJSONObject("counts");
                    int total = j.optInt("total", 0);
                    boolean more = j.optBoolean("has_more", false);

                    String summary = "Halaman " + page[0] + " · " + total + " video";
                    if (counts != null && "all".equals(privacy[0])) {
                        summary += " · " + counts.optInt("private", 0) + " PRIVATE"
                                + " · " + counts.optInt("unlisted", 0) + " UNLISTED"
                                + " · " + counts.optInt("public", 0) + " PUBLIC";
                    }
                    info.setText(summary);
                    prev.setEnabled(page[0] > 1);
                    next.setEnabled(more);

                    if (items == null || items.length() == 0) {
                        list.addView(text("Tidak ada video pada filter ini.", 12, MUTED, false));
                        return;
                    }

                    for (int i = 0; i < items.length(); i++) {
                        JSONObject v = items.getJSONObject(i);
                        final String pid = v.optString("publication_id");
                        final String videoId = v.optString("video_id");
                        final String title = v.optString("topic", "Tanpa judul");
                        final String pr = v.optString("privacy", "private").toUpperCase(Locale.US);
                        String status = v.optString("status", "—");
                        String updated = formatWibTime(v.optString("updated_at", ""));
                        String thumbState = v.optString("thumbnail_state", "");

                        LinearLayout vc = card();
                        LinearLayout head = row();
                        head.setPadding(0, 0, 0, 0);

                        ImageView thumb = new ImageView(this);
                        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        thumb.setBackground(solidBg(Color.rgb(5, 17, 29), LINE, dp(9)));
                        LinearLayout.LayoutParams thumbLp = new LinearLayout.LayoutParams(dp(112), dp(63));
                        thumbLp.setMargins(0, 0, dp(10), 0);
                        head.addView(thumb, thumbLp);

                        LinearLayout meta = new LinearLayout(this);
                        meta.setOrientation(LinearLayout.VERTICAL);
                        meta.addView(text(title, 14, TEXT, true));
                        String detail = pr + " · " + status;
                        if (!thumbState.isEmpty()) {
                            String thumbLabel = "FAILED".equalsIgnoreCase(thumbState)
                                    ? "Thumbnail kustom: GAGAL · pratinjau YouTube tetap tersedia"
                                    : "Thumbnail kustom: " + localizeStatus(thumbState);
                            detail += "\n" + thumbLabel;
                        }
                        if (!updated.isEmpty()) detail += "\n" + updated;
                        meta.addView(text(detail, 11, MUTED, false));
                        head.addView(meta, new LinearLayout.LayoutParams(0, -2, 1f));
                        vc.addView(head);

                        Button pb = actionButton("UBAH PRIVASI", false);
                        vc.addView(pb);
                        if ("FAILED".equalsIgnoreCase(thumbState)) {
                            Button retryThumb = actionButton("ULANGI THUMBNAIL KUSTOM", false);
                            vc.addView(retryThumb);
                            retryThumb.setOnClickListener(x -> retryYouTubeThumbnail(pid, retryThumb, load[0]));
                        }
                        list.addView(vc);

                        pb.setOnClickListener(x -> showYouTubePrivacyDialog(pid, title, load[0]));
                        loadYouTubeThumbnail(thumb, videoId);
                    }
                } catch (Exception e) {
                    info.setText("Data Video Manager tidak valid · " + e.getClass().getSimpleName());
                }
            });
        };

        filter.setOnClickListener(v -> {
            String p = privacy[0];
            privacy[0] = p.equals("all") ? "private"
                    : p.equals("private") ? "unlisted"
                    : p.equals("unlisted") ? "public" : "all";
            filter.setText("FILTER: " + (privacy[0].equals("all") ? "SEMUA" : privacy[0].toUpperCase(Locale.US)));
            page[0] = 1;
            load[0].run();
        });
        find.setOnClickListener(v -> { page[0] = 1; load[0].run(); });
        search.setOnEditorActionListener((v, actionId, event) -> { page[0] = 1; load[0].run(); return true; });
        prev.setOnClickListener(v -> { if (page[0] > 1) { page[0]--; load[0].run(); } });
        next.setOnClickListener(v -> { if (next.isEnabled()) { page[0]++; load[0].run(); } });

        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("KELOLA VIDEO")
                .setView(box)
                .setNegativeButton("TUTUP", null)
                .create();
        d.setOnShowListener(x -> load[0].run());
        d.show();
    }

    private void setVideoManagerRowButton(Button b, boolean first) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1f);
        p.setMargins(first ? 0 : dp(4), dp(8), first ? dp(4) : 0, 0);
        b.setLayoutParams(p);
    }

    private void loadYouTubeThumbnail(ImageView view, String videoId) {
        if (videoId == null || videoId.trim().isEmpty()) return;
        final String id = videoId.trim();
        io.execute(() -> {
            HttpURLConnection c = null;
            try {
                URL u = new URL("https://i.ytimg.com/vi/" + java.net.URLEncoder.encode(id, "UTF-8") + "/mqdefault.jpg");
                c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(5000);
                c.setReadTimeout(7000);
                c.setRequestProperty("User-Agent", "DJAEGER-WORK/1.3.7");
                if (c.getResponseCode() != 200) return;
                final android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(c.getInputStream());
                if (bmp != null) runOnUiThread(() -> view.setImageBitmap(bmp));
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.disconnect();
            }
        });
    }

    private void retryYouTubeThumbnail(String publicationId, Button button, Runnable reload) {
        if (publicationId == null || publicationId.trim().isEmpty()) return;
        button.setEnabled(false);
        button.setText("MENGULANGI…");
        try {
            JSONObject p = new JSONObject();
            p.put("publication_id", publicationId);
            apiAsync("POST", "/api/work/youtube/thumbnail", p.toString(), true, (code, response) -> {
                button.setEnabled(true);
                button.setText("ULANGI THUMBNAIL KUSTOM");
                if (code >= 200 && code < 300) {
                    Toast.makeText(this, "Thumbnail kustom berhasil dipasang.", Toast.LENGTH_LONG).show();
                    reload.run();
                } else {
                    Toast.makeText(this, "Thumbnail belum berhasil · HTTP " + code, Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            button.setEnabled(true);
            button.setText("ULANGI THUMBNAIL KUSTOM");
        }
    }

    private void showYouTubePrivacyDialog(String publicationId,String title,Runnable reload){
        String[] opts={"PRIVATE","UNLISTED","PUBLIC"};new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(opts,-1,(dialog,which)->{String target=opts[which].toLowerCase(Locale.US);dialog.dismiss();Runnable apply=()->{try{JSONObject p=new JSONObject();p.put("publication_id",publicationId);p.put("privacy",target);apiAsync("POST","/api/work/youtube/privacy",p.toString(),true,(code,response)->{Toast.makeText(this,code>=200&&code<300?"Privasi diubah ke "+target.toUpperCase(Locale.US):"Gagal mengubah privasi · HTTP "+code,Toast.LENGTH_LONG).show();if(code>=200&&code<300)reload.run();});}catch(Exception ignored){}};if("public".equals(target))new AlertDialog.Builder(this).setTitle("Publikasikan video?").setMessage("Video ini akan menjadi PUBLIC di YouTube.").setNegativeButton("BATAL",null).setPositiveButton("PUBLIKASIKAN",(d,w)->apply.run()).show();else apply.run();}).setNegativeButton("BATAL",null).show();
    }

    private void buildUpdate(LinearLayout body) {
        body.addView(sectionTitle("Pembaruan & Pemulihan", "Halaman terakhir khusus pembaruan, cadangan, pengembalian versi, mode aman, dan diagnostik."));

        LinearLayout rel = card();
        rel.addView(text("STATUS RILIS", 11, MUTED, true));
        LinearLayout r = row();
        TextView current = metric(r, "SAAT INI", "—");
        TextView previous = metric(r, "TERAKHIR STABIL", "—");
        rel.addView(r);
        body.addView(rel);

        LinearLayout conn = card();
        conn.addView(text("KONEKSI", 11, MUTED, true));
        EditText runtime = field("URL Runtime", runtimeUrl(), false);
        EditText token = field("TOKEN ADMIN", token(), true);
        conn.addView(runtime);
        conn.addView(token);
        Button save = actionButton("SIMPAN KONEKSI", false);
        conn.addView(save);
        body.addView(conn);

        LinearLayout actions = card();
        actions.addView(text("PEMBARU", 11, MUTED, true));
        Button update = actionButton("PERBARUI KE VERSI TERBARU", true);
        Button backup = actionButton("CADANGKAN", false);
        Button rollback = actionButton("KEMBALIKAN VERSI", false);
        Button safe = actionButton("MODE AMAN", false);
        Button resume = actionButton("LANJUTKAN", false);
        Button recover = actionButton("PULIHKAN RUNTIME", false);
        Button diag = actionButton("DIAGNOSTIK", false);
        Button refreshReport = actionButton("SEGARKAN HASIL PEMBARUAN", false);
        Button copyReport = actionButton("SALIN HASIL UNTUK CHATGPT", true);
        actions.addView(update); actions.addView(backup); actions.addView(rollback);
        actions.addView(safe); actions.addView(resume); actions.addView(recover); actions.addView(diag);
        actions.addView(refreshReport); actions.addView(copyReport);
        TextView out = mono("Siap.\n\nSetelah pembaruan, tekan SEGARKAN HASIL PEMBARUAN, lalu SALIN HASIL UNTUK CHATGPT.");
        actions.addView(out);
        body.addView(actions);

        save.setOnClickListener(v -> {
            String u = runtime.getText().toString().trim();
            if (u.isEmpty()) u = DEFAULT_RUNTIME;
            prefs.edit().putString("runtime", clean(u)).putString("token", token.getText().toString().trim()).apply();
            out.setText("Koneksi tersimpan.");
            refreshOnlineOnly();
        });

        update.setOnClickListener(v -> {
            save.performClick();
            update.setEnabled(false);
            out.setText("Memasang rilis DJAEGER WORK terbaru yang terverifikasi…");
            bootstrapFallbackUpdate(out, update, current, previous);
        });

        backup.setOnClickListener(v -> action("backup", out, current, previous));
        rollback.setOnClickListener(v -> confirm("Kembalikan ke rilis stabil terakhir?", () -> action("rollback", out, current, previous)));
        safe.setOnClickListener(v -> confirm("Aktifkan Mode Aman? Beban kerja akan dijeda, modem/control plane tetap hidup.", () -> action("safe_mode", out, current, previous)));
        resume.setOnClickListener(v -> action("resume", out, current, previous));
        recover.setOnClickListener(v -> recover(out, current, previous));
        diag.setOnClickListener(v -> apiAsync("GET", "/api/work/diagnostics", null, true, (code, s) -> out.setText(s.trim())));
        refreshReport.setOnClickListener(v -> generateUpdateReport(out, null));
        copyReport.setOnClickListener(v -> generateUpdateReport(out, report -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Hasil pembaruan DJAEGER WORK", report));
            Toast.makeText(this, "Hasil pembaruan disalin. Tempelkan ke ChatGPT.", Toast.LENGTH_LONG).show();
        }));

        loadRecovery(current, previous);
    }

    private void bootstrapFallbackUpdate(TextView out, Button update, TextView current, TextView previous) {
        io.execute(() -> {
            try {
                String channelUrl = "https://raw.githubusercontent.com/Djaeger1/DJAEGER-WORK/main/release/channel.json";
                HttpResult ch = request("GET", channelUrl, null, false);
                if (ch.code != 200) throw new Exception("HTTP kanal " + ch.code);
                JSONObject j = new JSONObject(ch.body);
                String ver = j.getString("version");
                String bundle = j.getString("bundle");
                String sha = j.getString("sha256").toLowerCase(Locale.US);
                if (!ver.matches("[A-Za-z0-9._-]+") || !bundle.matches("[A-Za-z0-9._-]+") || !sha.matches("[0-9a-f]{64}")) {
                    throw new Exception("Metadata rilis tidak valid");
                }
                String raw = "https://raw.githubusercontent.com/Djaeger1/DJAEGER-WORK/main/release/" + bundle;
                String cmd =
                        "ROOT=/data/adb/hermes_work; " +
                        "VER='" + ver + "'; URL='" + raw + "'; SHA='" + sha + "'; " +
                        "mkdir -p \"$ROOT/updates\" \"$ROOT/releases\"; " +
                        "TMP=\"$ROOT/updates/$VER.zip\"; STAGE=\"$ROOT/releases/.stage-$VER\"; DEST=\"$ROOT/releases/$VER\"; " +
                        "rm -f \"$TMP\"; " +
                        "/system/bin/wget -qO \"$TMP\" \"$URL\" || exit 10; " +
                        "GOT=$(sha256sum \"$TMP\" 2>/dev/null | awk '{print $1}'); [ \"$GOT\" = \"$SHA\" ] || exit 11; " +
                        "rm -rf \"$STAGE\" \"$DEST.new\"; mkdir -p \"$STAGE\" \"$DEST.new\"; " +
                        "if command -v unzip >/dev/null 2>&1; then unzip -oq \"$TMP\" -d \"$STAGE\" || exit 12; " +
                        "elif [ -x /data/adb/magisk/busybox ]; then /data/adb/magisk/busybox unzip -oq \"$TMP\" -d \"$STAGE\" || exit 12; " +
                        "elif command -v busybox >/dev/null 2>&1; then busybox unzip -oq \"$TMP\" -d \"$STAGE\" || exit 12; " +
                        "else exit 13; fi; " +
                        "[ -f \"$STAGE/manifest.json\" ] && [ -x \"$STAGE/payload/bin/workd\" -o -f \"$STAGE/payload/bin/workd\" ] || exit 14; " +
                        "cp \"$STAGE/manifest.json\" \"$DEST.new/manifest.json\" || exit 15; " +
                        "cp -R \"$STAGE/payload/.\" \"$DEST.new/\" || exit 15; " +
                        "chmod 755 \"$DEST.new/bin/workd\" \"$DEST.new/worker/tick.sh\" \"$DEST.new/worker/handoff.sh\" 2>/dev/null; " +
                        "CUR=$(cat \"$ROOT/current_release\" 2>/dev/null); [ -n \"$CUR\" ] && [ \"$CUR\" != \"$VER\" ] && printf '%s\\n' \"$CUR\" > \"$ROOT/previous_release\"; " +
                        "rm -rf \"$DEST\"; mv \"$DEST.new\" \"$DEST\" || exit 16; printf '%s\\n' \"$VER\" > \"$ROOT/current_release\"; " +
                        "PREV=$(cat \"$ROOT/previous_release\" 2>/dev/null); " +
                        "sh \"$DEST/worker/handoff.sh\" \"$ROOT\" \"$VER\" \"$PREV\" || exit 17; " +
                        "sleep 2; /system/bin/wget -qO- http://127.0.0.1:8766/api/work/status || exit 18";
                JSONObject body = new JSONObject();
                body.put("command", cmd);
                HttpResult ex = request("POST", bootstrapUrl() + "/api/exec", body.toString(), true);
                if (ex.code < 200 || ex.code >= 300) throw new Exception("HTTP bootstrap " + ex.code + "\\n" + ex.body);
                ui(() -> {
                    out.setText("PEMBARUAN TERPASANG\\n\\nTarget: " + ver + "\\nMenunggu handoff runtime…");
                    refreshOnlineOnly();
                    out.postDelayed(() -> {
                        loadRecovery(current, previous);
                        apiAsync("GET", "/api/work/status", null, false, (code, bodyText) -> {
                            if (code >= 200 && code < 300) {
                                try {
                                    JSONObject st = new JSONObject(bodyText);
                                    String active = st.optString("release", "—");
                                    String state = ver.equals(active) ? "TERVERIFIKASI" : "MENUNGGU HANDOFF";
                                    out.setText("PEMBARUAN SELESAI\\n\\nRilis aktif: " + active + "\\nTarget: " + ver + "\\nStatus: " + state);
                                    if (ver.equals(active)) Toast.makeText(this, "Rilis terbaru aktif: " + ver, Toast.LENGTH_LONG).show();
                                } catch (Exception parseErr) {
                                    out.setText("Pembaruan terpasang. Runtime aktif, tetapi status belum dapat dibaca.");
                                }
                            } else {
                                out.setText("Pembaruan terpasang. Runtime sedang handoff; tekan ↻ beberapa detik lagi.");
                            }
                        });
                    }, 3500);
                    update.setEnabled(true);
                });
            } catch (Exception e) {
                ui(() -> {
                    out.setText("PEMBARUAN GAGAL\\n\\n" + e.getMessage());
                    update.setEnabled(true);
                });
            }
        });
    }

    private interface ReportCallback { void done(String report); }

    private JSONObject readRelayStatusSnapshot() {
        try {
            HttpResult r = requestWithTimeout("GET",
                    "https://hermes-work-chatgpt-relay-v3-production.up.railway.app/status-feed",
                    null, false, "", 5000, 12000);
            if (r.code < 200 || r.code >= 300) return null;
            JSONObject root = new JSONObject(r.body);
            JSONObject snap = root.optJSONObject("snapshot");
            if (snap == null) return null;
            snap.put("_relay_source", root.optString("source", "relay"));
            snap.put("_device_connected", root.optBoolean("device_connected", false));
            return snap;
        } catch (Exception ignored) { return null; }
    }

    private void generateUpdateReport(TextView out, ReportCallback cb) {
        out.setText("Mengumpulkan hasil pembaruan…");
        io.execute(() -> {
            StringBuilder report = new StringBuilder();
            report.append("===== HASIL PEMBARUAN DJAEGER WORK =====\n");
            report.append("APP_VERSION=1.3.9\n");
            report.append("RUNTIME_URL=").append(runtimeUrl()).append("\n");
            report.append("GENERATED_AT=").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new java.util.Date())).append("\n\n");

            try {
                HttpResult st = requestWithTimeout("GET", runtimeUrl() + "/api/work/status", null, false, "", 2500, 8000);
                report.append("[STATUS]\nHTTP=").append(st.code).append("\n");
                if (st.code == 200) {
                    JSONObject j = new JSONObject(st.body);
                    report.append("SERVICE=").append(j.optString("service","—")).append("\n");
                    report.append("RELEASE=").append(j.optString("release","—")).append("\n");
                    report.append("CONTROL_CENTER=").append(j.optString("control_center","—")).append("\n");
                    report.append("TETHER=").append(j.optString("tether_state","—")).append("\n");
                    report.append("TETHER_IP=").append(j.optString("tether_ip","—")).append("\n");
                    report.append("TEMP_C=").append(j.opt("temperature_c")).append("\n");
                    report.append("FREE_RAM_MB=").append(j.opt("mem_available_mb")).append("\n");
                    report.append("WORKER_PAUSED=").append(j.optBoolean("worker_paused")).append("\n");
                    report.append("SAFE_MODE=").append(j.optBoolean("safe_mode")).append("\n");
                    report.append("BRIDGE_ENABLED=").append(j.optBoolean("bridge_enabled")).append("\n");
                    report.append("BRIDGE_STATE=").append(j.optString("bridge_state","—")).append("\n");
                    report.append("BRIDGE_LAST_SYNC=").append(String.valueOf(j.opt("bridge_last_sync"))).append("\n");
                    report.append("BRIDGE_MODE=").append(j.optString("bridge_mode","—")).append("\n");
                    report.append("BRIDGE_AI_USED=").append(j.optBoolean("bridge_ai_used")).append("\n");
                    report.append("BRIDGE_NEURONS_USED=").append(j.optInt("bridge_neurons_used",-1)).append("\n");
                } else {
                    report.append("BODY=").append(compact(st.body, 1200)).append("\n");
                }
            } catch (Exception e) {
                JSONObject snap = readRelayStatusSnapshot();
                if (snap != null) {
                    report.append("[STATUS]\nHTTP=REMOTE_FALLBACK\n");
                    report.append("SOURCE=").append(snap.optString("_relay_source","relay")).append("\n");
                    report.append("DEVICE_CONNECTED=").append(snap.optBoolean("_device_connected")).append("\n");
                    report.append("SERVICE=HERMES_WORK\n");
                    report.append("RELEASE=").append(snap.optString("release","—")).append("\n");
                    report.append("TETHER=").append(snap.optString("tether_state","—")).append("\n");
                    report.append("TEMP_C=").append(snap.opt("temperature_c")).append("\n");
                    report.append("FREE_RAM_MB=").append(snap.opt("mem_available_mb")).append("\n");
                    report.append("WORKD_RSS_MB=").append(snap.opt("workd_rss_mb")).append("\n");
                    report.append("WORKER_STATE=").append(snap.optString("worker_state","—")).append("\n");
                    report.append("SAFE_MODE=").append(snap.optBoolean("safe_mode")).append("\n");
                    report.append("AUTO_UPDATE_STATE=").append(snap.optString("auto_update_state","—")).append("\n");
                    report.append("NOTE=Jalur lokal gagal; status diambil dari Railway read-only.\n");
                } else {
                    report.append("[STATUS]\nERROR=").append(e.getMessage()).append("\n");
                }
            }

            try {
                HttpResult rec = requestWithTimeout("GET", runtimeUrl() + "/api/work/recovery", null, false, "", 2500, 8000);
                report.append("\n[RECOVERY]\nHTTP=").append(rec.code).append("\n");
                if (rec.code == 200) {
                    JSONObject j = new JSONObject(rec.body);
                    report.append("CURRENT=").append(j.optString("current","—")).append("\n");
                    report.append("PREVIOUS=").append(j.optString("previous","—")).append("\n");
                    report.append("SAFE_MODE=").append(j.optBoolean("safe_mode")).append("\n");
                    report.append("WORKER_PAUSED=").append(j.optBoolean("worker_paused")).append("\n");
                } else {
                    report.append("BODY=").append(compact(rec.body, 1200)).append("\n");
                }
            } catch (Exception e) {
                report.append("\n[RECOVERY]\nLOCAL_UNREACHABLE=").append(e.getMessage()).append("\n");
                report.append("NOTE=Kontrol recovery memerlukan jalur lokal/remote terautentikasi.\n");
            }

            try {
                HttpResult br = requestWithTimeout("GET", runtimeUrl() + "/api/work/bridge", null, false, "", 2500, 8000);
                report.append("\n[BRIDGE]\nHTTP=").append(br.code).append("\n");
                if (br.code == 200) {
                    JSONObject j = new JSONObject(br.body);
                    report.append("STATE=").append(j.optString("state","—")).append("\n");
                    report.append("REASON=").append(j.optString("reason","—")).append("\n");
                    report.append("MODE=").append(j.optString("mode","—")).append("\n");
                    report.append("HTTP_CODE=").append(j.optInt("http_code",0)).append("\n");
                    report.append("LAST_SYNC=").append(j.optString("last_sync","—")).append("\n");
                    report.append("AI_USED=").append(j.optBoolean("ai_used")).append("\n");
                    report.append("NEURONS_USED=").append(j.optInt("neurons_used",-1)).append("\n");
                    report.append("UPDATED_AT=").append(j.optString("updated_at","—")).append("\n");
                } else {
                    report.append("BODY=").append(compact(br.body, 1200)).append("\n");
                }
            } catch (Exception e) {
                report.append("\n[BRIDGE]\nLOCAL_UNREACHABLE=").append(e.getMessage()).append("\n");
                report.append("NOTE=Status umum tetap tersedia melalui Railway read-only.\n");
            }

            report.append("\n===== AKHIR HASIL PEMBARUAN DJAEGER WORK =====");
            String finalReport = report.toString();
            ui(() -> {
                out.setText(finalReport);
                if (cb != null) cb.done(finalReport);
            });
        });
    }

    private String compact(String s, int max) {
        if (s == null) return "";
        String x = s.replace('\r',' ').replace('\n',' ').trim();
        return x.length() <= max ? x : x.substring(0, max) + "…";
    }

    private void loadRecovery(TextView current, TextView previous) {
        apiAsync("GET", "/api/work/recovery", null, false, (code, s) -> {
            try {
                JSONObject j = new JSONObject(s);
                setMetric(current, dash(j.optString("current")), TEXT);
                setMetric(previous, dash(j.optString("previous")), MUTED);
            } catch (Exception ignored) {}
        });
    }

    private void action(String a, TextView out, TextView current, TextView previous) {
        JSONObject j = new JSONObject();
        try { j.put("action", a); } catch (Exception ignored) {}
        apiAsync("POST", "/api/work/action", j.toString(), true, (code, s) -> {
            out.setText(s.trim());
            loadRecovery(current, previous);
            refreshOnlineOnly();
        });
    }

    private void recover(TextView out, TextView current, TextView previous) {
        out.setText("Memulihkan runtime…");
        io.execute(() -> {
            try {
                String cmd = "ROOT=/data/adb/hermes_work; VER=$(cat $ROOT/current_release 2>/dev/null); REL=$ROOT/releases/$VER; [ -x \"$REL/bin/workd\" ] || exit 7; PID=$ROOT/state/workd.pid; OLD=$(cat $PID 2>/dev/null); [ -n \"$OLD\" ] && kill \"$OLD\" 2>/dev/null; nohup \"$REL/bin/workd\" --root \"$ROOT\" --release \"$REL\" >>\"$ROOT/logs/workd.log\" 2>&1 & echo $! > \"$PID\"; sleep 2";
                JSONObject b = new JSONObject(); b.put("command", cmd);
                HttpResult r = request("POST", bootstrapUrl() + "/api/exec", b.toString(), true);
                ui(() -> {
                    out.setText(r.body.trim());
                    loadRecovery(current, previous);
                    refreshOnlineOnly();
                });
            } catch (Exception e) {
                ui(() -> out.setText("Pemulihan gagal: " + e.getMessage()));
            }
        });
    }

    private void setConnectionBadge(String route) {
        if ("LOCAL".equals(route)) {
            online.setText("● LOKAL");
            online.setTextColor(OK);
            online.setBackground(solidBg(Color.rgb(5, 34, 23), Color.rgb(19, 81, 49), dp(20)));
        } else if ("REMOTE".equals(route)) {
            online.setText("● REMOTE");
            online.setTextColor(ACCENT);
            online.setBackground(solidBg(Color.rgb(8, 26, 48), Color.rgb(31, 78, 128), dp(20)));
        } else if ("CHECKING".equals(route)) {
            online.setText("● MEMERIKSA");
            online.setTextColor(MUTED);
            online.setBackground(solidBg(Color.rgb(25, 28, 34), Color.rgb(48, 58, 72), dp(20)));
        } else {
            online.setText("● TERPUTUS");
            online.setTextColor(BAD);
            online.setBackground(solidBg(Color.rgb(42, 17, 25), Color.rgb(83, 40, 50), dp(20)));
        }
    }

    private void refreshOnlineOnly() {
        setConnectionBadge("CHECKING");
        apiAsync("GET", "/api/work/status", null, false, (code, s) -> {
            if (code < 200 || code >= 300) setConnectionBadge("OFFLINE");
        });
    }

    private interface ApiCallback { void done(int code, String body); }

    private void provisionRemotePair() {
        io.execute(() -> {
            try {
                HttpResult p = requestWithTimeout("GET", runtimeUrl() + "/api/work/remote", null, false, "", 2500, 7000);
                if (p.code < 200 || p.code >= 300) return;
                JSONObject j = new JSONObject(p.body);
                String u = clean(j.optString("remote_url", ""));
                String k = j.optString("remote_key", "").trim();
                if (!u.startsWith("https://") || k.length() < 32) return;
                prefs.edit().putString(PREF_REMOTE_URL, u).putString(PREF_REMOTE_KEY, k).apply();
            } catch (Exception ignored) {}
        });
    }

    private void apiAsync(String method, String path, String body, boolean auth, ApiCallback cb) {
        io.execute(() -> {
            Exception localError = null;
            try {
                HttpResult local = requestWithTimeout(method, runtimeUrl() + path, body, auth, "", 2500, 8000);
                if (local.code > 0) {
                    if (local.code >= 200 && local.code < 300) {
                        ui(() -> setConnectionBadge("LOCAL"));
                        if ("/api/work/status".equals(path)) provisionRemotePair();
                    }
                    final HttpResult out = local;
                    ui(() -> cb.done(out.code, out.body));
                    return;
                }
            } catch (Exception e) { localError = e; }

            String ru = remoteUrl();
            String rk = remoteKey();
            if (!ru.isEmpty() && !rk.isEmpty()) {
                try {
                    HttpResult remote = requestWithTimeout(method, ru + path, body, auth, rk, 5000, 45000);
                    if (remote.code > 0) {
                        if (remote.code >= 200 && remote.code < 300) ui(() -> setConnectionBadge("REMOTE"));
                        final HttpResult out = remote;
                        ui(() -> cb.done(out.code, out.body));
                        return;
                    }
                } catch (Exception ignored) {}
            }

            final String err = localError == null ? "jalur lokal dan remote tidak tersedia" : localError.getMessage();
            ui(() -> {
                setConnectionBadge("OFFLINE");
                cb.done(0, "GALAT: " + err);
            });
        });
    }

    private HttpResult request(String method, String target, String body, boolean auth) throws Exception {
        return requestWithTimeout(method, target, body, auth, "", 5000, 60000);
    }

    private HttpResult requestWithTimeout(String method, String target, String body, boolean auth, String remoteKey,
                                          int connectTimeout, int readTimeout) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(target).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(connectTimeout);
        c.setReadTimeout(readTimeout);
        c.setUseCaches(false);
        c.setRequestProperty("Accept", "application/json, text/plain, */*");
        if (auth) c.setRequestProperty("X-Hermes-Token", token());
        if (remoteKey != null && !remoteKey.isEmpty()) c.setRequestProperty("X-Djaeger-Remote-Key", remoteKey);
        if (body != null) {
            byte[] data = body.getBytes(StandardCharsets.UTF_8);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setFixedLengthStreamingMode(data.length);
            try (OutputStream os = c.getOutputStream()) { os.write(data); }
        } else if ("POST".equals(method)) {
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(0);
            try (OutputStream os = c.getOutputStream()) {}
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        StringBuilder sb = new StringBuilder();
        if (in != null) try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line; while ((line = br.readLine()) != null) sb.append(line).append('\n');
        }
        c.disconnect();
        return new HttpResult(code, sb.toString());
    }

    private String runtimeUrl() { return clean(prefs.getString("runtime", DEFAULT_RUNTIME)); }
    private String remoteUrl() {
        String s = prefs.getString(PREF_REMOTE_URL, "").trim();
        return s.isEmpty() ? "" : clean(s);
    }
    private String remoteKey() { return prefs.getString(PREF_REMOTE_KEY, "").trim(); }
    private String token() { return prefs.getString("token", "").trim(); }
    private String bootstrapUrl() {
        try {
            URL u = new URL(runtimeUrl());
            return u.getProtocol() + "://" + u.getHost() + ":8765";
        } catch (Exception e) { return DEFAULT_BOOTSTRAP; }
    }
    private String clean(String s) {
        s = s == null ? "" : s.trim();
        if (s.isEmpty()) s = DEFAULT_RUNTIME;
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://" + s;
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private java.util.Date parseIsoTime(String raw) {
        if (raw == null || raw.trim().isEmpty() || "—".equals(raw.trim())) return null;
        String v = raw.trim();
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'"
        };
        for (String pattern : patterns) {
            try {
                java.text.SimpleDateFormat in = new java.text.SimpleDateFormat(pattern, Locale.US);
                in.setLenient(false);
                if (pattern.endsWith("'Z'")) in.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                return in.parse(v);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String formatWibTime(String raw) {
        java.util.Date d = parseIsoTime(raw);
        if (d == null) return dash(raw);
        java.text.SimpleDateFormat out = new java.text.SimpleDateFormat("dd MMM yyyy · HH:mm 'WIB'", new Locale("id", "ID"));
        out.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Jakarta"));
        return out.format(d);
    }

    private String formatWibDate(String raw) {
        java.util.Date d = parseIsoTime(raw);
        if (d == null) return dash(raw);
        java.text.SimpleDateFormat out = new java.text.SimpleDateFormat("dd MMM yyyy", new Locale("id", "ID"));
        out.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Jakarta"));
        return out.format(d);
    }

    private LinearLayout card() {
        LinearLayout x = new LinearLayout(this);
        x.setOrientation(LinearLayout.VERTICAL);
        x.setPadding(dp(14), dp(14), dp(14), dp(14));
        x.setBackground(gradientBg(Color.rgb(10, 29, 49), Color.rgb(6, 19, 32), dp(18)));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 0, 0, dp(12));
        x.setLayoutParams(p);
        return x;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(0, dp(8), 0, 0);
        return r;
    }

    private TextView metric(LinearLayout row, String label, String initial) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(11), dp(10), dp(11), dp(10));
        box.setBackground(gradientBg(Color.rgb(7, 21, 35), Color.rgb(5, 17, 29), dp(11)));
        TextView k = text(label, 10, MUTED, true);
        TextView v = text(initial, 17, TEXT, true);
        v.setPadding(0, dp(4), 0, 0);
        box.addView(k);
        box.addView(v);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
        p.setMargins(0, 0, dp(6), dp(6));
        row.addView(box, p);
        return v;
    }

    private View sectionTitle(String title, String sub) {
        LinearLayout x = new LinearLayout(this);
        x.setOrientation(LinearLayout.VERTICAL);
        x.setPadding(dp(2), dp(5), dp(2), dp(13));
        x.addView(text(title, 23, TEXT, true));
        TextView s = text(sub, 13, MUTED, false);
        s.setPadding(0, dp(4), 0, 0);
        x.addView(s);
        return x;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private TextView mono(String s) {
        TextView v = text(s, 12, Color.rgb(205, 236, 212), false);
        v.setTypeface(Typeface.MONOSPACE);
        v.setTextIsSelectable(true);
        v.setPadding(dp(10), dp(10), dp(10), dp(10));
        v.setBackground(solidBg(Color.rgb(3, 9, 15), Color.rgb(18, 45, 70), dp(10)));
        return v;
    }

    private Button actionButton(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setBackground(primary
                ? gradientBg(Color.rgb(37, 111, 222), Color.rgb(49, 137, 255), dp(11))
                : gradientBg(Color.rgb(22, 37, 56), Color.rgb(15, 28, 44), dp(11)));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(48));
        p.setMargins(0, dp(8), 0, 0);
        b.setLayoutParams(p);
        return b;
    }

    private Button navButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(8);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(1), 0, dp(1), 0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        return b;
    }

    private EditText field(String hint, String value, boolean secret) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        e.setSingleLine(true);
        e.setBackground(solidBg(Color.rgb(6, 19, 32), Color.rgb(31, 70, 107), dp(10)));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        if (secret) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(50));
        p.setMargins(0, dp(8), 0, 0);
        e.setLayoutParams(p);
        return e;
    }

    private GradientDrawable gradientBg(int start, int end, float radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        g.setCornerRadius(radius);
        if (radius > 0) g.setStroke(dp(1), LINE);
        return g;
    }

    private GradientDrawable solidBg(int fill, int stroke, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radius);
        if (stroke != Color.TRANSPARENT) g.setStroke(dp(1), stroke);
        return g;
    }

    private String localizeCategory(String s) {
        if (s == null) return "";
        switch (s.trim().toLowerCase(Locale.US)) {
            case "numbers": return "angka";
            case "colors": return "warna";
            case "alphabet": return "alfabet";
            case "animals": return "hewan";
            case "shapes": return "bentuk";
            case "habits": return "kebiasaan";
            case "english": return "bahasa Inggris";
            case "stories": return "cerita";
            case "other": return "lainnya";
            default: return s;
        }
    }

    private String localizeWhy(String s) {
        if (s == null) return "—";
        if (s.equals("High demand signal from search suggestions; prioritized for today's queue."))
            return "Sinyal permintaan tinggi dari saran pencarian; diprioritaskan untuk antrean hari ini.";
        if (s.equals("Repeated search interest with usable educational intent; good candidate for testing."))
            return "Minat pencarian berulang dengan tujuan edukasi yang jelas; kandidat yang baik untuk diuji.";
        if (s.equals("Relevant educational query in a target category; keep as a discovery test."))
            return "Pencarian edukatif yang relevan dalam kategori sasaran; pertahankan sebagai uji eksplorasi.";
        if (s.equals("Discovery query kept for exploration; lower priority than core learning categories."))
            return "Pencarian eksplorasi dipertahankan untuk pengujian; prioritasnya di bawah kategori pembelajaran utama.";
        return s;
    }

    private String localizeFormat(String s) {
        if (s == null) return "—";
        switch (s.trim().toUpperCase(Locale.US)) {
            case "QUIZ + REPETITION": return "KUIS + PENGULANGAN";
            case "SONG/CHANT": return "LAGU / NYANYIAN";
            case "SHORT STORY": return "CERITA PENDEK";
            case "REPEAT-AFTER-ME": return "ULANGI SETELAH SAYA";
            case "NAME + SOUND + GUESS": return "NAMA + SUARA + TEBAK";
            case "MINI STORY + MODELING": return "CERITA MINI + CONTOH";
            case "SHORT EXPLAINER + REPETITION": return "PENJELASAN SINGKAT + PENGULANGAN";
            default: return s;
        }
    }

    private String localizeStatus(String s) {
        if (s == null) return "—";
        String x = s.trim();
        switch (x.toUpperCase(Locale.US)) {
            case "UP": return "AKTIF";
            case "READY": return "SIAP";
            case "SAFE MODE": return "MODE AMAN";
            case "PAUSED": return "DIJEDA";
            case "CONNECTED": return "TERHUBUNG";
            case "STARTING": return "MEMULAI";
            case "OFF": return "MATI";
            case "NOT CONNECTED": return "BELUM TERHUBUNG";
            case "HIGH": return "TINGGI";
            case "MEDIUM": return "SEDANG";
            case "DISCOVERY": return "EKSPLORASI";
            case "DEFERRED": return "DITUNDA";
            case "PUBLISHED": return "DITERBITKAN";
            case "HOLD": return "DITAHAN";
            default: return x;
        }
    }

    private void setMetric(TextView v, String s, int color) {
        v.setText(localizeStatus(dash(s)));
        v.setTextColor(color);
    }

    private String dash(String s) { return (s == null || s.trim().isEmpty()) ? "—" : s; }
    private String value(JSONObject j, String k) {
        Object o = j.opt(k);
        return o == null || o == JSONObject.NULL ? "—" : String.valueOf(o);
    }
    private String val(JSONObject j, String k) { return value(j, k); }

    private void confirm(String msg, Runnable yes) {
        new AlertDialog.Builder(this).setTitle("DJAEGER WORK").setMessage(msg)
                .setNegativeButton("BATAL", null)
                .setPositiveButton("LANJUTKAN", (d, w) -> yes.run()).show();
    }

    private void ui(Runnable r) { if (!destroyed) runOnUiThread(r); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override protected void onDestroy() {
        destroyed = true;
        io.shutdownNow();
        super.onDestroy();
    }

    static class HttpResult {
        final int code; final String body;
        HttpResult(int c, String b) { code = c; body = b == null ? "" : b; }
    }
}
