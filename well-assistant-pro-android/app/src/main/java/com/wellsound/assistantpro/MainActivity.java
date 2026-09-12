package com.wellsound.assistantpro;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
  private WebView webView;
  private final ExecutorService io = Executors.newSingleThreadExecutor();

  @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    webView = new WebView(this);
    webView.getSettings().setJavaScriptEnabled(true);
    webView.getSettings().setDomStorageEnabled(true);
    webView.setWebViewClient(new WebViewClient());
    webView.setWebChromeClient(new WebChromeClient());
    webView.addJavascriptInterface(new NativeOsc(), "WellNative");
    setContentView(webView);
    webView.loadUrl("file:///android_asset/index.html");
  }

  @Override public void onBackPressed() {
    if (webView.canGoBack()) webView.goBack(); else super.onBackPressed();
  }

  public class NativeOsc {
    @JavascriptInterface public void send(String ip, int port, String address, String type, String value) {
      io.execute(() -> {
        try {
          byte[] data = encodeOsc(address, type, value);
          DatagramSocket socket = new DatagramSocket();
          socket.send(new DatagramPacket(data, data.length, InetAddress.getByName(ip), port));
          socket.close();
          callback(true, "ส่ง OSC สำเร็จ: " + address);
        } catch (Exception e) { callback(false, e.getMessage()); }
      });
    }
    @JavascriptInterface public void query(String ip, int port, String address) { send(ip, port, address, "none", ""); }
  }

  private void callback(boolean ok, String msg) {
    String safe = msg == null ? "" : msg.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ");
    runOnUiThread(() -> webView.evaluateJavascript("window.onNativeOsc && window.onNativeOsc(" + ok + ", '" + safe + "')", null));
  }

  private static byte[] oscString(String s) {
    byte[] raw = s.getBytes(StandardCharsets.UTF_8);
    int n = raw.length + 1; int padded = (n + 3) & ~3;
    byte[] out = new byte[padded]; System.arraycopy(raw,0,out,0,raw.length); return out;
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
    if ("int".equals(type)) { v = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(Integer.parseInt(value)).array(); }
    else if ("string".equals(type)) { v = oscString(value); }
    else { v = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putFloat(Float.parseFloat(value)).array(); }
    byte[] out = new byte[a.length+t.length+v.length];
    System.arraycopy(a,0,out,0,a.length); System.arraycopy(t,0,out,a.length,t.length); System.arraycopy(v,0,out,a.length+t.length,v.length); return out;
  }
}
