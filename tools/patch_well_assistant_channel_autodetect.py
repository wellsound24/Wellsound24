from pathlib import Path

html_path = Path('well-assistant-pro-android/app/src/main/assets/index.html')
java_path = Path('well-assistant-pro-android/app/src/main/java/com/wellsound/assistantpro/MainActivity.java')

s = html_path.read_text()
marker = 'CHANNEL_SETUP_NAV_AUTODETECT_V2'
if marker not in s:
    addon = r'''<!-- CHANNEL_SETUP_NAV_AUTODETECT_V2 -->
<style id="channelAutoDetectStyleV2">
#m32AutoDetectBtn{width:100%;margin-top:8px;border:1px solid #2d7ca2;background:#102536;color:#8fdcff;border-radius:12px;padding:11px 12px;font-weight:900;font-size:12px}
#m32AutoDetectBtn.busy{opacity:.65}
</style>
<script id="channelAutoDetectScriptV2">
(function(){
  function openCardSetup(e){
    const card=e.target&&e.target.closest?e.target.closest('.chStrip'):null;
    if(!card)return;
    if(e.target.closest('select,input,.chProcBtn'))return;
    e.preventDefault();e.stopPropagation();if(e.stopImmediatePropagation)e.stopImmediatePropagation();
    let n=parseInt(card.dataset.ch||card.getAttribute('data-ch')||'0');
    if(!n){const cards=[...document.querySelectorAll('.chStrip')];n=cards.indexOf(card)+1;}
    if(typeof window.openChannelSetup==='function') window.openChannelSetup(n);
    else if(typeof openChannelSetup==='function') openChannelSetup(n);
  }
  document.addEventListener('click',openCardSetup,true);

  window.autoDetectM32=function(){
    const b=document.getElementById('m32AutoDetectBtn');
    if(b){b.classList.add('busy');b.textContent='SEARCHING M32R...';b.disabled=true;}
    try{WellNative.autoDetectM32()}catch(e){window.onM32AutoDetect(false,'','Auto detect unavailable')}
  };
  window.onM32AutoDetect=function(ok,ip,msg){
    const b=document.getElementById('m32AutoDetectBtn');
    if(b){b.classList.remove('busy');b.textContent='AUTO DETECT M32R';b.disabled=false;}
    if(ok&&ip){
      localStorage.ip=ip;
      const q=document.getElementById('m32QuickIp');if(q)q.value=ip;
      const ipEl=document.getElementById('ip');if(ipEl)ipEl.value=ip;
      try{toast('M32R FOUND: '+ip)}catch(e){}
      if(typeof window.connectM32==='function') window.connectM32();
    }else{try{toast(msg||'M32R NOT FOUND')}catch(e){}}
  };
  function installAutoDetect(){
    const panel=document.getElementById('m32ConnectPanel');
    if(panel&&!document.getElementById('m32AutoDetectBtn')){
      const b=document.createElement('button');
      b.id='m32AutoDetectBtn';b.textContent='AUTO DETECT M32R';b.onclick=window.autoDetectM32;
      const form=panel.querySelector('.m32ConnectForm');
      if(form)form.after(b);else panel.appendChild(b);
    }
  }
  window.addEventListener('load',()=>setTimeout(installAutoDetect,250));
  setInterval(installAutoDetect,1000);
})();
</script>'''
    s = s.replace('</body>', addon + '</body>')
    html_path.write_text(s)

j = java_path.read_text()
for imp in [
    'import java.net.Inet4Address;',
    'import java.net.NetworkInterface;',
    'import java.util.ArrayList;',
    'import java.util.Enumeration;',
    'import java.util.LinkedHashSet;'
]:
    if imp not in j:
        j = j.replace('import java.net.InetAddress;\n', 'import java.net.InetAddress;\n' + imp + '\n', 1)

if '@JavascriptInterface public void autoDetectM32()' not in j:
    anchor = '    @JavascriptInterface public void connectM32(String ip, int port) {'
    method = '''    @JavascriptInterface public void autoDetectM32() {\n      io.execute(() -> {\n        String found = null;\n        try {\n          for (String prefix : localIpv4Prefixes()) {\n            DatagramSocket ds = null;\n            try {\n              ds = new DatagramSocket();\n              ds.setSoTimeout(140);\n              byte[] msg = encodeOsc("/info", "none", "");\n              for (int i = 1; i <= 254; i++) {\n                try {\n                  InetAddress a = InetAddress.getByName(prefix + i);\n                  ds.send(new DatagramPacket(msg, msg.length, a, 10023));\n                } catch (Exception ignored) {}\n              }\n              long end = System.currentTimeMillis() + 1800;\n              byte[] buf = new byte[4096];\n              while (System.currentTimeMillis() < end && found == null) {\n                try {\n                  DatagramPacket p = new DatagramPacket(buf, buf.length);\n                  ds.receive(p);\n                  found = p.getAddress().getHostAddress();\n                } catch (java.net.SocketTimeoutException timeout) { break; }\n                catch (Exception ignored) {}\n              }\n            } finally {\n              if (ds != null) try { ds.close(); } catch (Exception ignored) {}\n            }\n            if (found != null) break;\n          }\n        } catch (Exception ignored) {}\n        final String f = found;\n        runOnUiThread(() -> webView.evaluateJavascript(\n            "window.onM32AutoDetect&&window.onM32AutoDetect(" + (f != null ? "true" : "false") + "," +\n            (f != null ? "'" + f + "'" : "''") + "," + (f != null ? "'M32R found'" : "'M32R not found'") + ")", null\n        ));\n      });\n    }\n\n    private ArrayList<String> localIpv4Prefixes() {\n      LinkedHashSet<String> out = new LinkedHashSet<>();\n      try {\n        Enumeration<NetworkInterface> es = NetworkInterface.getNetworkInterfaces();\n        while (es.hasMoreElements()) {\n          NetworkInterface ni = es.nextElement();\n          if (!ni.isUp() || ni.isLoopback()) continue;\n          Enumeration<InetAddress> as = ni.getInetAddresses();\n          while (as.hasMoreElements()) {\n            InetAddress a = as.nextElement();\n            if (a instanceof Inet4Address && a.isSiteLocalAddress()) {\n              String x = a.getHostAddress();\n              int k = x.lastIndexOf('.');\n              if (k > 0) out.add(x.substring(0, k + 1));\n            }\n          }\n        }\n      } catch (Exception ignored) {}\n      return new ArrayList<>(out);\n    }\n\n    @JavascriptInterface public void connectM32(String ip, int port) {'''
    if anchor not in j:
        raise SystemExit('connectM32 anchor not found')
    j = j.replace(anchor, method, 1)
    java_path.write_text(j)
