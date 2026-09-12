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
                injectPatch(view);
            }
        });
        webView.loadUrl("https://welllicensedashboardnewvercel.vercel.app/?android=7");
    }

    private void injectPatch(WebView v) {
        String js = """
        (function(){try{
          if(window.__wellAndroidV7)return; window.__wellAndroidV7=true;
          window.wellStatusFilter='all';

          window.copyLicense=function(id){
            var l=licenses.find(function(x){return String(x.id)===String(id)});
            if(!l||!l.license_key){toast('ไม่มี License ให้คัดลอก');return;}
            AndroidBridge.copyText(String(l.license_key));
          };

          function lastSeen(l){return l.last_seen_at||l.last_seen||l.heartbeat_at||l.last_heartbeat_at||null;}
          function isOnline(l){var t=lastSeen(l);if(!t)return false;var d=new Date(t);return !isNaN(d)&&((Date.now()-d.getTime())<=300000);}
          function seenText(l){var t=lastSeen(l);if(!t)return 'ยังไม่เคยเชื่อมต่อ';try{return new Date(t).toLocaleString('th-TH');}catch(e){return String(t)}}
          function stateOf(l){var d=daysLeft(l.expires_at);if(l.ib_status==='pending')return 'ib';if(l.enabled===false)return 'disabled';if(d!==null&&d<0)return 'expired';if(d!==null&&d<=7)return 'warning';return 'active';}
          function stateLabel(l){var s=stateOf(l);if(s==='ib')return '<span class="badge warn">IB Pending</span>';if(s==='disabled')return '<span class="badge bad">Disabled</span>';if(s==='expired')return '<span class="badge bad">Expired</span>';if(s==='warning')return '<span class="badge warn">ใกล้หมดอายุ</span>';return '<span class="badge good">Active</span>';}
          function expText(l){if(!l.expires_at)return '<b>ไม่มีวันหมดอายุ</b><br><span class="muted">ใช้งานตลอด</span>';var d=daysLeft(l.expires_at);return esc(String(l.expires_at).slice(0,10))+'<br><span class="muted">เหลือ '+Math.max(0,d||0)+' วัน</span>';}
          function shortKey(k){k=String(k||'');return k.length>27?k.slice(0,27)+'...':k;}

          window.licenseRows=function(ls){return (ls||[]).map(function(l){
            var eid=encodeURIComponent(String(l.id));var online=isOnline(l);var key=l.license_key||'';
            return '<tr><td><b>'+esc(l.customer_name||'-')+'</b><br><span class="muted">'+esc(l.mt5_login||l.activation_email||'-')+'</span></td>'+
              '<td>'+expText(l)+'</td>'+
              '<td><div style="display:flex;gap:8px;align-items:center;min-width:270px"><code style="flex:1;white-space:nowrap">'+esc(shortKey(key)||'-')+'</code><button class="btn small" title="คัดลอก License Key" onclick="copyLicense(decodeURIComponent(\\\''+eid+'\\\'))">⧉</button></div></td>'+
              '<td><div style="font-weight:800;color:'+(online?'#4ade80':'#f87171')+'">● '+(online?'Online':'Offline')+'</div><div style="margin-top:4px">License: '+stateLabel(l)+'</div><div class="muted" style="font-size:12px;margin-top:4px">ล่าสุด: '+esc(seenText(l))+'</div></td>'+
              '<td><button class="btn small" onclick="openLicenseModal(decodeURIComponent(\\\''+eid+'\\\'))">แก้ไข</button></td></tr>';
          }).join('')||'<tr><td colspan="5" class="muted">ไม่มี License ในโปรเจกต์นี้</td></tr>';};

          window.renderLicenses=function(){
            var all=filteredLicenses(), f=window.wellStatusFilter||'all';
            var ls=all.filter(function(l){return f==='all'||stateOf(l)===f;});
            var opts=[['all','ทุกสถานะ'],['active','Active'],['ib','IB Pending'],['disabled','Disabled'],['expired','Expired'],['warning','ใกล้หมดอายุ']];
            document.querySelector('#view-licenses').innerHTML='<div class="panel"><div class="toolbar"><input id="licenseSearch" class="input search" placeholder="ค้นหา License / ลูกค้า / MT5 / Email"><select id="licenseStatusFilter" class="select" style="max-width:190px">'+opts.map(function(o){return '<option value="'+o[0]+'" '+(f===o[0]?'selected':'')+'>'+o[1]+'</option>'}).join('')+'</select><button class="btn primary" onclick="openLicenseModal()">+ เพิ่ม License</button></div><div class="table-wrap"><table><thead><tr><th>ลูกค้า</th><th>หมดอายุ</th><th>License Key</th><th>สถานะ</th><th>จัดการ</th></tr></thead><tbody id="licenseRows">'+licenseRows(ls)+'</tbody></table></div></div>';
            document.querySelector('#licenseStatusFilter').onchange=function(e){window.wellStatusFilter=e.target.value;renderLicenses();};
            document.querySelector('#licenseSearch').oninput=function(e){var q=e.target.value.toLowerCase();document.querySelector('#licenseRows').innerHTML=licenseRows(ls.filter(function(x){return JSON.stringify(x).toLowerCase().includes(q)}));};
          };

          function expiryFields(prefix,l){var date=(l.expires_at||'').slice(0,10);var life=!l.expires_at&&!!l.id;return '<div class="field"><label class="label">วันหมดอายุ</label><input id="'+prefix+'Exp" type="date" class="input" value="'+esc(date)+'"></div><div class="field"><label class="label">ใช้ได้กี่วัน</label><input id="'+prefix+'Days" type="number" min="0" class="input" value="0" placeholder="เช่น 30"><div class="muted" style="font-size:12px;margin-top:5px">พิมพ์จำนวนวัน แล้วระบบจะคำนวณวันหมดอายุให้อัตโนมัติ</div></div><div class="field span2"><label style="display:flex;gap:9px;align-items:center"><input id="'+prefix+'Life" type="checkbox" '+(life?'checked':'')+'> ใช้งานตลอด / ไม่มีวันหมดอายุ</label></div>';}
          function bindExpiry(prefix){var days=document.querySelector('#'+prefix+'Days'),exp=document.querySelector('#'+prefix+'Exp'),life=document.querySelector('#'+prefix+'Life');if(days)days.oninput=function(){var n=parseInt(days.value||'0',10);if(n>0){var d=new Date();d.setDate(d.getDate()+n);exp.value=d.toISOString().slice(0,10);life.checked=false;}};if(exp)exp.onchange=function(){if(exp.value)life.checked=false;};if(life)life.onchange=function(){if(life.checked){exp.value='';days.value='0';}};}
          function expiryValue(prefix){var life=document.querySelector('#'+prefix+'Life');return life&&life.checked?null:(document.querySelector('#'+prefix+'Exp').value||null);}

          window.deleteLicenseFromEdit=async function(id){if(!id||!confirm('ยืนยันลบ License นี้?'))return;try{await call(API.core,{action:'archive',id:id});closeModal();toast('ลบ License แล้ว');await refresh();}catch(e){toast('ลบ License ไม่สำเร็จ');}};

          window.openAutoEqLicenseModal=function(id){id=id||'';var l=licenses.find(function(x){return String(x.id)===String(id)})||{};var selected=l.customer_id||'';var options=customers.map(function(c){return '<option value="'+esc(c.id)+'" '+(String(c.id)===String(selected)?'selected':'')+'>'+esc(c.name)+(c.email?' — '+esc(c.email):'')+'</option>';}).join('');openModal('<div class="panel-head"><div><div class="panel-title">'+(id?'แก้ไข':'เพิ่ม')+' License</div><div class="panel-sub">Well Auto EQ Pro</div></div><button class="btn small" onclick="closeModal()">ปิด</button></div><form id="autoEqLicenseForm"><div class="modal-grid"><div class="field span2"><label class="label">ลูกค้า</label><select id="aefCustomer" class="select" required><option value="">เลือกลูกค้า</option>'+options+'</select></div><div class="field span2"><label class="label">Email</label><input id="aefEmail" class="input" type="email" value="'+esc(l.activation_email||'')+'" required></div>'+expiryFields('aef',l)+'<div class="field span2"><label class="label">หมายเหตุ</label><textarea id="aefNotes" class="textarea">'+esc(l.notes||'')+'</textarea></div></div><div class="modal-actions">'+(id?'<button type="button" class="btn danger" id="deleteLicenseBtn">ลบ License</button>':'')+'<button type="button" class="btn" onclick="closeModal()">ยกเลิก</button><button class="btn primary">บันทึก</button></div></form>');bindExpiry('aef');if(id)document.querySelector('#deleteLicenseBtn').onclick=function(){deleteLicenseFromEdit(id)};document.querySelector('#aefCustomer').onchange=function(){var c=customers.find(function(x){return String(x.id)===String(document.querySelector('#aefCustomer').value)});if(c&&!document.querySelector('#aefEmail').value.trim()&&c.email)document.querySelector('#aefEmail').value=c.email;};document.querySelector('#autoEqLicenseForm').onsubmit=async function(e){e.preventDefault();var c=customers.find(function(x){return String(x.id)===String(document.querySelector('#aefCustomer').value)});if(!c){toast('กรุณาเลือกลูกค้า');return}try{await call(API.autoEq,{action:id?'update':'create',id:id,customer_id:c.id,customer_name:c.name,activation_email:document.querySelector('#aefEmail').value.trim(),expires_at:expiryValue('aef'),notes:document.querySelector('#aefNotes').value.trim()});closeModal();toast('บันทึก License แล้ว');await refresh();}catch(err){toast('บันทึกไม่สำเร็จ: '+err.message);}};};

          var baseOpen=window.openLicenseModal;
          window.openLicenseModal=function(id){id=id||'';var p=currentProject();if(p&&p.product_code==='WELL_AUTO_EQ_PRO')return openAutoEqLicenseModal(id);var l=licenses.find(function(x){return String(x.id)===String(id)})||{};if(p&&p.product_code!=='SEMI_EA'&&!id)return baseOpen(id);openModal('<div class="panel-head"><div class="panel-title">'+(id?'แก้ไข':'เพิ่ม')+' License</div><button class="btn small" onclick="closeModal()">ปิด</button></div><form id="licenseForm"><div class="modal-grid"><div class="field"><label class="label">ชื่อลูกค้า</label><input id="lfName" class="input" value="'+esc(l.customer_name||'')+'" required></div><div class="field"><label class="label">MT5 Login</label><input id="lfLogin" class="input" value="'+esc(l.mt5_login||'')+'" required></div><div class="field"><label class="label">Broker</label><input id="lfBroker" class="input" value="'+esc(l.broker||'')+'" required></div><div class="field"><label class="label">Server</label><input id="lfServer" class="input" value="'+esc(l.server||'')+'" required></div><div class="field"><label class="label">IB Status</label><select id="lfIb" class="select"><option value="approved">Approved</option><option value="pending">Pending</option><option value="rejected">Rejected</option></select></div>'+expiryFields('lf',l)+'<div class="field span2"><label class="label">หมายเหตุ</label><textarea id="lfNotes" class="textarea">'+esc(l.notes||'')+'</textarea></div></div><div class="modal-actions">'+(id?'<button type="button" class="btn danger" id="deleteLicenseBtn">ลบ License</button>':'')+'<button type="button" class="btn" onclick="closeModal()">ยกเลิก</button><button class="btn primary">บันทึก</button></div></form>');document.querySelector('#lfIb').value=l.ib_status||'pending';bindExpiry('lf');if(id)document.querySelector('#deleteLicenseBtn').onclick=function(){deleteLicenseFromEdit(id)};document.querySelector('#licenseForm').onsubmit=async function(e){e.preventDefault();try{await call(API.core,{action:id?'update_license':'create',id:id,customer_name:document.querySelector('#lfName').value.trim(),mt5_login:document.querySelector('#lfLogin').value.trim(),broker:document.querySelector('#lfBroker').value.trim(),server:document.querySelector('#lfServer').value.trim(),ib_status:document.querySelector('#lfIb').value,expires_at:expiryValue('lf'),notes:document.querySelector('#lfNotes').value.trim()});closeModal();toast('บันทึก License แล้ว');await refresh();}catch(err){toast('บันทึกไม่สำเร็จ: '+err.message);}};};

          window.customerRows=function(rows){return (rows||[]).map(function(c){var eid=encodeURIComponent(String(c.id));return '<tr><td><b>'+esc(c.name||'-')+'</b></td><td>'+esc(c.phone||'-')+'</td><td>'+esc(c.email||'-')+'</td><td>'+esc(c.line_id||'-')+'</td><td>'+(c.license_count||0)+'</td><td><button class="btn small" onclick="openCustomerModal(decodeURIComponent(\\\''+eid+'\\\'))">แก้ไข</button></td></tr>';}).join('')||'<tr><td colspan="6" class="muted">ไม่มีลูกค้าในโปรเจกต์นี้</td></tr>';};

          function syncProjectButton(){var active=document.querySelector('.nav button.active');var view=active&&active.dataset?active.dataset.view:'';var btn=document.getElementById('newProjectBtn');if(btn)btn.classList.toggle('hidden',view!=='projects');}
          document.querySelectorAll('.nav button').forEach(function(b){b.addEventListener('click',function(){setTimeout(function(){syncProjectButton();if(b.dataset.view==='licenses')renderLicenses();},0);});});
          syncProjectButton();renderLicenses();if(typeof renderCustomers==='function')renderCustomers();
        }catch(e){console.log('Well Android v7 patch',e);}})();
        """;
        v.evaluateJavascript(js, null);
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
}
