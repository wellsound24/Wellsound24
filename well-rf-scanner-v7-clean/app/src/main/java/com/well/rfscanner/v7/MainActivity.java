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
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.HashMap;

public class MainActivity extends Activity {
    private static final String ACTION_USB_PERMISSION = "com.well.rfscanner.v7.USB_PERMISSION";
    private static final int REALTEK_VID = 0x0BDA;
    private static final int RTL2838_PID = 0x2838;

    private UsbManager usbManager;
    private UsbDevice activeDevice;
    private UsbDeviceConnection activeConnection;
    private TextView usbState, rfEngine, deviceInfo;
    private boolean receiverRegistered = false;

    private final BroadcastReceiver permissionReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!ACTION_USB_PERMISSION.equals(intent.getAction())) return;
            UsbDevice d = getUsbDevice(intent);
            boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
            if (granted && d != null) openDevice(d);
            else setUsbState("USB PERMISSION DENIED", false);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildOriginalUi();
        initUsb();
    }

    private void buildOriginalUi() {
        int bg = Color.rgb(7, 10, 15);
        int panel = Color.rgb(15, 20, 28);
        int line = Color.rgb(31, 41, 55);
        int muted = Color.rgb(148, 163, 184);
        int green = Color.rgb(34, 197, 94);
        int blue = Color.rgb(37, 99, 235);
        int red = Color.rgb(220, 38, 38);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(26));
        root.setBackgroundColor(bg);

        TextView title = text("WELL RF SCANNER PRO", 25, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView sub = text("Professional Preview", 13, muted, false);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 0, 0, dp(14));
        root.addView(sub);

        LinearLayout tabs = row();
        String[] tabNames = {"SCAN", "COORDINATE", "MICS", "MONITOR", "PROJECT"};
        for (String n : tabNames) {
            TextView t = text(n, 11, n.equals("SCAN") ? Color.WHITE : muted, n.equals("SCAN"));
            t.setGravity(Gravity.CENTER);
            t.setBackgroundColor(n.equals("SCAN") ? Color.rgb(30, 41, 59) : panel);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1f);
            lp.setMargins(dp(2), 0, dp(2), 0);
            t.setLayoutParams(lp);
            tabs.addView(t);
        }
        root.addView(tabs);
        spacer(root, 12);

        TextView usbHeader = text("Well Connect USB", 18, Color.WHITE, true);
        root.addView(usbHeader);
        usbState = card("DISCONNECTED", panel, line);
        usbState.setTextColor(Color.rgb(248, 113, 113));
        root.addView(usbState);
        deviceInfo = card("RTL-SDR: waiting for device", panel, line);
        deviceInfo.setTextColor(muted);
        root.addView(deviceInfo);

        LinearLayout usbButtons = row();
        Button connect = button("CONNECT", green);
        Button disconnect = button("DISCONNECT", red);
        connect.setOnClickListener(v -> { press(v); connectUsb(); });
        disconnect.setOnClickListener(v -> { press(v); closeUsb(); setUsbState("DISCONNECTED", false); });
        usbButtons.addView(connect, weightButton());
        usbButtons.addView(disconnect, weightButton());
        root.addView(usbButtons);
        spacer(root, 12);

        LinearLayout metrics1 = row();
        metrics1.addView(metric("SAMPLE RATE", "2.048 MS/s", panel, muted), weightCard());
        metrics1.addView(metric("GAIN", "AUTO", panel, muted), weightCard());
        root.addView(metrics1);
        LinearLayout metrics2 = row();
        metrics2.addView(metric("PPM", "0.5", panel, muted), weightCard());
        rfEngine = metric("RF ENGINE", "IDLE", panel, muted);
        metrics2.addView(rfEngine, weightCard());
        root.addView(metrics2);
        spacer(root, 16);

        root.addView(sectionTitle("SCAN RANGE"));
        LinearLayout range = row();
        range.addView(fieldBlock("START MHz", "500.000", panel, muted), weightCard());
        range.addView(fieldBlock("END MHz", "900.000", panel, muted), weightCard());
        root.addView(range);
        LinearLayout scanParams1 = row();
        scanParams1.addView(fieldBlock("STEP", "50 kHz", panel, muted), weightCard());
        scanParams1.addView(fieldBlock("GUARD", "150 kHz", panel, muted), weightCard());
        root.addView(scanParams1);
        LinearLayout scanParams2 = row();
        scanParams2.addView(fieldBlock("CHANNEL WIDTH", "200 kHz", panel, muted), weightCard());
        scanParams2.addView(fieldBlock("MIC COUNT", "8", panel, muted), weightCard());
        root.addView(scanParams2);

        Button autoScan = button("AUTO CLEAN SCAN", blue);
        autoScan.setOnClickListener(v -> {
            press(v);
            if (activeConnection == null) {
                setUsbState("CONNECT Well Connect USB FIRST", false);
                rfEngine.setText("RF ENGINE\nWAIT USB");
            } else {
                rfEngine.setText("RF ENGINE\nUSB READY");
                rfEngine.setTextColor(Color.rgb(74, 222, 128));
            }
        });
        LinearLayout.LayoutParams scanLp = new LinearLayout.LayoutParams(-1, dp(56));
        scanLp.setMargins(0, dp(8), 0, dp(14));
        autoScan.setLayoutParams(scanLp);
        root.addView(autoScan);

        root.addView(sectionTitle("SPECTRUM ANALYZER"));
        TextView spectrum = new TextView(this);
        spectrum.setText("500 MHz                                      900 MHz\n\n              SPECTRUM / WATERFALL\n\nRF data will appear here when RTL-SDR sampling engine is enabled");
        spectrum.setGravity(Gravity.CENTER);
        spectrum.setTextColor(muted);
        spectrum.setTextSize(13);
        spectrum.setBackgroundColor(Color.rgb(4, 8, 13));
        spectrum.setPadding(dp(12), dp(18), dp(12), dp(18));
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(-1, dp(180));
        spLp.setMargins(0, dp(4), 0, dp(10));
        spectrum.setLayoutParams(spLp);
        root.addView(spectrum);

        LinearLayout analyzer1 = row();
        analyzer1.addView(smallButton("LIVE"), weightButton());
        analyzer1.addView(smallButton("PEAK"), weightButton());
        analyzer1.addView(smallButton("AVERAGE"), weightButton());
        root.addView(analyzer1);
        LinearLayout analyzer2 = row();
        analyzer2.addView(smallButton("FREEZE"), weightButton());
        analyzer2.addView(smallButton("MARKER"), weightButton());
        analyzer2.addView(smallButton("SIGNAL ID"), weightButton());
        root.addView(analyzer2);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root);
        setContentView(scroll);
    }

    private void initUsb() {
        try {
            usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
            registerReceiverOnce();
            UsbDevice d = findRtl();
            if (d != null) {
                showDevice(d);
                setUsbState("RTL-SDR DETECTED", true);
            }
        } catch (Throwable t) {
            setUsbState("USB ERROR: " + t.getClass().getSimpleName(), false);
        }
    }

    private void connectUsb() {
        initUsb();
        UsbDevice d = findRtl();
        if (d == null) {
            setUsbState("RTL-SDR NOT FOUND", false);
            return;
        }
        requestOrOpen(d);
    }

    private void registerReceiverOnce() {
        if (receiverRegistered || usbManager == null) return;
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
        if (usbManager.hasPermission(d)) { openDevice(d); return; }
        Intent i = new Intent(ACTION_USB_PERMISSION).setPackage(getPackageName());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
        PendingIntent pi = PendingIntent.getBroadcast(this, 901, i, flags);
        setUsbState("WAITING USB PERMISSION", true);
        usbManager.requestPermission(d, pi);
    }

    private void openDevice(UsbDevice d) {
        try {
            closeUsb();
            UsbDeviceConnection c = usbManager.openDevice(d);
            if (c == null) { setUsbState("USB OPEN FAILED", false); return; }
            activeDevice = d;
            activeConnection = c;
            showDevice(d);
            setUsbState("CONNECTED", true);
            rfEngine.setText("RF ENGINE\nUSB READY");
            rfEngine.setTextColor(Color.rgb(74, 222, 128));
        } catch (Throwable t) {
            setUsbState("USB OPEN ERROR", false);
        }
    }

    private void showDevice(UsbDevice d) {
        if (deviceInfo == null || d == null) return;
        String p = d.getProductName();
        if (p == null || p.isEmpty()) p = "RTL2838 / RTL-SDR";
        deviceInfo.setText("RTL-SDR: " + p + "\nVID 0x" + Integer.toHexString(d.getVendorId()).toUpperCase() + "  PID 0x" + Integer.toHexString(d.getProductId()).toUpperCase());
    }

    @SuppressWarnings("deprecation")
    private UsbDevice getUsbDevice(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) return intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class);
        return (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
    }

    private void closeUsb() {
        if (activeConnection != null) try { activeConnection.close(); } catch (Throwable ignored) {}
        activeConnection = null;
        activeDevice = null;
        if (rfEngine != null) {
            rfEngine.setText("RF ENGINE\nIDLE");
            rfEngine.setTextColor(Color.WHITE);
        }
    }

    private void setUsbState(String s, boolean ok) {
        if (usbState == null) return;
        usbState.setText(s);
        usbState.setTextColor(ok ? Color.rgb(74, 222, 128) : Color.rgb(248, 113, 113));
    }

    private TextView sectionTitle(String s) {
        TextView t = text(s, 17, Color.WHITE, true);
        t.setPadding(0, dp(5), 0, dp(8));
        return t;
    }

    private TextView card(String s, int panel, int line) {
        TextView t = text(s, 15, Color.WHITE, false);
        t.setPadding(dp(14), dp(13), dp(14), dp(13));
        t.setBackgroundColor(panel);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(7));
        t.setLayoutParams(lp);
        return t;
    }

    private TextView metric(String label, String value, int panel, int muted) {
        TextView t = text(label + "\n" + value, 14, Color.WHITE, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(8), dp(11), dp(8), dp(11));
        t.setBackgroundColor(panel);
        return t;
    }

    private LinearLayout fieldBlock(String label, String value, int panel, int muted) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(8), dp(10), dp(8));
        box.setBackgroundColor(panel);
        TextView l = text(label, 11, muted, false);
        EditText e = new EditText(this);
        e.setText(value);
        e.setTextColor(Color.WHITE);
        e.setTextSize(15);
        e.setSingleLine(true);
        e.setBackgroundColor(Color.TRANSPARENT);
        e.setPadding(0, dp(2), 0, 0);
        box.addView(l);
        box.addView(e);
        return box;
    }

    private Button button(String s, int color) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setBackgroundColor(color);
        return b;
    }

    private Button smallButton(String s) {
        Button b = button(s, Color.rgb(30, 41, 59));
        b.setTextSize(11);
        return b;
    }

    private TextView text(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    private LinearLayout.LayoutParams weightButton() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1f);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        return lp;
    }

    private LinearLayout.LayoutParams weightCard() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        return lp;
    }

    private void spacer(LinearLayout root, int h) {
        View v = new View(this);
        root.addView(v, new LinearLayout.LayoutParams(1, dp(h)));
    }

    private void press(View v) {
        v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(60).withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(80).start()).start();
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onDestroy() {
        closeUsb();
        if (receiverRegistered) try { unregisterReceiver(permissionReceiver); } catch (Throwable ignored) {}
        super.onDestroy();
    }
}
