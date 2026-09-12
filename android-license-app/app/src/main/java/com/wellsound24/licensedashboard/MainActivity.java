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

        webView.loadUrl("https://welllicensedashboardnewvercel.vercel.app/?android=5");
    }

    private void injectStableAndroidPatch(WebView v) {
        String js = "(function(){try{" +
            "if(window.__wellAndroidV5)return;window.__wellAndroidV5=true;" +

            // Copy License: same visible button, Android-native copy underneath.
            "window.copyLicense=function(id){try{" +
                "var l=licenses.find(function(x){return String(x.id)===String(id)});" +
                "if(!l){if(typeof toast==='function')toast('ไม่พบ License');return;}" +
                "var p=(typeof currentProject==='function')?currentProject():null;" +
                "var value=(p&&p.product_code==='WELL_AUTO_EQ_PRO')?(l.activation_email||''):(l.license_key||'');" +
                "if(!value){if(typeof toast==='function')toast('ไม่มี License ให้คัดลอก');return;}" +
                "AndroidBridge.copyText(String(value));" +
            "}catch(e){if(typeof toast==='function')toast('คัดลอกไม่สำเร็จ');}};" +

            // Fix row action quoting on Android: encoded IDs keep Copy/Edit/Enable buttons clickable.
            "window.licenseRows=function(ls){return (ls||[]).map(function(l){" +
                "var d=daysLeft(l.expires_at);" +
                "var status=l.enabled===false?'<span class=\"badge bad\">ปิด</span>':(d!==null&&d<0?'<span class=\"badge bad\">หมดอายุ</span>':(d!==null&&d<=7?'<span class=\"badge warn\">ใกล้หมดอายุ</span>':'<span class=\"badge good\">ใช้งาน</span>'));" +
                "var p=(typeof currentProject==='function')?currentProject():null;" +
                "var shown=(p&&p.product_code==='WELL_AUTO_EQ_PRO')?(l.activation_email||''):(l.license_key||'');" +
                "var eid=encodeURIComponent(String(l.id));" +
                "var display=esc(shown||'-');" +
                "return '<tr><td><b>'+esc(l.customer_name||'-')+'</b><div style=\"display:flex;gap:7px;align-items:center;margin-top:8px;min-width:280px\"><input class=\"input\" readonly value=\"'+display+'\" style=\"min-width:0;flex:1;padding:8px 9px\"><button class=\"btn small primary\" style=\"white-space:nowrap\" onclick=\"copyLicense(decodeURIComponent(\\\''+eid+'\\\'))\">คัดลอก License</button></div></td><td>'+esc(l.mt5_login||l.activation_email||'-')+'</td><td>'+esc(l.broker||l.product_code||'-')+'<br><span class=\"muted\">'+esc(l.server||'')+'</span></td><td>'+esc(l.expires_at||'-')+'</td><td>'+status+'</td><td><div class=\"row-actions\"><button class=\"btn small\" onclick=\"openLicenseModal(decodeURIComponent(\\\''+eid+'\\\'))\">แก้ไข</button><button class=\"btn small '+(l.enabled===false?'primary':'')+'\" onclick=\"toggleLicense(decodeURIComponent(\\\''+eid+'\\\'),'+(l.enabled===false)+')\">'+(l.enabled===false?'เปิด':'ปิด')+'</button></div></td></tr>';" +
            "}).join('')||'<tr><td colspan=\"6\" class=\"muted\">ไม่มี License ในโปรเจกต์นี้</td></tr>';};" +

            // Delete License only from inside Edit. Uses archive so audit/history is retained.
            "window.deleteLicenseFromEdit=async function(id){try{" +
                "if(!id)return;if(!confirm('ยืนยันลบ License นี้?'))return;" +
                "await call(API.core,{action:'archive',id:id});" +
                "if(typeof closeModal==='function')closeModal();" +
                "if(typeof toast==='function')toast('ลบ License แล้ว');" +
                "if(typeof refresh==='function')await refresh();" +
            "}catch(e){if(typeof toast==='function')toast('ลบ License ไม่สำเร็จ');}};" +

            // Wrap Edit License after the original page has defined it.
            "var originalOpenLicenseModal=window.openLicenseModal;" +
            "if(typeof originalOpenLicenseModal==='function'){window.openLicenseModal=function(id){" +
                "var r=originalOpenLicenseModal(id);" +
                "if(id){setTimeout(function(){try{" +
                    "var actions=document.querySelector('#modalRoot .modal-actions');" +
                    "if(actions&&!actions.querySelector('[data-delete-license]')){" +
                        "var b=document.createElement('button');b.type='button';b.className='btn danger';b.setAttribute('data-delete-license','1');b.textContent='ลบ License';" +
                        "b.onclick=function(){window.deleteLicenseFromEdit(id)};actions.insertBefore(b,actions.firstChild);" +
                    "}" +
                "}catch(e){}},60);}" +
                "return r;" +
            "};}" +

            // Customer list: remove standalone Delete button. Keep only Edit on each row.
            "window.customerRows=function(rows){return (rows||[]).map(function(c){" +
                "var eid=encodeURIComponent(String(c.id));" +
                "return '<tr><td><b>'+esc(c.name||'-')+'</b></td><td>'+esc(c.phone||'-')+'</td><td>'+esc(c.email||'-')+'</td><td>'+esc(c.line_id||'-')+'</td><td>'+(c.license_count||0)+'</td><td><div class=\"row-actions\"><button class=\"btn small\" onclick=\"openCustomerModal(decodeURIComponent(\\\''+eid+'\\\'))\">แก้ไข</button></div></td></tr>';" +
            "}).join('')||'<tr><td colspan=\"6\" class=\"muted\">ไม่มีลูกค้าในโปรเจกต์นี้</td></tr>';};" +

            // Customer Delete button lives inside Edit modal only.
            "var originalOpenCustomerModal=window.openCustomerModal;" +
            "if(typeof originalOpenCustomerModal==='function'){window.openCustomerModal=function(id){" +
                "var r=originalOpenCustomerModal(id);" +
                "if(id){setTimeout(function(){try{" +
                    "var actions=document.querySelector('#modalRoot .modal-actions');" +
                    "if(actions&&!actions.querySelector('[data-delete-customer]')){" +
                        "var b=document.createElement('button');b.type='button';b.className='btn danger';b.setAttribute('data-delete-customer','1');b.textContent='ลบลูกค้า';" +
                        "b.onclick=function(){if(typeof window.deleteCustomer==='function')window.deleteCustomer(id)};actions.insertBefore(b,actions.firstChild);" +
                    "}" +
                "}catch(e){}},60);}" +
                "return r;" +
            "};}" +

            // Preserve original navigation. New Project appears only on Projects page.
            "function syncProjectButton(){try{" +
                "var active=document.querySelector('.nav button.active');" +
                "var view=active&&active.dataset?active.dataset.view:'';" +
                "var btn=document.getElementById('newProjectBtn');if(btn)btn.classList.toggle('hidden',view!=='projects');" +
            "}catch(e){}}" +
            "document.querySelectorAll('.nav button').forEach(function(b){b.addEventListener('click',function(){setTimeout(function(){syncProjectButton();if(b.dataset.view==='licenses'&&typeof renderLicenses==='function')renderLicenses();if(b.dataset.view==='customers'&&typeof renderCustomers==='function')renderCustomers();},0);},false);});" +
            "var observer=new MutationObserver(function(){syncProjectButton();});var nav=document.querySelector('.nav');if(nav)observer.observe(nav,{subtree:true,attributes:true,attributeFilter:['class']});" +
            "syncProjectButton();if(typeof renderLicenses==='function')renderLicenses();if(typeof renderCustomers==='function')renderCustomers();" +
            "}catch(e){console.log('Well Android v5 patch',e);}})();";
        v.evaluateJavascript(js, null);
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
}
