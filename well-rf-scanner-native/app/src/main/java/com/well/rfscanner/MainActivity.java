package com.well.rfscanner;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.HashMap;

public class MainActivity extends Activity {
    private static final String ACTION_USB_PERMISSION = "com.well.rfscanner.USB_PERMISSION";
    private static final int REALTEK_VID = 0x0BDA;
    private static final int RTL2838_PID = 0x2838;

    private UsbManager usbManager;
    private UsbDevice activeDevice;
    private UsbDeviceConnection activeConnection;
    private TextView statusText;
    private TextView engineText;
    private TextView deviceText;
    private Button connectButton;
    private Button disconnectButton;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable autoCheck = new Runnable() {
        @Override public void run() {
            autoDetectUsb();
            handler.postDelayed(this, 1200);
        }
    };

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                UsbDevice device = getUsbDevice(intent);
                boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                if (granted && device != null) openUsb(device);
                else setStatus(false, "พบ Well Connect USB แต่ยังไม่ได้อนุญาต USB");
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                UsbDevice device = getUsbDevice(intent);
                if (device != null && isRtlCandidate(device)) requestOrOpen(device);
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = getUsbDevice(intent);
                if (device != null && activeDevice != null && device.getDeviceId() == activeDevice.getDeviceId()) {
                    closeUsb();
                    setStatus(false, "Well Connect USB — DISCONNECTED");
                }
            }
        }
    };

    @SuppressWarnings("deprecation")
    private UsbDevice getUsbDevice(Intent intent) {
        if (intent == null) return null;
        if (Build.VERSION.SDK_INT >= 33) {
            return intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class);
        }
        return (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        buildUi();

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(usbReceiver, filter);

        UsbDevice fromIntent = getUsbDevice(getIntent());
        if (fromIntent != null && isRtlCandidate(fromIntent)) requestOrOpen(fromIntent);
        handler.post(autoCheck);
    }

    private void buildUi() {
        int pad = dp(18);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.rgb(9, 12, 16));

        TextView title = new TextView(this);
        title.setText("WELL RF SCANNER PRO");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setPadding(0, dp(8), 0, dp(18));
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Native USB Test • RTL-SDR");
        sub.setTextColor(Color.rgb(148, 163, 184));
        sub.setTextSize(13);
        sub.setGravity(Gravity.CENTER_HORIZONTAL);
        sub.setPadding(0, 0, 0, dp(22));
        root.addView(sub);

        deviceText = makeInfo("Device: Well Connect USB");
        root.addView(deviceText);

        statusText = makeInfo("Status: กำลังตรวจหา Well Connect USB...");
        root.addView(statusText);

        engineText = makeInfo("USB Engine: IDLE");
        root.addView(engineText);

        connectButton = makeButton("CONNECT", Color.rgb(22, 163, 74));
        connectButton.setOnClickListener(v -> {
            pressEffect(v);
            UsbDevice d = findRtlDevice();
            if (d != null) requestOrOpen(d);
            else setStatus(false, "ไม่พบ Well Connect USB");
        });
        root.addView(connectButton);

        disconnectButton = makeButton("DISCONNECT", Color.rgb(185, 28, 28));
        disconnectButton.setOnClickListener(v -> {
            pressEffect(v);
            closeUsb();
            setStatus(false, "Well Connect USB — DISCONNECTED");
        });
        root.addView(disconnectButton);

        TextView note = new TextView(this);
        note.setText("เสียบ RTL-SDR แล้วแอปจะตรวจหาอัตโนมัติทุก 1.2 วินาที และตอบสนองต่อ USB attach/detach โดยตรงจาก Android\n\nหมายเหตุ: Android อาจขออนุญาต USB ครั้งแรกก่อน หลังได้รับอนุญาตแอปจะเปิดการเชื่อมต่อให้อัตโนมัติ");
        note.setTextColor(Color.rgb(148, 163, 184));
        note.setTextSize(13);
        note.setPadding(0, dp(18), 0, dp(18));
        root.addView(note);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private TextView makeInfo(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(16);
        t.setPadding(dp(14), dp(14), dp(14), dp(14));
        t.setBackgroundColor(Color.rgb(20, 27, 36));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        t.setLayoutParams(lp);
        return t;
    }

    private Button makeButton(String text, int color) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(16);
        b.setBackgroundColor(color);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(58));
        lp.setMargins(0, dp(6), 0, dp(6));
        b.setLayoutParams(lp);
        return b;
    }

    private void pressEffect(View v) {
        v.animate().translationY(dp(2)).scaleX(0.98f).scaleY(0.98f).setDuration(60)
            .withEndAction(() -> v.animate().translationY(0).scaleX(1f).scaleY(1f).setDuration(80).start()).start();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private boolean isRtlCandidate(UsbDevice d) {
        return d != null && (d.getVendorId() == REALTEK_VID || d.getProductId() == RTL2838_PID);
    }

    private UsbDevice findRtlDevice() {
        HashMap<String, UsbDevice> list = usbManager.getDeviceList();
        for (UsbDevice d : list.values()) if (isRtlCandidate(d)) return d;
        return null;
    }

    private void autoDetectUsb() {
        UsbDevice d = findRtlDevice();
        if (d != null) {
            if (activeConnection == null) requestOrOpen(d);
        } else if (activeConnection == null) {
            setStatus(false, "Well Connect USB — DISCONNECTED");
        }
    }

    private PendingIntent permissionIntent() {
        Intent i = new Intent(ACTION_USB_PERMISSION).setPackage(getPackageName());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
        return PendingIntent.getBroadcast(this, 0, i, flags);
    }

    private void requestOrOpen(UsbDevice d) {
        if (d == null) return;
        if (activeConnection != null && activeDevice != null && activeDevice.getDeviceId() == d.getDeviceId()) return;
        if (usbManager.hasPermission(d)) openUsb(d);
        else {
            setStatus(false, "พบ Well Connect USB — รออนุญาตการเชื่อมต่อ");
            usbManager.requestPermission(d, permissionIntent());
        }
    }

    private void openUsb(UsbDevice d) {
        closeUsb();
        UsbDeviceConnection c = usbManager.openDevice(d);
        if (c == null) {
            setStatus(false, "เปิด Well Connect USB ไม่สำเร็จ");
            return;
        }
        activeDevice = d;
        activeConnection = c;
        setStatus(true, "Well Connect USB — CONNECTED");
    }

    private void closeUsb() {
        if (activeConnection != null) {
            try { activeConnection.close(); } catch (Exception ignored) {}
        }
        activeConnection = null;
        activeDevice = null;
    }

    private void setStatus(boolean connected, String text) {
        runOnUiThread(() -> {
            if (statusText != null) {
                statusText.setText("Status: " + text);
                statusText.setTextColor(connected ? Color.rgb(74, 222, 128) : Color.WHITE);
            }
            if (engineText != null) engineText.setText("USB Engine: " + (connected ? "USB READY" : "IDLE"));
            if (deviceText != null) deviceText.setText("Device: Well Connect USB");
        });
    }

    @Override protected void onResume() {
        super.onResume();
        autoDetectUsb();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        UsbDevice device = getUsbDevice(intent);
        if (device != null && isRtlCandidate(device)) requestOrOpen(device);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(autoCheck);
        closeUsb();
        try { unregisterReceiver(usbReceiver); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
