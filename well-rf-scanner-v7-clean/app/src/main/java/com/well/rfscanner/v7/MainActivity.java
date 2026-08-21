package com.well.rfscanner.v7;

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
    private static final String ACTION_USB_PERMISSION = "com.well.rfscanner.v7.USB_PERMISSION";
    private static final int REALTEK_VID = 0x0BDA;
    private static final int RTL2838_PID = 0x2838;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private UsbManager usbManager;
    private UsbDevice activeDevice;
    private UsbDeviceConnection activeConnection;
    private TextView statusText;
    private TextView deviceText;
    private TextView usbText;
    private boolean receiverRegistered = false;

    private final BroadcastReceiver permissionReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!ACTION_USB_PERMISSION.equals(intent.getAction())) return;
            UsbDevice d = getUsbDevice(intent);
            boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
            if (granted && d != null) openDevice(d);
            else setStatus("USB permission denied", false);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        handler.postDelayed(this::initUsb, 800);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(22));
        root.setBackgroundColor(Color.rgb(8, 12, 18));

        TextView title = new TextView(this);
        title.setText("WELL RF SCANNER PRO");
        title.setTextColor(Color.WHITE);
        title.setTextSize(25);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(8), 0, dp(4));
        root.addView(title);

        TextView ver = new TextView(this);
        ver.setText("v8 REAL USB BASE");
        ver.setTextColor(Color.rgb(74, 222, 128));
        ver.setTextSize(14);
        ver.setGravity(Gravity.CENTER);
        ver.setPadding(0, 0, 0, dp(20));
        root.addView(ver);

        deviceText = card("Device: searching...");
        statusText = card("Status: APP READY");
        usbText = card("USB: not connected");
        root.addView(deviceText);
        root.addView(statusText);
        root.addView(usbText);

        Button connect = button("CONNECT WELL USB", Color.rgb(22, 163, 74));
        connect.setOnClickListener(v -> {
            press(v);
            initUsb();
            UsbDevice d = findRtl();
            if (d == null) setStatus("ไม่พบ RTL-SDR / Well Connect USB", false);
            else requestOrOpen(d);
        });
        root.addView(connect);

        Button refresh = button("SEARCH USB", Color.rgb(30, 64, 175));
        refresh.setOnClickListener(v -> {
            press(v);
            initUsb();
            UsbDevice d = findRtl();
            if (d == null) setStatus("ไม่พบ RTL-SDR", false);
            else {
                showDevice(d);
                setStatus("พบ RTL-SDR แล้ว กด CONNECT", true);
            }
        });
        root.addView(refresh);

        Button disconnect = button("DISCONNECT", Color.rgb(185, 28, 28));
        disconnect.setOnClickListener(v -> {
            press(v);
            closeUsb();
            setStatus("DISCONNECTED", false);
        });
        root.addView(disconnect);

        TextView note = new TextView(this);
        note.setText("ส่วนนี้เชื่อมต่อ USB จริงกับ RTL-SDR ผ่าน Android USB Host และขอสิทธิ์จากระบบจริง\n\nRF spectrum / waterfall ยังไม่ถูกจำลองในรุ่นนี้ และจะเปิดใช้หลังเพิ่ม native RTL-SDR sampling driver บนฐานที่เปิดแอปได้แล้ว");
        note.setTextColor(Color.rgb(148, 163, 184));
        note.setTextSize(13);
        note.setPadding(0, dp(20), 0, dp(10));
        root.addView(note);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private void initUsb() {
        try {
            usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
            if (usbManager == null) {
                setStatus("Android USB Host service unavailable", false);
                return;
            }
            registerReceiverOnce();
            UsbDevice d = findRtl();
            if (d != null) {
                showDevice(d);
                setStatus("พบ RTL-SDR พร้อมเชื่อมต่อ", true);
            } else {
                deviceText.setText("Device: Well Connect USB / RTL-SDR");
                usbText.setText("USB: waiting for device");
            }
        } catch (Throwable t) {
            setStatus("USB init error: " + t.getClass().getSimpleName(), false);
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
        if (usbManager == null) return null;
        HashMap<String, UsbDevice> list = usbManager.getDeviceList();
        if (list == null) return null;
        for (UsbDevice d : list.values()) {
            if (d.getVendorId() == REALTEK_VID || d.getProductId() == RTL2838_PID) return d;
        }
        return null;
    }

    private void requestOrOpen(UsbDevice d) {
        if (usbManager == null || d == null) return;
        if (usbManager.hasPermission(d)) {
            openDevice(d);
            return;
        }
        Intent i = new Intent(ACTION_USB_PERMISSION).setPackage(getPackageName());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
        PendingIntent pi = PendingIntent.getBroadcast(this, 801, i, flags);
        setStatus("รออนุญาต USB จาก Android", true);
        usbManager.requestPermission(d, pi);
    }

    private void openDevice(UsbDevice d) {
        try {
            closeUsb();
            UsbDeviceConnection c = usbManager.openDevice(d);
            if (c == null) {
                setStatus("เปิด RTL-SDR ไม่สำเร็จ", false);
                return;
            }
            activeDevice = d;
            activeConnection = c;
            showDevice(d);
            usbText.setText("USB: CONNECTED | FD=" + c.getFileDescriptor());
            setStatus("Well Connect USB — CONNECTED", true);
        } catch (Throwable t) {
            setStatus("USB open error: " + t.getClass().getSimpleName(), false);
        }
    }

    private void showDevice(UsbDevice d) {
        String product = d.getProductName();
        if (product == null || product.trim().isEmpty()) product = "RTL2838 / RTL-SDR";
        deviceText.setText("Device: Well Connect USB\nHW: " + product + "\nVID: 0x" + Integer.toHexString(d.getVendorId()).toUpperCase() + "  PID: 0x" + Integer.toHexString(d.getProductId()).toUpperCase());
        usbText.setText("USB: detected | Android ID=" + d.getDeviceId());
    }

    @SuppressWarnings("deprecation")
    private UsbDevice getUsbDevice(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) return intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class);
        return (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
    }

    private void closeUsb() {
        if (activeConnection != null) {
            try { activeConnection.close(); } catch (Throwable ignored) {}
        }
        activeConnection = null;
        activeDevice = null;
        if (usbText != null) usbText.setText("USB: not connected");
    }

    private TextView card(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(16);
        t.setPadding(dp(14), dp(14), dp(14), dp(14));
        t.setBackgroundColor(Color.rgb(20, 28, 38));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        t.setLayoutParams(lp);
        return t;
    }

    private Button button(String text, int color) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setBackgroundColor(color);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(58));
        lp.setMargins(0, dp(5), 0, dp(5));
        b.setLayoutParams(lp);
        return b;
    }

    private void setStatus(String text, boolean positive) {
        if (statusText != null) {
            statusText.setText("Status: " + text);
            statusText.setTextColor(positive ? Color.rgb(74, 222, 128) : Color.WHITE);
        }
    }

    private void press(View v) {
        v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(60).withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(80).start()).start();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override protected void onDestroy() {
        closeUsb();
        if (receiverRegistered) {
            try { unregisterReceiver(permissionReceiver); } catch (Throwable ignored) {}
        }
        super.onDestroy();
    }
}
