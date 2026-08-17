package com.wellsound24.wellhondaobd;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends Activity {
    private static final String PREFS = "well_honda_obd";
    private static final String KEY_URL = "server_url";
    private static final int PORT = 8765;

    private WebView webView;
    private EditText urlBox;
    private TextView stateText;
    private Button findButton;
    private volatile boolean scanning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(11,15,20));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(12),dp(10),dp(12),dp(8));

        TextView title = new TextView(this);
        title.setText("WELL HONDA OBD");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        top.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Forza 350 • Android Live Dashboard v1.1 (Read-only)");
        subtitle.setTextColor(Color.rgb(154,166,178));
        subtitle.setTextSize(12);
        top.addView(subtitle);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(8);
        top.addView(row, rp);

        urlBox = new EditText(this);
        urlBox.setSingleLine(true);
        urlBox.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlBox.setText(prefs.getString(KEY_URL, ""));
        urlBox.setHint("IP คอม เช่น 192.168.1.5:8765");
        urlBox.setTextColor(Color.WHITE);
        urlBox.setHintTextColor(Color.GRAY);
        urlBox.setTextSize(13);
        urlBox.setPadding(dp(10),dp(7),dp(10),dp(7));
        row.addView(urlBox, new LinearLayout.LayoutParams(0, dp(44), 1f));

        Button connect = new Button(this);
        connect.setText("เชื่อมต่อ");
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(100), dp(44));
        bp.leftMargin = dp(8);
        row.addView(connect, bp);

        findButton = new Button(this);
        findButton.setText("ค้นหาคอมอัตโนมัติ");
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        fp.topMargin = dp(6);
        top.addView(findButton, fp);

        stateText = new TextView(this);
        stateText.setText("กด “ค้นหาคอมอัตโนมัติ” โดยให้มือถือและคอมอยู่ Wi‑Fi วงเดียวกัน");
        stateText.setTextColor(Color.rgb(154,166,178));
        stateText.setTextSize(11);
        top.addView(stateText);

        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        webView = new WebView(this);
        configureWebView();
        root.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));
        setContentView(root);

        connect.setOnClickListener(v -> connectToServer());
        findButton.setOnClickListener(v -> autoFindServer());

        if (urlBox.getText().toString().trim().isEmpty()) autoFindServer();
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        webView.setBackgroundColor(Color.rgb(11,15,20));
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !isAllowedLocalUrl(request.getUrl().toString());
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                stateText.setText("เชื่อมต่อแล้ว: " + url);
            }
        });
    }

    private void autoFindServer() {
        if (scanning) return;
        scanning = true;
        findButton.setEnabled(false);
        stateText.setText("กำลังค้นหา Well Honda OBD บน Wi‑Fi...");

        new Thread(() -> {
            String localIp = getLocalPrivateIpv4();
            if (localIp == null || localIp.lastIndexOf('.') < 0) {
                runOnUiThread(() -> finishScan(null, "ไม่พบ IP Wi‑Fi ของมือถือ"));
                return;
            }

            String prefix = localIp.substring(0, localIp.lastIndexOf('.') + 1);
            ExecutorService pool = Executors.newFixedThreadPool(32);
            AtomicBoolean found = new AtomicBoolean(false);
            AtomicInteger done = new AtomicInteger(0);

            for (int i = 1; i <= 254; i++) {
                final String host = prefix + i;
                pool.submit(() -> {
                    try {
                        if (!found.get() && probe(host)) {
                            if (found.compareAndSet(false, true)) {
                                runOnUiThread(() -> finishScan("http://" + host + ":" + PORT + "/", null));
                                pool.shutdownNow();
                                return;
                            }
                        }
                    } finally {
                        if (done.incrementAndGet() >= 254 && !found.get()) {
                            runOnUiThread(() -> finishScan(null, "ไม่พบคอมที่เปิด Well Honda OBD v1.2.1 ใน Wi‑Fi นี้"));
                            pool.shutdown();
                        }
                    }
                });
            }
        }).start();
    }

    private boolean probe(String host) {
        HttpURLConnection c = null;
        try {
            URL u = new URL("http://" + host + ":" + PORT + "/api/live");
            c = (HttpURLConnection)u.openConnection();
            c.setConnectTimeout(280);
            c.setReadTimeout(400);
            c.setRequestMethod("GET");
            int code = c.getResponseCode();
            if (code != 200) return false;
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
            String line = r.readLine();
            return line != null && line.contains("\"status\"");
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private void finishScan(String url, String error) {
        scanning = false;
        findButton.setEnabled(true);
        if (url != null) {
            urlBox.setText(url);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_URL, url).apply();
            stateText.setText("พบคอมแล้ว: " + url + " กำลังเชื่อมต่อ...");
            webView.loadUrl(url);
        } else {
            stateText.setText(error);
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
        }
    }

    private String getLocalPrivateIpv4() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                String name = ni.getName() == null ? "" : ni.getName().toLowerCase();
                if (!name.startsWith("wlan") && !name.startsWith("wifi")) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && isPrivate(a.getHostAddress())) return a.getHostAddress();
                }
            }
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && isPrivate(a.getHostAddress())) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private boolean isPrivate(String host) {
        if (host == null) return false;
        if (host.startsWith("10.") || host.startsWith("192.168.")) return true;
        if (host.startsWith("172.")) {
            try {
                String[] p = host.split("\\.");
                int second = Integer.parseInt(p[1]);
                return second >= 16 && second <= 31;
            } catch (Exception ignored) {}
        }
        return false;
    }

    private void connectToServer() {
        String raw = urlBox.getText().toString().trim();
        if (raw.isEmpty()) { autoFindServer(); return; }
        if (!raw.startsWith("http://")) raw = "http://" + raw;
        if (!raw.contains(":" + PORT)) {
            try {
                URI u = new URI(raw);
                if (u.getPort() < 0) raw = "http://" + u.getHost() + ":" + PORT + "/";
            } catch (Exception ignored) {}
        }
        if (!raw.endsWith("/")) raw += "/";
        if (!isAllowedLocalUrl(raw)) {
            Toast.makeText(this,"ใช้ IP ภายในของคอมเท่านั้น",Toast.LENGTH_LONG).show();
            return;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_URL, raw).apply();
        stateText.setText("กำลังเชื่อมต่อ...");
        webView.loadUrl(raw);
    }

    private boolean isAllowedLocalUrl(String value) {
        try {
            URI u = new URI(value);
            String host = u.getHost();
            return "http".equalsIgnoreCase(u.getScheme()) && host != null && (isPrivate(host) || host.equals("127.0.0.1") || host.equalsIgnoreCase("localhost"));
        } catch (Exception e) { return false; }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    private int dp(int v) {
        return (int)(v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
