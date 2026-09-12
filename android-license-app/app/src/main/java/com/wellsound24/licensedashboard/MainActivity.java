package com.wellsound24.licensedashboard;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
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
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectLatestUi(view);
            }
        });
        webView.loadUrl("https://welllicensedashboardnewvercel.vercel.app/?android=1");
    }

    private void injectLatestUi(WebView v) {
        String js = "(function(){try{" +
            "if(window.__wellAndroidPatch)return;window.__wellAndroidPatch=true;" +
            "window.copyLicense=async function(id){var l=(window.licenses||[]).find(function(x){return x.id===id});if(!l)return;var val=l.license_key||l.activation_email||'';if(!val){if(window.toast)toast('ไม่มี License ให้คัดลอก');return;}try{await navigator.clipboard.writeText(String(val));if(window.toast)toast('คัดลอก License แล้ว');}catch(e){var t=document.createElement('textarea');t.value=String(val);document.body.appendChild(t);t.select();document.execCommand('copy');t.remove();if(window.toast)toast('คัดลอก License แล้ว');}};" +
            "if(window.licenseRows){window.licenseRows=function(ls){return (ls||[]).map(function(l){var d=window.daysLeft?daysLeft(l.expires_at):null;var status=l.enabled===false?'<span class=\"badge bad\">ปิด</span>':(d!==null&&d<0?'<span class=\"badge bad\">หมดอายุ</span>':(d!==null&&d<=7?'<span class=\"badge warn\">ใกล้หมดอายุ</span>':'<span class=\"badge good\">ใช้งาน</span>'));var id=String(l.id).replace(/'/g,\"\\\\'\");return '<tr><td><b>'+esc(l.customer_name||'-')+'</b><br><span class=\"muted\">License: '+esc(l.license_key||l.activation_email||'-')+'</span></td><td>'+esc(l.mt5_login||l.activation_email||'-')+'</td><td>'+esc(l.broker||l.product_code||'-')+'<br><span class=\"muted\">'+esc(l.server||'')+'</span></td><td>'+esc(l.expires_at||'-')+'</td><td>'+status+'</td><td><div class=\"row-actions\"><button class=\"btn small\" onclick=\"copyLicense(\\\''+id+'\\\')\">คัดลอก License</button><button class=\"btn small\" onclick=\"openLicenseModal(\\\''+id+'\\\')\">แก้ไข</button><button class=\"btn small '+(l.enabled===false?'primary':'')+'\" onclick=\"toggleLicense(\\\''+id+'\\\','+(l.enabled===false)+')\">'+(l.enabled===false?'เปิด':'ปิด')+'</button></div></td></tr>';}).join('')||'<tr><td colspan=\"6\" class=\"muted\">ไม่มี License ในโปรเจกต์นี้</td></tr>';};}" +
            "if(window.filteredCustomers){window.filteredCustomers=function(){var ls=window.filteredLicenses?filteredLicenses():[];var ids=new Set(ls.map(function(x){return x.customer_id}).filter(Boolean));var names=new Set(ls.map(function(x){return x.customer_name}).filter(Boolean));return (window.customers||[]).filter(function(c){return ids.has(c.id)||names.has(c.name)});};}" +
            "var oldSwitch=window.switchView;if(oldSwitch){window.switchView=function(name){oldSwitch(name);var b=document.getElementById('newProjectBtn');if(b)b.classList.toggle('hidden',name!=='projects');};}" +
            "if(window.selectProject){window.selectProject=function(id,keep){var active=document.querySelector('.nav button.active');var view=active?active.dataset.view:'dashboard';window.currentProjectId=id;localStorage.setItem('well_project',id);if(window.renderProjectSelect)renderProjectSelect();if(window.renderAll)renderAll();if(window.switchView)switchView(keep?view:'dashboard');};}" +
            "document.querySelectorAll('.nav button').forEach(function(b){b.onclick=function(){if(window.switchView)switchView(b.dataset.view);};});" +
            "var ps=document.getElementById('projectSelect');if(ps)ps.onchange=function(e){if(window.selectProject)selectProject(e.target.value,true);};" +
            "if(window.renderAll)renderAll();var a=document.querySelector('.nav button.active');if(window.switchView)switchView(a?a.dataset.view:'dashboard');" +
            "}catch(e){console.log('Well Android patch',e);}})();";
        v.evaluateJavascript(js, null);
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
}
