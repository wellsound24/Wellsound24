from pathlib import Path

HTML=Path('well-assistant-pro-android/app/src/main/assets/index.html')
JAVA=Path('well-assistant-pro-android/app/src/main/java/com/wellsound/assistantpro/MainActivity.java')

s=HTML.read_text()
marker='LIVE_M32_VU_AUTOCONNECT_V2'
if marker not in s:
    # Stable channel id on each Overview card so live meter updates survive re-rendering.
    old="d.type='button';d.className='chStrip '+(c.ch===selectedOverviewCh?'sel':'');"
    new="d.type='button';d.dataset.ch=String(c.ch);d.className='chStrip '+(c.ch===selectedOverviewCh?'sel':'');"
    if old in s:
        s=s.replace(old,new,1)

    addon=r'''<!-- LIVE_M32_VU_AUTOCONNECT_V2 -->
<style id="liveM32VuStyle">
.chStrip{position:relative;overflow:hidden}
.m32VuTrack{position:absolute;right:5px;top:8px;bottom:8px;width:6px;border-radius:6px;background:#101922;border:1px solid #20303d;overflow:hidden;pointer-events:none}
.m32VuFill{position:absolute;left:0;right:0;bottom:0;height:0%;border-radius:5px;background:#36b6e9;transition:height .06s linear,background .06s linear}
.m32VuFill.hot{background:#e3ad39}.m32VuFill.clip{background:#e65050}
#m32AutoStatus{font-size:9px;color:#7f98aa;margin-top:5px}
</style>
<script id="liveM32VuScript">
(function(){
  function ensureOverviewMeters(){
    document.querySelectorAll('.chStrip').forEach((card,i)=>{
      if(!card.dataset.ch)card.dataset.ch=String(i+1);
      if(!card.querySelector('.m32VuTrack')){
        const tr=document.createElement('span');tr.className='m32VuTrack';
        const fl=document.createElement('span');fl.className='m32VuFill';tr.appendChild(fl);card.appendChild(tr);
      }
    });
  }
  window.onM32Meters=function(values){
    ensureOverviewMeters();
    (values||[]).slice(0,32).forEach((db,i)=>{
      const card=document.querySelector('.chStrip[data-ch="'+(i+1)+'"]')||document.querySelectorAll('.chStrip')[i];
      if(!card)return;const f=card.querySelector('.m32VuFill');if(!f)return;
      db=Number(db);if(!Number.isFinite(db))db=-60;
      const pct=Math.max(0,Math.min(100,(db+60)/60*100));f.style.height=pct+'%';
      f.className='m32VuFill'+(db>=-3?' clip':db>=-10?' hot':'');
    });
  };
  const prevConnect=window.onM32Connect;
  window.onM32Connect=function(ok,msg){
    if(prevConnect)prevConnect(ok,msg);
    if(ok){try{WellNative.startM32Meters(localStorage.ip||'')}catch(e){}}
  };
  function autoConnectOnLaunch(){
    ensureOverviewMeters();
    try{WellNative.autoDetectM32()}catch(e){}
  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',()=>setTimeout(autoConnectOnLaunch,700));
  else setTimeout(autoConnectOnLaunch,700);
  window.addEventListener('load',()=>setTimeout(ensureOverviewMeters,300));
  setInterval(ensureOverviewMeters,700);
})();
</script>'''
    s=s.replace('</body>',addon+'</body>')
    HTML.write_text(s)

j=JAVA.read_text()
# Fields for real M32 meter subscription.
if 'private volatile boolean m32MeterRunning' not in j:
    j=j.replace('  private Thread audioThread;','  private Thread audioThread;\n  private volatile boolean m32MeterRunning = false;\n  private volatile String m32MeterHost = null;\n  private Thread m32MeterThread;')

# Start/stop meter methods are injected into the JS bridge only once.
anchor='    @JavascriptInterface public void query(String ip, int port, String address) {'
if '@JavascriptInterface public void startM32Meters(String ip)' not in j:
    methods=r'''    @JavascriptInterface public void startM32Meters(String ip) {
      String host = (ip == null ? "" : ip.trim());
      if (host.length() == 0) return;
      startM32MetersInternal(host);
    }

    @JavascriptInterface public void stopM32Meters() {
      stopM32MetersInternal();
    }

'''
    if anchor not in j:
        raise SystemExit('query anchor not found for meter bridge')
    j=j.replace(anchor,methods+anchor,1)

# Start meters immediately after a verified /info reply.
needle='''          boolean ok = reply.getAddress().equals(host);\n          m32ConnectCallback(ok,'''
if needle in j:
    j=j.replace(needle,'''          boolean ok = reply.getAddress().equals(host);\n          if (ok) startM32MetersInternal(ip);\n          m32ConnectCallback(ok,''',1)

