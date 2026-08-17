package com.wellsound24.wellhondaobd;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
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

import java.net.URI;

public class MainActivity extends Activity {
    private static final String PREFS = "well_honda_obd";
    private static final String KEY_URL = "server_url";
    private static final String DEFAULT_URL = "http://192.168.1.2:8765/";

    private WebView webView;
    private EditText urlBox;
    private TextView stateText;

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
        subtitle.setText("Forza 350 • Android Live Dashboard (Read-only)");
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
        urlBox.setText(prefs.getString(KEY_URL, DEFAULT_URL));
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

        stateText = new TextView(this);
        stateText.setText("ใส่ IP ที่โปรแกรมบนคอมแสดง แล้วกดเชื่อมต่อ");
        stateText.setTextColor(Color.rgb(154,166,178));
        stateText.setTextSize(11);
        top.addView(stateText);

        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        webView = new WebView(this);
        configureWebView();
        root.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));
        setContentView(root);

        connect.setOnClickListener(v -> connectToServer());
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
                String value = request.getUrl().toString();
                if (isAllowedLocalUrl(value)) return false;
                Toast.makeText(MainActivity.this,"อนุญาตเฉพาะเซิร์ฟเวอร์ Well Honda OBD ในเครือข่ายภายใน",Toast.LENGTH_SHORT).show();
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                stateText.setText("เชื่อมต่อ: " + url);
            }
        });
    }

    private void connectToServer() {
        String raw = urlBox.getText().toString().trim();
        if (!raw.startsWith("http://") && !raw.startsWith("https://")) raw = "http://" + raw;
        if (!raw.endsWith("/")) raw += "/";
        if (!isAllowedLocalUrl(raw)) {
            Toast.makeText(this,"กรุณาใช้ IP ภายใน เช่น 192.168.x.x:8765",Toast.LENGTH_LONG).show();
            return;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_URL, raw).apply();
        stateText.setText("กำลังเชื่อมต่อ...");
        webView.loadUrl(raw);
    }

    private boolean isAllowedLocalUrl(String value) {
        try {
            URI u = new URI(value);
            String scheme = u.getScheme();
            String host = u.getHost();
            if (scheme == null || host == null || !scheme.equalsIgnoreCase("http")) return false;
            if (host.equals("127.0.0.1") || host.equalsIgnoreCase("localhost")) return true;
            if (host.startsWith("10.") || host.startsWith("192.168.")) return true;
            if (host.startsWith("172.")) {
                String[] p = host.split("\\.");
                if (p.length == 4) {
                    int second = Integer.parseInt(p[1]);
                    return second >= 16 && second <= 31;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    private int dp(int v) {
        return (int)(v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
