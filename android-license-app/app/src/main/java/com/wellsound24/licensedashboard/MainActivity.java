package com.wellsound24.licensedashboard;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.Context;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {
    private WebView webView;

    public class AndroidBridge {
        @JavascriptInterface
        public void copyText(String text) {
            runOnUiThread(() -> {
                try {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText("Well License", text == null ? "" : text));
                    Toast.makeText(MainActivity.this, "คัดลอก License แล้ว", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "คัดลอกไม่สำเร็จ", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setJavaScriptCanOpenWindowsAutomatically(false);

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectStableAndroidPatch(view);
            }
        });

        webView.loadUrl("https://welllicensedashboardnewvercel.vercel.app/?android=2");
    }

    private void injectStableAndroidPatch(WebView v) {
        String js = "(function(){try{" +
            "if(window.__wellAndroidStablePatch)return;window.__wellAndroidStablePatch=true;" +

            // Native clipboard. IMPORTANT: reference page lexical variables directly, not window.licenses.
            "window.copyLicense=function(id){try{" +
                "var l=licenses.find(function(x){return String(x.id)===String(id)});" +
                "if(!l){if(typeof toast==='function')toast('ไม่พบ License');return;}" +
                "var p=(typeof currentProject==='function')?currentProject():null;" +
                "var value=(p&&p.product_code==='WELL_AUTO_EQ_PRO')?(l.activation_email||''):(l.license_key||'');" +
                "if(!value){if(typeof toast==='function')toast('ไม่มี License ให้คัดลอก');return;}" +
                "AndroidBridge.copyText(String(value));" +
            "}catch(e){if(typeof toast==='function')toast('คัดลอกไม่สำเร็จ');}};" +

            // Put copy button directly under the visible license/email so it is usable on narrow phones.
            "window.licenseRows=function(ls){return (ls||[]).map(function(l){" +
                "var d=daysLeft(l.expires_at);" +
                "var status=l.enabled===false?'<span class=\"badge bad\">ปิด</span>':(d!==null&&d<0?'<span class=\"badge bad\">หมดอายุ</span>':(d!==null&&d<=7?'<span class=\"badge warn\">ใกล้หมดอายุ</span>':'<span class=\"badge good\">ใช้งาน</span>'));" +
                "var p=(typeof currentProject==='function')?currentProject():null;" +
                "var shown=(p&&p.product_code==='WELL_AUTO_EQ_PRO')?(l.activation_email||'-'):(l.license_key||'-');" +
                "var q=JSON.stringify(l.id);" +
                "return '<tr><td><b>'+esc(l.customer_name||'-')+'</b><br><span class=\"muted\">'+esc(shown)+'</span><br><button class=\"btn small\" style=\"margin-top:7px\" onclick=\"copyLicense('+q+')\">คัดลอก License</button></td><td>'+esc(l.mt5_login||l.activation_email||'-')+'</td><td>'+esc(l.broker||l.product_code||'-')+'<br><span class=\"muted\">'+esc(l.server||'')+'</span></td><td>'+esc(l.expires_at||'-')+'</td><td>'+status+'</td><td><div class=\"row-actions\"><button class=\"btn small\" onclick=\"openLicenseModal('+q+')\">แก้ไข</button><button class=\"btn small '+(l.enabled===false?'primary':'')+'\" onclick=\"toggleLicense('+q+','+(l.enabled===false)+')\">'+(l.enabled===false?'เปิด':'ปิด')+'</button></div></td></tr>';" +
            "}).join('')||'<tr><td colspan=\"6\" class=\"muted\">ไม่มี License ในโปรเจกต์นี้</td></tr>';};" +

            // Do NOT replace switchView/selectProject/nav handlers. Preserve original app navigation.
            "function syncProjectButton(){try{" +
                "var active=document.querySelector('.nav button.active');" +
                "var view=active&&active.dataset?active.dataset.view:'';" +
                "var btn=document.getElementById('newProjectBtn');" +
                "if(btn)btn.classList.toggle('hidden',view!=='projects');" +
            "}catch(e){}}" +

            "document.querySelectorAll('.nav button').forEach(function(b){" +
                "b.addEventListener('click',function(){setTimeout(function(){syncProjectButton();if(b.dataset.view==='licenses'&&typeof renderLicenses==='function')renderLicenses();},0);},false);" +
            "});" +
            "var observer=new MutationObserver(function(){syncProjectButton();});" +
            "var nav=document.querySelector('.nav');if(nav)observer.observe(nav,{subtree:true,attributes:true,attributeFilter:['class']});" +
            "syncProjectButton();" +
            "if(typeof renderLicenses==='function')renderLicenses();" +

            // lightweight runtime self-check; no UI mutation except warning toast if a required target is missing.
            "var required=['dashboard','licenses','customers','projects','settings'];" +
            "var missing=required.filter(function(n){return !document.getElementById('view-'+n)||!document.querySelector('.nav button[data-view=\"'+n+'\"]');});" +
            "if(missing.length&&typeof toast==='function')toast('เมนูไม่ครบ: '+missing.join(','));" +
            "}catch(e){console.log('Well Android stable patch',e);}})();";
        v.evaluateJavascript(js, null);
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
}
