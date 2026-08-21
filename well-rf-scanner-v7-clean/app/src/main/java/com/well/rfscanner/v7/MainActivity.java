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
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String ACTION_USB_PERMISSION = "com.well.rfscanner.standard1.USB_PERMISSION";
    private static final int REALTEK_VID = 0x0BDA;
    private static final int RTL2838_PID = 0x2838;
    private static final int SAMPLE_RATE = 2_048_000;
    private static final long CENTER_HZ = 700_000_000L;

    private WebView webView;
    private UsbManager usbManager;
    private UsbDeviceConnection activeConnection;
    private DirectRtlSdr rtl;
    private boolean receiverRegistered = false;
    private volatile boolean iqRunning = false;
    private Thread iqThread;

    private final BroadcastReceiver permissionReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!ACTION_USB_PERMISSION.equals(intent.getAction())) return;
            UsbDevice d = getUsbDevice(intent);
            boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
            if (granted && d != null) startDirectRf(d);
            else js("nativeUsbDisconnected();document.getElementById('usbText').textContent='Well Connect USB — ไม่อนุญาต USB';document.getElementById('engineVal').textContent='IDLE';");
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
        @JavascriptInterface public void connect() { runOnUiThread(() -> connectRf()); }
        @JavascriptInterface public void disconnect() { runOnUiThread(() -> disconnectRf()); }
    }

    private void connectRf() {
        try {
            js("document.getElementById('usbText').textContent='Well Connect USB — กำลังเปิด RTL-SDR โดยตรง...';document.getElementById('engineVal').textContent='STARTING';");
            usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
            if (usbManager == null) {
                js("document.getElementById('usbText').textContent='Well Connect USB — USB HOST ไม่พร้อม';document.getElementById('engineVal').textContent='IDLE';");
                return;
            }
            registerReceiverOnce();
            UsbDevice d = findRtl();
            if (d == null) {
                js("document.getElementById('usbText').textContent='Well Connect USB — ไม่พบ RTL-SDR';document.getElementById('engineVal').textContent='IDLE';");
                return;
            }
            if (usbManager.hasPermission(d)) startDirectRf(d);
            else {
                Intent i = new Intent(ACTION_USB_PERMISSION).setPackage(getPackageName());
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
                usbManager.requestPermission(d, PendingIntent.getBroadcast(this, 77, i, flags));
                js("document.getElementById('usbText').textContent='Well Connect USB — รออนุญาต USB';");
            }
        } catch (Throwable t) {
            showRfError(t);
        }
    }

    private void startDirectRf(UsbDevice d) {
        stopIq();
        closeUsb();
        try {
            activeConnection = usbManager.openDevice(d);
            if (activeConnection == null) throw new Exception("เปิด USB ไม่สำเร็จ");
            rtl = new DirectRtlSdr(d, activeConnection, 0.5);
            rtl.open();
            int actualRate = rtl.setSampleRate(SAMPLE_RATE);
            long actualCenter = rtl.setCenterFrequency(CENTER_HZ);
            rtl.resetBuffer();

            double halfMhz = actualRate / 2_000_000.0;
            double centerMhz = actualCenter / 1_000_000.0;
            String jsCode = String.format(Locale.US,
                    "window.nativeCaptureStart=%.6f;window.nativeCaptureEnd=%.6f;nativeUsbConnected();document.getElementById('usbText').textContent='Well Connect USB — REAL RF %.3f MHz';document.getElementById('engineVal').textContent='REAL IQ';",
                    centerMhz-halfMhz, centerMhz+halfMhz, centerMhz);
            js(jsCode);
            startIq();
        } catch (Throwable t) {
            showRfError(t);
            closeUsb();
        }
    }

    private void startIq() {
        iqRunning = true;
        iqThread = new Thread(() -> {
            byte[] buf = new byte[16 * 1024];
            try {
                while (iqRunning && rtl != null) {
                    int r = rtl.read(buf, 1000);
                    if (r < 0) continue;
                    if ((r & 1) != 0) r--;
                    if (r >= 2048) pushSpectrumFromIq(buf, r);
                }
            } catch (Throwable t) {
                if (iqRunning) showRfError(t);
            }
        }, "WellRF-DirectIQ");
        iqThread.start();
    }

    private void pushSpectrumFromIq(byte[] iq, int len) {
        final int bins = 192;
        final int n = Math.min(2048, len / 2);
        if (n < 512) return;
        double meanI = 0, meanQ = 0;
        for (int i = 0; i < n; i++) {
            meanI += (iq[i * 2] & 0xff) - 127.5;
            meanQ += (iq[i * 2 + 1] & 0xff) - 127.5;
        }
        meanI /= n; meanQ /= n;
        float[] out = new float[bins];
        for (int k = 0; k < bins; k++) {
            int signedK = k - bins / 2;
            double re = 0, im = 0;
            for (int i = 0; i < n; i += 4) {
                double wi = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / (n - 1));
                double iv = ((iq[i * 2] & 0xff) - 127.5) - meanI;
                double qv = ((iq[i * 2 + 1] & 0xff) - 127.5) - meanQ;
                double a = -2.0 * Math.PI * signedK * i / bins;
                double ca = Math.cos(a), sa = Math.sin(a);
                re += wi * (iv * ca - qv * sa);
                im += wi * (iv * sa + qv * ca);
            }
            double mag = Math.sqrt(re * re + im * im) / (n / 4.0);
            double db = 20.0 * Math.log10(mag / 128.0 + 1e-9);
            double norm = (db + 90.0) / 75.0;
            if (norm < 0) norm = 0;
            if (norm > 1) norm = 1;
            out[k] = (float) norm;
        }
        StringBuilder sb = new StringBuilder(1800);
        sb.append("data=[");
        for (int i = 0; i < out.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format(Locale.US, "%.4f", out[i]));
        }
        sb.append("];if(typeof draw==='function'){draw();waterfall();metrics();}");
        js(sb.toString());
    }

    private void disconnectRf() {
        stopIq();
        closeUsb();
        js("window.nativeCaptureStart=null;window.nativeCaptureEnd=null;nativeUsbDisconnected();document.getElementById('engineVal').textContent='IDLE';");
    }

    private void stopIq() {
        iqRunning = false;
        if (iqThread != null) iqThread.interrupt();
        iqThread = null;
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
            if (d.getVendorId() == REALTEK_VID && d.getProductId() == RTL2838_PID) return d;
        }
        for (UsbDevice d : devices.values()) if (d.getVendorId() == REALTEK_VID) return d;
        return null;
    }

    private void closeUsb() {
        try { if (rtl != null) rtl.close(); } catch (Throwable ignored) {}
        rtl = null;
        if (activeConnection != null) {
            try { activeConnection.close(); } catch (Throwable ignored) {}
        }
        activeConnection = null;
    }

    private void showRfError(Throwable t) {
        String msg = t == null || t.getMessage() == null ? "RF ERROR" : t.getMessage();
        msg = msg.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ");
        js("nativeUsbDisconnected();document.getElementById('usbText').textContent='Well Connect USB — " + msg + "';document.getElementById('engineVal').textContent='IDLE';");
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
        stopIq();
        closeUsb();
        if (receiverRegistered) {
            try { unregisterReceiver(permissionReceiver); } catch (Throwable ignored) {}
        }
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