# Add native meter engine before audio methods.
engine_anchor='  @SuppressLint("MissingPermission")\n  private synchronized void startNativeMicInternal() {'
if 'private synchronized void startM32MetersInternal(String host)' not in j:
    engine=r'''  private synchronized void startM32MetersInternal(String host) {
    if (host == null || host.trim().isEmpty()) return;
    host = host.trim();
    if (m32MeterRunning && host.equals(m32MeterHost)) return;
    stopM32MetersInternal();
    m32MeterHost = host;
    m32MeterRunning = true;
    final String targetHost = host;
    m32MeterThread = new Thread(() -> runM32MeterLoop(targetHost), "WellM32Meters");
    m32MeterThread.start();
  }

  private synchronized void stopM32MetersInternal() {
    m32MeterRunning = false;
    Thread t = m32MeterThread;
    m32MeterThread = null;
    if (t != null && t != Thread.currentThread()) {
      try { t.join(180); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
  }

  private void runM32MeterLoop(String host) {
    DatagramSocket ds = null;
    try {
      InetAddress mixer = InetAddress.getByName(host);
      ds = new DatagramSocket();
      ds.setSoTimeout(900);
      long lastSubscribe = 0;
      long lastPacket = 0;
      while (m32MeterRunning && host.equals(m32MeterHost)) {
        long now = System.currentTimeMillis();
        // /meters subscriptions expire after about 10 s. Renew at 7 s, and re-request after silence.
        if (lastSubscribe == 0 || now - lastSubscribe > 7000 || (lastPacket > 0 && now - lastPacket > 1800)) {
          byte[] req = encodeOsc("/meters", "string", "meters/1");
          ds.send(new DatagramPacket(req, req.length, mixer, 10023));
          lastSubscribe = now;
        }
        try {
          byte[] buf = new byte[8192];
          DatagramPacket p = new DatagramPacket(buf, buf.length);
          ds.receive(p);
          if (!p.getAddress().equals(mixer)) continue;
          double[] db = parseM32Meter1(buf, p.getLength());
          if (db != null && db.length >= 32) {
            lastPacket = System.currentTimeMillis();
            pushM32Meters(db);
          }
        } catch (java.net.SocketTimeoutException timeout) {
          // Loop re-subscribes automatically.
        }
      }
    } catch (Exception e) {
      final String msg = safeMsg(e.getMessage()).replace("\\", "\\\\").replace("'", "\\'");
      runOnUiThread(() -> webView.evaluateJavascript("window.onM32MeterError&&window.onM32MeterError('" + msg + "')", null));
    } finally {
      if (ds != null) try { ds.close(); } catch (Exception ignored) {}
    }
  }

  private static int align4(int n) { return (n + 3) & ~3; }

  private static double[] parseM32Meter1(byte[] b, int len) {
    try {
      int p = 0;
      while (p < len && b[p] != 0) p++;
      if (p >= len) return null;
      String address = new String(b, 0, p, StandardCharsets.UTF_8);
      if (!(address.equals("meters/1") || address.equals("/meters/1"))) return null;
      p = align4(p + 1);
      int ts = p;
      while (p < len && b[p] != 0) p++;
      if (p >= len) return null;
      String tags = new String(b, ts, p - ts, StandardCharsets.UTF_8);
      p = align4(p + 1);
      if (!tags.contains("b") || p + 4 > len) return null;
      int blobLen = ((b[p] & 255) << 24) | ((b[p+1] & 255) << 16) | ((b[p+2] & 255) << 8) | (b[p+3] & 255);
      p += 4;
      if (blobLen < 8 || p + blobLen > len) return null;
      // First 32-bit value inside the blob is little-endian float count.
      int count = (b[p] & 255) | ((b[p+1] & 255) << 8) | ((b[p+2] & 255) << 16) | ((b[p+3] & 255) << 24);
      p += 4;
      count = Math.min(count, (blobLen - 4) / 4);
      if (count < 32) return null;
      double[] out = new double[32];
      for (int i = 0; i < 32; i++) {
        int bits = (b[p] & 255) | ((b[p+1] & 255) << 8) | ((b[p+2] & 255) << 16) | ((b[p+3] & 255) << 24);
        p += 4;
        float linear = Float.intBitsToFloat(bits);
        double db = (linear > 0.000001f && Float.isFinite(linear)) ? 20.0 * Math.log10(linear) : -60.0;
        if (db < -60) db = -60;
        if (db > 0) db = 0;
        out[i] = db;
      }
      return out;
    } catch (Exception e) { return null; }
  }

  private void pushM32Meters(double[] db) {
    StringBuilder a = new StringBuilder("[");
    for (int i = 0; i < 32; i++) {
      if (i > 0) a.append(',');
      a.append(String.format(Locale.US, "%.1f", db[i]));
    }
    a.append(']');
    final String js = a.toString();
    runOnUiThread(() -> webView.evaluateJavascript("window.onM32Meters&&window.onM32Meters(" + js + ")", null));
  }

'''
    if engine_anchor not in j:
        raise SystemExit('native audio anchor not found for meter engine')
    j=j.replace(engine_anchor,engine+engine_anchor,1)

# Stop meter worker when app is destroyed.
if 'stopM32MetersInternal();\n    stopNativeMicInternal();' not in j:
    j=j.replace('  @Override protected void onDestroy() {\n    stopNativeMicInternal();','  @Override protected void onDestroy() {\n    stopM32MetersInternal();\n    stopNativeMicInternal();',1)

JAVA.write_text(j)
