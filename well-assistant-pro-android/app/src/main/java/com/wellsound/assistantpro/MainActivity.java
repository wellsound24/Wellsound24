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

import java.io.BufferedReader;
import java.io.InputStreamReader;
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
  private PermissionRequest pendingWebPermissionRequest;
  private final ExecutorService io = Executors.newSingleThreadExecutor();

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

    // Load the bundled UI under a secure HTTPS origin instead of file://.
    // Android WebView/Chromium can reject getUserMedia() on file origins on some devices.
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

      webView.loadDataWithBaseURL(
          "https://wellassistant.local/",
          html.toString(),
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

      final String js = granted
          ? "window.onNativeMicPermission && window.onNativeMicPermission(true)"
          : "window.onNativeMicPermission && window.onNativeMicPermission(false)";
      runOnUiThread(() -> webView.evaluateJavascript(js, null));
    }
  }

  @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == REQ_FILE && fileCallback != null) {
      Uri[] result = null;
      if (resultCode == RESULT_OK && data != null && data.getData() != null) {
        result = new Uri[]{data.getData()};
      }
      fileCallback.onReceiveValue(result);
      fileCallback = null;
    }
  }

  @Override public void onBackPressed() {
    if (webView.canGoBack()) webView.goBack();
    else super.onBackPressed();
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
          webView.evaluateJavascript(
              "window.onNativeMicPermission && window.onNativeMicPermission(true)", null
          );
        } else {
          requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
        }
      });
    }

    @JavascriptInterface public void openAppSettings() {
      runOnUiThread(() -> startActivity(new Intent(
          Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
          Uri.parse("package:" + getPackageName())
      )));
    }
  }

  private void callback(boolean ok, String msg) {
    String safe = msg == null ? "" : msg
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", " ");
    runOnUiThread(() -> webView.evaluateJavascript(
        "window.onNativeOsc && window.onNativeOsc(" + ok + ", '" + safe + "')",
        null
    ));
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
