package com.wellsound.assistantpro;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
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
  private static final int REQ_MIC = 501;
  private static final int REQ_FILE = 502;
  private WebView webView;
  private ValueCallback<Uri[]> fileCallback;
  private final ExecutorService io = Executors.newSingleThreadExecutor();

  @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    webView = new WebView(this);
    webView.getSettings().setJavaScriptEnabled(true);
    webView.getSettings().setDomStorageEnabled(true);
    webView.getSettings().setAllowFileAccess(true);
    webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
    webView.setWebViewClient(new WebViewClient());
    webView.setWebChromeClient(new WebChromeClient(){
      @Override public void onPermissionRequest(PermissionRequest request) {
        runOnUiThread(() -> {
          if (android.os.Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
          }
          request.grant(request.getResources());
        });
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
    if (android.os.Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
    }
    webView.loadUrl("file:///android_asset/index.html");
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

  @Override public void onBackPressed() {
    if (webView.canGoBack()) webView.goBack(); else super.onBackPressed();
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
        } catch (Exception e) { callback(false, e.getMessage()); }
      });
    }
    @JavascriptInterface public void query(String ip, int port, String address) { send(ip, port, address, "none", ""); }
    @JavascriptInterface public boolean hasMicPermission() {
      return android.os.Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }
    @JavascriptInterface public void requestMicPermission() {
      runOnUiThread(() -> {
        if (android.os.Build.VERSION.SDK_INT >= 23) requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
      });
    }
    @JavascriptInterface public void openAppSettings() {
      runOnUiThread(() -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
    }
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
    if ("int".equals(type)) v = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(Integer.parseInt(value)).array();
    else if ("string".equals(type)) v = oscString(value);
    else v = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putFloat(Float.parseFloat(value)).array();
    byte[] out = new byte[a.length+t.length+v.length];
    System.arraycopy(a,0,out,0,a.length); System.arraycopy(t,0,out,a.length,t.length); System.arraycopy(v,0,out,a.length+t.length,v.length); return out;
  }
}
