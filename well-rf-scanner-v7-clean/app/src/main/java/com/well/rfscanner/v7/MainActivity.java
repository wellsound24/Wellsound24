package com.well.rfscanner.v7;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.util.HashMap;

public class MainActivity extends Activity {
    private static final String ACTION_USB_PERMISSION = "com.well.rfscanner.standard1.USB_PERMISSION";
    private static final int REALTEK_VID = 0x0BDA;
    private static final int RTL2838_PID = 0x2838;

    private WebView webView;
    private UsbManager usbManager;
    private UsbDeviceConnection activeConnection;
    private boolean receiverRegistered = false;

    private final BroadcastReceiver permissionReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!ACTION_USB_PERMISSION.equals(intent.getAction())) return;
            UsbDevice d = getUsbDevice(intent);
            boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
            if (granted && d != null) openDevice(d);
            else js("nativeUsbDisconnected();document.getElementById('usbText').textContent='Well Connect USB — ไม่อนุญาต USB';");
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new AndroidUsbBridge(), "AndroidUSB");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    public class AndroidUsbBridge {
        @JavascriptInterface public void connect() { runOnUiThread(() -> connectUsb()); }
        @JavascriptInterface public void disconnect() { runOnUiThread(() -> { closeUsb(); js("nativeUsbDisconnected();"); }); }
    }

    private void connectUsb() {
        try {
            usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
            if (usbManager == null) {
                js("document.getElementById('usbText').textContent='Well Connect USB — USB HOST ไม่พร้อม';");
                return;
            }
            registerReceiverOnce();
            UsbDevice d = findRtl();
            if (d == null) {
                js("document.getElementById('usbText').textContent='Well Connect USB — ไม่พบ RTL-SDR';");
                return;
            }
            if (usbManager.hasPermission(d)) {
                openDevice(d);
            } else {
                Intent i = new Intent(ACTION_USB_PERMISSION).setPackage(getPackageName());
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
                PendingIntent pi = PendingIntent.getBroadcast(this, 77, i, flags);
                usbManager.requestPermission(d, pi);
                js("document.getElementById('usbText').textContent='Well Connect USB — รออนุญาต USB';");
            }
        } catch (Throwable t) {
            js("document.getElementById('usbText').textContent='Well Connect USB — USB ERROR';");
        }
    }

    private void registerReceiverOnce() {
        if (receiverRegistered) return;
        IntentFilter f = new IntentFilter(ACTION_USB_PERMISSION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(permissionReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(permissionReceiver, f);
        receiverRegistered = true;
    }

    private UsbDevice findRtl() {
        HashMap<String, UsbDevice> devices = usbManager.getDeviceList();
        for (UsbDevice d : devices.values()) {
            if (d.getVendorId() == REALTEK_VID || d.getProductId() == RTL2838_PID) return d;
        }
        return null;
    }

    private void openDevice(UsbDevice d) {
        closeUsb();
        activeConnection = usbManager.openDevice(d);
        if (activeConnection != null) js("nativeUsbConnected();");
        else js("nativeUsbDisconnected();document.getElementById('usbText').textContent='Well Connect USB — เปิด USB ไม่สำเร็จ';");
    }

    private void closeUsb() {
        if (activeConnection != null) {
            try { activeConnection.close(); } catch (Throwable ignored) {}
        }
        activeConnection = null;
    }

    private void js(String code) {
        if (webView == null) return;
        runOnUiThread(() -> webView.evaluateJavascript(code, null));
    }

    @SuppressWarnings("deprecation")
    private UsbDevice getUsbDevice(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) return intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class);
        return (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        closeUsb();
        if (receiverRegistered) {
            try { unregisterReceiver(permissionReceiver); } catch (Throwable ignored) {}
        }
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
