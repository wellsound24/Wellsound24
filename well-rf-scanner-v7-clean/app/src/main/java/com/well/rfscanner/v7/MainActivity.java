package com.well.rfscanner.v7;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;

public class MainActivity extends Activity {
    private static final String ACTION_USB_PERMISSION = "com.well.rfscanner.standard1.USB_PERMISSION";
    private static final int REALTEK_VID = 0x0BDA;
    private static final int RTL2838_PID = 0x2838;
    private static final int IQSRC_REQUEST = 1234;
    private static final int IQ_PORT = 14423;
    private static final int SAMPLE_RATE = 2048000;

    private WebView webView;
    private UsbManager usbManager;
    private UsbDeviceConnection activeConnection;
    private boolean receiverRegistered = false;
    private volatile boolean iqRunning = false;
    private Thread iqThread;
    private Socket iqSocket;

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
        @JavascriptInterface public void connect() { runOnUiThread(() -> connectRf()); }
        @JavascriptInterface public void disconnect() { runOnUiThread(() -> disconnectRf()); }
    }

    private void connectRf() {
        js("document.getElementById('usbText').textContent='Well Connect USB — กำลังเปิด RF engine...';");
        Intent iqIntent = new Intent(Intent.ACTION_VIEW);
        iqIntent.setData(Uri.parse("iqsrc://-a 127.0.0.1 -p " + IQ_PORT + " -s " + SAMPLE_RATE));
        try {
            startActivityForResult(iqIntent, IQSRC_REQUEST);
        } catch (ActivityNotFoundException e) {
            // Keep legacy USB detection as a diagnostic fallback, but do not generate fake RF data.
            connectUsbDiagnostic();
            js("document.getElementById('usbText').textContent='Well Connect USB — พบ USB แต่ยังไม่มี RTL-SDR IQ driver';document.getElementById('engineVal').textContent='DRIVER NEEDED';");
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != IQSRC_REQUEST) return;
        if (resultCode == RESULT_OK) {
            js("nativeUsbConnected();document.getElementById('usbText').textContent='Well Connect USB — RF IQ CONNECTED';document.getElementById('engineVal').textContent='REAL IQ';");
            startIqTcp();
        } else {
            String msg = data != null ? data.getStringExtra("detailed_exception_message") : null;
            if (msg == null || msg.length() == 0) msg = "เปิด RTL-SDR ไม่สำเร็จ";
            final String safe = msg.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ");
            js("nativeUsbDisconnected();document.getElementById('usbText').textContent='Well Connect USB — " + safe + "';document.getElementById('engineVal').textContent='IDLE';");
        }
    }

    private void startIqTcp() {
        stopIqTcp();
        iqRunning = true;
        iqThread = new Thread(() -> {
            try {
                Socket s = new Socket();
                iqSocket = s;
                boolean connected = false;
                for (int n = 0; n < 30 && iqRunning; n++) {
                    try {
                        s.connect(new InetSocketAddress("127.0.0.1", IQ_PORT), 500);
                        connected = true;
                        break;
                    } catch (Throwable ignored) {
                        try { Thread.sleep(150); } catch (InterruptedException ie) { return; }
                        if (!s.isConnected()) s = new Socket();
                        iqSocket = s;
                    }
                }
                if (!connected) throw new Exception("IQ TCP connect timeout");
                InputStream in = s.getInputStream();
                byte[] header = new byte[12];
                int hp = 0;
                while (hp < 12 && iqRunning) {
                    int r = in.read(header, hp, 12 - hp);
                    if (r < 0) throw new Exception("IQ header EOF");
                    hp += r;
                }
                byte[] buf = new byte[16384];
                while (iqRunning) {
                    int r = in.read(buf);
                    if (r <= 0) break;
                    if ((r & 1) != 0) r--;
                    if (r >= 2048) pushSpectrumFromIq(buf, r);
                }
            } catch (Throwable t) {
                if (iqRunning) js("document.getElementById('usbText').textContent='Well Connect USB — IQ stream หลุด';document.getElementById('engineVal').textContent='IDLE';");
            } finally {
                iqRunning = false;
                try { if (iqSocket != null) iqSocket.close(); } catch (Throwable ignored) {}
                iqSocket = null;
            }
        }, "WellRF-IQ");
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
        // Lightweight DFT with Hann window. This is intentionally capped to 192 bins for phone CPU load.
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
        StringBuilder sb = new StringBuilder(1600);
        sb.append("data=[");
        for (int i = 0; i < out.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format(java.util.Locale.US, "%.4f", out[i]));
        }
        sb.append("];if(typeof draw==='function'){draw();waterfall();metrics();}");
        js(sb.toString());
    }

    private void disconnectRf() {
        stopIqTcp();
        closeUsb();
        js("nativeUsbDisconnected();document.getElementById('engineVal').textContent='IDLE';");
    }

    private void stopIqTcp() {
        iqRunning = false;
        try { if (iqSocket != null) iqSocket.close(); } catch (Throwable ignored) {}
        iqSocket = null;
        if (iqThread != null) iqThread.interrupt();
        iqThread = null;
    }

    private void connectUsbDiagnostic() {
        try {
            usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
            if (usbManager == null) return;
            registerReceiverOnce();
            UsbDevice d = findRtl();
            if (d == null) return;
            if (usbManager.hasPermission(d)) openDevice(d);
            else {
                Intent i = new Intent(ACTION_USB_PERMISSION).setPackage(getPackageName());
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
                PendingIntent pi = PendingIntent.getBroadcast(this, 77, i, flags);
                usbManager.requestPermission(d, pi);
            }
        } catch (Throwable ignored) {}
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
            if (d.getVendorId() == REALTEK_VID) return d;
        }
        return null;
    }

    private void openDevice(UsbDevice d) {
        closeUsb();
        activeConnection = usbManager.openDevice(d);
        if (activeConnection != null) js("document.getElementById('usbText').textContent='Well Connect USB — USB DETECTED';");
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
        stopIqTcp();
        closeUsb();
        if (receiverRegistered) {
            try { unregisterReceiver(permissionReceiver); } catch (Throwable ignored) {}
        }
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
