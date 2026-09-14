package com.wellsound.assistantpro;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.LinkedHashSet;
import java.util.Enumeration;
import java.util.ArrayList;
import java.net.NetworkInterface;
import java.net.Inet4Address;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
  private static final int REQ_MIC = 501;
  private static final int REQ_FILE = 502;
  private static final int SAMPLE_RATE = 48000;
  private static final int FFT_SIZE = 2048;

  private WebView webView;
  private ValueCallback<Uri[]> fileCallback;
  private PermissionRequest pendingWebPermissionRequest;
  private final ExecutorService io = Executors.newSingleThreadExecutor();

  private volatile boolean nativeMicRunning = false;
  private volatile boolean pendingNativeMicStart = false;
  private AudioRecord audioRecord;
  private Thread audioThread;
  private volatile boolean m32MeterRunning = false;
  private volatile String m32MeterHost = null;
  private Thread m32MeterThread;

  @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
  @Override public void onCreate(Bundle b) {
    super.onCreate(b);

    webView = new WebView(this);
    webView.getSettings().setJavaScriptEnabled(true);
    webView.getSettings().setDomStorageEnabled(true);
    webView.getSettings().setAllowFileAccess(true);
    webView.getSettings().setAllowContentAccess(true);
    webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
    webView.setWebViewClient(new WebViewClient());

    webView.setWebChromeClient(new WebChromeClient() {
      @Override public void onPermissionRequest(PermissionRequest request) {
        runOnUiThread(() -> {
          boolean wantsAudio = false;
          for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
              wantsAudio = true;
              break;
            }
          }
          if (!wantsAudio) {
            request.deny();
            return;
          }
          if (android.os.Build.VERSION.SDK_INT < 23 ||
              checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
          } else {
            pendingWebPermissionRequest = request;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
          }
        });
      }

      @Override public void onPermissionRequestCanceled(PermissionRequest request) {
        if (pendingWebPermissionRequest == request) pendingWebPermissionRequest = null;
      }

      @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
        if (fileCallback != null) fileCallback.onReceiveValue(null);
        fileCallback = callback;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");
        startActivityForResult(i, REQ_FILE);
        return true;
      }
    });

    webView.addJavascriptInterface(new NativeBridge(), "WellNative");
    setContentView(webView);
    loadUiFromSecureOrigin();
  }

  private void loadUiFromSecureOrigin() {
    try {
      BufferedReader reader = new BufferedReader(
          new InputStreamReader(getAssets().open("index.html"), StandardCharsets.UTF_8)
      );
      StringBuilder html = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) html.append(line).append('\n');
      reader.close();

      String nativePatch =
          "<script>(function(){" +
          "const oldStop=window.stopSource;" +
          "window.stopSource=function(){try{WellNative.stopNativeMic()}catch(e){};if(oldStop)oldStop();};" +
          "window.startMic=async function(){window.stopSource();try{" +
          "currentSource='mic';document.querySelectorAll('.inputsrc button').forEach(b=>b.classList.remove('active'));" +
          "$('micBtn').classList.add('active');$('genControls').classList.add('hidden');" +
          "log('INPUT NATIVE LIVE MIC');WellNative.startNativeMic();" +
          "}catch(e){log('NATIVE MIC ERROR '+e);toast('เปิดไมค์ไม่ได้');}};" +
          "window.onNativeMicStarted=function(){toast('LIVE MIC พร้อมใช้งาน');log('NATIVE MIC STARTED 48 kHz');};" +
          "window.onNativeMicError=function(msg){$('dbfs').textContent='-∞';$('peakHz').textContent='—';$('crest').textContent='—';log('NATIVE MIC ERROR '+msg);toast('ไมค์ผิดพลาด: '+msg);};" +
          "window.onNativeAudio=function(d){try{" +
          "$('dbfs').textContent=Number(d.dbfs).toFixed(1);$('peakHz').textContent=d.peakHz>0?Math.round(d.peakHz):'—';" +
          "$('crest').textContent=Number(d.crest).toFixed(1);let simgr=Math.max(0,(Number(d.dbfs)+18)*.55);$('gr').textContent=simgr.toFixed(1);" +
          "if(window.feedbackWatch)feedbackWatch(Number(d.peakHz),Number(d.strength),Number(d.dbfs));" +
          "let c=$('rta'),g=c.getContext('2d'),w=c.width,h=c.height,b=d.bins||[];g.clearRect(0,0,w,h);" +
          "g.strokeStyle='#162235';g.lineWidth=1;for(let i=0;i<8;i++){let y=i*h/8;g.beginPath();g.moveTo(0,y);g.lineTo(w,y);g.stroke();}" +
          "if(b.length){g.beginPath();for(let x=0;x<w;x++){let idx=Math.min(b.length-1,Math.floor(x/w*b.length));let v=Math.max(0,Math.min(1,Number(b[idx])));let y=h-v*h;if(x===0)g.moveTo(x,y);else g.lineTo(x,y);}g.strokeStyle='#52cfff';g.lineWidth=2;g.stroke();}" +
          "}catch(e){}};" +
          "})();</script>";

      String patched = html.toString().replace("</body>", nativePatch + "</body>");
      webView.loadDataWithBaseURL(
          "https://wellassistant.local/",
          patched,
          "text/html",
          "UTF-8",
          null
      );
    } catch (Exception e) {
      webView.loadData(
          "<html><body style='background:#080b14;color:white;font-family:sans-serif;padding:24px'>" +
          "<h2>Well Assistant Pro</h2><p>UI load error: " + escapeHtml(e.getMessage()) + "</p></body></html>",
          "text/html",
          "UTF-8"
      );
    }
  }

  private static String escapeHtml(String s) {
    if (s == null) return "Unknown";
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == REQ_MIC) {
      boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
      PermissionRequest request = pendingWebPermissionRequest;
      pendingWebPermissionRequest = null;
      if (request != null) {
        if (granted) request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
        else request.deny();
      }
      runOnUiThread(() -> webView.evaluateJavascript(
          granted ? "window.onNativeMicPermission&&window.onNativeMicPermission(true)" :
              "window.onNativeMicPermission&&window.onNativeMicPermission(false)", null
      ));
      if (granted && pendingNativeMicStart) {
        pendingNativeMicStart = false;
        startNativeMicInternal();
      } else if (!granted) {
        pendingNativeMicStart = false;
        nativeMicError("ไม่ได้รับอนุญาตใช้ไมโครโฟน");
      }
    }
  }

  @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == REQ_FILE && fileCallback != null) {
      Uri[] result = null;
      if (resultCode == RESULT_OK && data != null && data.getData() != null) result = new Uri[]{data.getData()};
      fileCallback.onReceiveValue(result);
      fileCallback = null;
    }
  }

  @Override protected void onDestroy() {
    stopM32MetersInternal();
    stopNativeMicInternal();
    io.shutdownNow();
    super.onDestroy();
  }

  @Override public void onBackPressed() {
    if (webView.canGoBack()) webView.goBack();
    else super.onBackPressed();
  }

  private void m32ConnectCallback(boolean ok, String message) {
    String msg = safeMsg(message).replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ").replace("\r", " ");
    runOnUiThread(() -> webView.evaluateJavascript(
        "window.onM32Connect&&window.onM32Connect(" + (ok ? "true" : "false") + ",'" + msg + "')", null
    ));
  }

  public class NativeBridge {
    @JavascriptInterface public void send(String ip, int port, String address, String type, String value) {
      io.execute(() -> {
        try {
          byte[] data = encodeOsc(address, type, value);
          DatagramSocket socket = new DatagramSocket();
          socket.send(new DatagramPacket(data, data.length, InetAddress.getByName(ip), port));
          socket.close();
          callback(true, "OSC TX " + address);
        } catch (Exception e) {
          callback(false, e.getMessage());
        }
      });
    }

    @JavascriptInterface public void autoDetectM32() {
      io.execute(() -> {
        String found = null;
        try {
          for (String prefix : localIpv4Prefixes()) {
            DatagramSocket ds = null;
            try {
              ds = new DatagramSocket();
              ds.setSoTimeout(140);
              byte[] msg = encodeOsc("/info", "none", "");
              for (int i = 1; i <= 254; i++) {
                try {
                  InetAddress a = InetAddress.getByName(prefix + i);
                  ds.send(new DatagramPacket(msg, msg.length, a, 10023));
                } catch (Exception ignored) {}
              }
              long end = System.currentTimeMillis() + 1800;
              byte[] buf = new byte[4096];
              while (System.currentTimeMillis() < end && found == null) {
                try {
                  DatagramPacket p = new DatagramPacket(buf, buf.length);
                  ds.receive(p);
                  found = p.getAddress().getHostAddress();
                } catch (java.net.SocketTimeoutException timeout) { break; }
                catch (Exception ignored) {}
              }
            } finally {
              if (ds != null) try { ds.close(); } catch (Exception ignored) {}
            }
            if (found != null) break;
          }
        } catch (Exception ignored) {}
        final String f = found;
        runOnUiThread(() -> webView.evaluateJavascript(
            "window.onM32AutoDetect&&window.onM32AutoDetect(" + (f != null ? "true" : "false") + "," +
            (f != null ? "'" + f + "'" : "''") + "," + (f != null ? "'M32R found'" : "'M32R not found'") + ")", null
        ));
      });
    }

    private ArrayList<String> localIpv4Prefixes() {
      LinkedHashSet<String> out = new LinkedHashSet<>();
      try {
        Enumeration<NetworkInterface> es = NetworkInterface.getNetworkInterfaces();
        while (es.hasMoreElements()) {
          NetworkInterface ni = es.nextElement();
          if (!ni.isUp() || ni.isLoopback()) continue;
          Enumeration<InetAddress> as = ni.getInetAddresses();
          while (as.hasMoreElements()) {
            InetAddress a = as.nextElement();
            if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
              String x = a.getHostAddress();
              int k = x.lastIndexOf('.');
              if (k > 0) out.add(x.substring(0, k + 1));
            }
          }
        }
      } catch (Exception ignored) {}
      return new ArrayList<>(out);
    }

    @JavascriptInterface public void connectM32(String ip, int port) {
      io.execute(() -> {
        DatagramSocket socket = null;
        try {
          InetAddress host = InetAddress.getByName(ip);
          byte[] data = encodeOsc("/info", "none", "");
          socket = new DatagramSocket();
          socket.setSoTimeout(1600);
          socket.send(new DatagramPacket(data, data.length, host, port));
          byte[] buf = new byte[4096];
          DatagramPacket reply = new DatagramPacket(buf, buf.length);
          socket.receive(reply);
          boolean ok = reply.getAddress().equals(host);
          if (ok) startM32MetersInternal(ip);
          m32ConnectCallback(ok, ok ? "M32 replied from " + reply.getAddress().getHostAddress() : "Unexpected reply");
        } catch (Exception e) {
          m32ConnectCallback(false, safeMsg(e.getMessage()));
        } finally {
          if (socket != null) try { socket.close(); } catch (Exception ignored) {}
        }
      });
    }

    @JavascriptInterface public void startM32Meters(String ip) {
      String host = (ip == null ? "" : ip.trim());
      if (host.length() == 0) return;
      startM32MetersInternal(host);
    }

    @JavascriptInterface public void stopM32Meters() {
      stopM32MetersInternal();
    }

    @JavascriptInterface public void query(String ip, int port, String address) {
      send(ip, port, address, "none", "");
    }

    @JavascriptInterface public boolean hasMicPermission() {
      return android.os.Build.VERSION.SDK_INT < 23 ||
          checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    @JavascriptInterface public void requestMicPermission() {
      runOnUiThread(() -> {
        if (android.os.Build.VERSION.SDK_INT < 23 ||
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
          webView.evaluateJavascript("window.onNativeMicPermission&&window.onNativeMicPermission(true)", null);
        } else {
          requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
        }
      });
    }

    @JavascriptInterface public void startNativeMic() {
      runOnUiThread(() -> {
        if (android.os.Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
          pendingNativeMicStart = true;
          requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
        } else {
          startNativeMicInternal();
        }
      });
    }

    @JavascriptInterface public void stopNativeMic() {
      stopNativeMicInternal();
    }

    @JavascriptInterface public void openAppSettings() {
      runOnUiThread(() -> startActivity(new Intent(
          Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
          Uri.parse("package:" + getPackageName())
      )));
    }
  }

  private synchronized void startM32MetersInternal(String host) {
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

  @SuppressLint("MissingPermission")
  private synchronized void startNativeMicInternal() {
    if (nativeMicRunning) return;
    try {
      int min = AudioRecord.getMinBufferSize(
          SAMPLE_RATE,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT
      );
      if (min <= 0) min = FFT_SIZE * 2;
      int bufferBytes = Math.max(min * 2, FFT_SIZE * 4);
      audioRecord = new AudioRecord(
          MediaRecorder.AudioSource.MIC,
          SAMPLE_RATE,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
          bufferBytes
      );
      if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
        try { audioRecord.release(); } catch (Exception ignored) {}
        audioRecord = null;
        nativeMicError("AudioRecord เริ่มต้นไม่สำเร็จ");
        return;
      }
      audioRecord.startRecording();
      nativeMicRunning = true;
      runOnUiThread(() -> webView.evaluateJavascript(
          "window.onNativeMicStarted&&window.onNativeMicStarted()", null
      ));
      audioThread = new Thread(this::audioLoop, "WellNativeAudio");
      audioThread.start();
    } catch (Exception e) {
      nativeMicRunning = false;
      nativeMicError(e.getClass().getSimpleName() + ": " + safeMsg(e.getMessage()));
    }
  }

  private synchronized void stopNativeMicInternal() {
    nativeMicRunning = false;
    AudioRecord r = audioRecord;
    audioRecord = null;
    if (r != null) {
      try { r.stop(); } catch (Exception ignored) {}
      try { r.release(); } catch (Exception ignored) {}
    }
    Thread t = audioThread;
    audioThread = null;
    if (t != null && t != Thread.currentThread()) {
      try { t.join(150); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }
  }

  private void audioLoop() {
    short[] buffer = new short[FFT_SIZE];
    long lastUi = 0;
    while (nativeMicRunning) {
      AudioRecord r = audioRecord;
      if (r == null) break;
      int n;
      try {
        n = r.read(buffer, 0, buffer.length);
      } catch (Exception e) {
        nativeMicError("อ่านเสียงไม่สำเร็จ: " + safeMsg(e.getMessage()));
        break;
      }
      if (n <= 0) continue;
      long now = System.currentTimeMillis();
      if (now - lastUi < 90) continue;
      lastUi = now;
      try {
        NativeAudioFrame frame = analyzePcm(buffer, n);
        pushNativeAudio(frame);
      } catch (Exception ignored) {}
    }
  }

  private static NativeAudioFrame analyzePcm(short[] pcm, int count) {
    int n = 1;
    while ((n << 1) <= count && (n << 1) <= FFT_SIZE) n <<= 1;
    if (n < 256) n = Math.min(256, count);

    double sumSq = 0.0;
    double peak = 0.0;
    for (int i = 0; i < count; i++) {
      double x = pcm[i] / 32768.0;
      sumSq += x * x;
      double a = Math.abs(x);
      if (a > peak) peak = a;
    }
    double rms = Math.sqrt(sumSq / Math.max(1, count));
    double dbfs = 20.0 * Math.log10(Math.max(rms, 1e-9));
    double crest = 20.0 * Math.log10(Math.max(peak, 1e-9) / Math.max(rms, 1e-9));

    if ((n & (n - 1)) != 0) {
      int p = 1;
      while ((p << 1) < n) p <<= 1;
      n = p;
    }

    double[] re = new double[n];
    double[] im = new double[n];
    for (int i = 0; i < n; i++) {
      double window = 0.5 - 0.5 * Math.cos((2.0 * Math.PI * i) / (n - 1));
      re[i] = (pcm[i] / 32768.0) * window;
    }
    fft(re, im);

    int half = n / 2;
    double peakMag = 0.0;
    int peakBin = 0;
    for (int i = 1; i < half; i++) {
      double hz = i * (double) SAMPLE_RATE / n;
      if (hz < 70 || hz > 20000) continue;
      double mag = Math.hypot(re[i], im[i]);
      if (mag > peakMag) {
        peakMag = mag;
        peakBin = i;
      }
    }
    double peakHz = peakBin * (double) SAMPLE_RATE / n;

    final int bands = 96;
    double[] out = new double[bands];
    double fMin = 50.0;
    double fMax = 20000.0;
    for (int b = 0; b < bands; b++) {
      double t0 = b / (double) bands;
      double t1 = (b + 1) / (double) bands;
      double lo = fMin * Math.pow(fMax / fMin, t0);
      double hi = fMin * Math.pow(fMax / fMin, t1);
      int i0 = Math.max(1, (int) Math.floor(lo * n / SAMPLE_RATE));
      int i1 = Math.min(half - 1, Math.max(i0, (int) Math.ceil(hi * n / SAMPLE_RATE)));
      double m = 0.0;
      for (int i = i0; i <= i1; i++) m = Math.max(m, Math.hypot(re[i], im[i]));
      double ref = n * 0.25;
      double bandDb = 20.0 * Math.log10(Math.max(m / ref, 1e-9));
      out[b] = clamp((bandDb + 90.0) / 90.0, 0.0, 1.0);
    }

    double peakNormDb = 20.0 * Math.log10(Math.max(peakMag / (n * 0.25), 1e-9));
    double strength = clamp((peakNormDb + 90.0) / 90.0 * 255.0, 0.0, 255.0);
    return new NativeAudioFrame(dbfs, crest, peakHz, strength, out);
  }

  private static void fft(double[] re, double[] im) {
    int n = re.length;
    for (int i = 1, j = 0; i < n; i++) {
      int bit = n >> 1;
      for (; (j & bit) != 0; bit >>= 1) j ^= bit;
      j ^= bit;
      if (i < j) {
        double tr = re[i]; re[i] = re[j]; re[j] = tr;
        double ti = im[i]; im[i] = im[j]; im[j] = ti;
      }
    }
    for (int len = 2; len <= n; len <<= 1) {
      double ang = -2.0 * Math.PI / len;
      double wLenR = Math.cos(ang);
      double wLenI = Math.sin(ang);
      for (int i = 0; i < n; i += len) {
        double wr = 1.0, wi = 0.0;
        for (int j = 0; j < len / 2; j++) {
          int u = i + j;
          int v = i + j + len / 2;
          double vr = re[v] * wr - im[v] * wi;
          double vi = re[v] * wi + im[v] * wr;
          re[v] = re[u] - vr;
          im[v] = im[u] - vi;
          re[u] += vr;
          im[u] += vi;
          double nwr = wr * wLenR - wi * wLenI;
          wi = wr * wLenI + wi * wLenR;
          wr = nwr;
        }
      }
    }
  }

  private void pushNativeAudio(NativeAudioFrame f) {
    StringBuilder sb = new StringBuilder(900);
    sb.append('{');
    sb.append("\"dbfs\":").append(fmt(f.dbfs)).append(',');
    sb.append("\"crest\":").append(fmt(f.crest)).append(',');
    sb.append("\"peakHz\":").append(fmt(f.peakHz)).append(',');
    sb.append("\"strength\":").append(fmt(f.strength)).append(',');
    sb.append("\"bins\":[");
    for (int i = 0; i < f.bins.length; i++) {
      if (i > 0) sb.append(',');
      sb.append(fmt(f.bins[i]));
    }
    sb.append("]}");
    String json = sb.toString();
    runOnUiThread(() -> webView.evaluateJavascript(
        "window.onNativeAudio&&window.onNativeAudio(" + json + ")", null
    ));
  }

  private void nativeMicError(String msg) {
    String safe = jsQuote(safeMsg(msg));
    runOnUiThread(() -> webView.evaluateJavascript(
        "window.onNativeMicError&&window.onNativeMicError(" + safe + ")", null
    ));
  }

  private void callback(boolean ok, String msg) {
    String safe = msg == null ? "" : msg
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", " ");
    runOnUiThread(() -> webView.evaluateJavascript(
        "window.onNativeOsc&&window.onNativeOsc(" + ok + ", '" + safe + "')", null
    ));
  }

  private static String fmt(double v) {
    if (!Double.isFinite(v)) return "0";
    return String.format(Locale.US, "%.4f", v);
  }

  private static double clamp(double v, double lo, double hi) {
    return Math.max(lo, Math.min(hi, v));
  }

  private static String safeMsg(String s) {
    return s == null || s.trim().isEmpty() ? "Unknown" : s.replace('\n', ' ');
  }

  private static String jsQuote(String s) {
    return "'" + s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ") + "'";
  }

  private static class NativeAudioFrame {
    final double dbfs, crest, peakHz, strength;
    final double[] bins;
    NativeAudioFrame(double dbfs, double crest, double peakHz, double strength, double[] bins) {
      this.dbfs = dbfs;
      this.crest = crest;
      this.peakHz = peakHz;
      this.strength = strength;
      this.bins = bins;
    }
  }

  private static byte[] oscString(String s) {
    byte[] raw = s.getBytes(StandardCharsets.UTF_8);
    int n = raw.length + 1;
    int padded = (n + 3) & ~3;
    byte[] out = new byte[padded];
    System.arraycopy(raw, 0, out, 0, raw.length);
    return out;
  }

  private static byte[] encodeOsc(String address, String type, String value) {
    byte[] a = oscString(address);
    if ("none".equals(type)) return a;
    String tag;
    if ("int".equals(type)) tag = ",i";
    else if ("string".equals(type)) tag = ",s";
    else tag = ",f";
    byte[] t = oscString(tag);
    byte[] v;
    if ("int".equals(type)) {
      v = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(Integer.parseInt(value)).array();
    } else if ("string".equals(type)) {
      v = oscString(value);
    } else {
      v = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putFloat(Float.parseFloat(value)).array();
    }
    byte[] out = new byte[a.length + t.length + v.length];
    System.arraycopy(a, 0, out, 0, a.length);
    System.arraycopy(t, 0, out, a.length, t.length);
    System.arraycopy(v, 0, out, a.length + t.length, v.length);
    return out;
  }
}
