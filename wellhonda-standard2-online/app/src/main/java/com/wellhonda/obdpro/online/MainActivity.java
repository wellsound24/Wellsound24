package com.wellhonda.obdpro.online;

import android.app.*;
import android.os.*;
import android.content.*;
import android.hardware.usb.*;
import android.net.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private static final String ACTION_USB_PERMISSION = "com.wellhonda.obdpro.online.USB_PERMISSION";

    private UsbManager usbManager;
    private UsbDevice activeDevice;
    private UsbDeviceConnection usbConnection;
    private UsbInterface usbInterface;
    private TextView connectionStatus;
    private TextView onlineStatus;
    private LinearLayout liveFrame;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(usbReceiver, filter);
        }

        setContentView(buildUi());
        refreshOnlineState();
        scanUsb();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(8), dp(10), dp(8));
        root.setBackgroundColor(Color.rgb(5,5,5));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("WELL HONDA  OBD PRO   •   STANDARD 2 ONLINE", 18, Color.WHITE, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1));

        onlineStatus = text("ONLINE: --", 11, Color.LTGRAY, true);
        onlineStatus.setGravity(Gravity.CENTER);
        header.addView(onlineStatus, new LinearLayout.LayoutParams(dp(120), dp(42)));

        connectionStatus = text("USB: --", 11, Color.LTGRAY, true);
        connectionStatus.setGravity(Gravity.CENTER);
        header.addView(connectionStatus, new LinearLayout.LayoutParams(dp(220), dp(42)));

        root.addView(header, new LinearLayout.LayoutParams(-1, dp(44)));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(0, dp(4), 0, dp(4));

        Button connect = button("CONNECT");
        connect.setOnClickListener(v -> connectUsb());
        controls.addView(connect, new LinearLayout.LayoutParams(0, dp(46), 1));

        Space gap1 = new Space(this);
        controls.addView(gap1, new LinearLayout.LayoutParams(dp(8), 1));

        Button realtime = button("REAL TIME");
        realtime.setOnClickListener(v -> showLiveData());
        controls.addView(realtime, new LinearLayout.LayoutParams(0, dp(46), 1));

        Space gap2 = new Space(this);
        controls.addView(gap2, new LinearLayout.LayoutParams(dp(8), 1));

        Button scan = button("SCAN USB / OTG");
        scan.setOnClickListener(v -> scanUsb());
        controls.addView(scan, new LinearLayout.LayoutParams(0, dp(46), 1));

        root.addView(controls, new LinearLayout.LayoutParams(-1, dp(54)));

        liveFrame = panel();
        liveFrame.setOrientation(LinearLayout.VERTICAL);
        liveFrame.setPadding(dp(8), dp(8), dp(8), dp(8));

        LinearLayout top = row();
        top.addView(metric("RPM", "1,650", "RPM", Color.rgb(235,20,28)), weight());
        top.addView(metric("SPEED", "0", "km/h", Color.WHITE), weight());
        top.addView(metric("THROTTLE", "9.0", "%", Color.rgb(235,20,28)), weight());
        top.addView(metric("BATTERY", "12.6", "V", Color.WHITE), weight());
        liveFrame.addView(top, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout mid = row();
        mid.addView(metric("COOLANT TEMP", "37", "°C", Color.rgb(235,20,28)), weight());
        mid.addView(metric("INTAKE AIR TEMP", "42", "°C", Color.rgb(235,20,28)), weight());
        mid.addView(metric("AFR", "14.7", ":1", Color.rgb(50,215,102)), weight());
        mid.addView(metric("O2 SENSOR", "0.85", "V", Color.rgb(50,215,102)), weight());
        liveFrame.addView(mid, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bottom = row();
        bottom.addView(metric("ENGINE LOAD", "18", "%", Color.rgb(255,170,20)), weight());
        bottom.addView(metric("IGNITION ADVANCE", "12", "°", Color.rgb(255,170,20)), weight());
        bottom.addView(metric("SHORT FUEL TRIM", "+1.6", "%", Color.WHITE), weight());
        bottom.addView(metric("LONG FUEL TRIM", "-0.8", "%", Color.WHITE), weight());
        liveFrame.addView(bottom, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView note = text("ECU STATUS: READY     •     MIL: OFF     •     DTC: --     •     DATA RATE: -- Hz", 11, Color.LTGRAY, false);
        note.setGravity(Gravity.CENTER);
        liveFrame.addView(note, new LinearLayout.LayoutParams(-1, dp(30)));

        root.addView(liveFrame, new LinearLayout.LayoutParams(-1, 0, 1));
        return root;
    }

    private void refreshOnlineState() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean online = false;
        if (cm != null) {
            NetworkInfo info = cm.getActiveNetworkInfo();
            online = info != null && info.isConnected();
        }
        onlineStatus.setText(online ? "ONLINE: READY" : "ONLINE: OFFLINE");
        onlineStatus.setTextColor(online ? Color.rgb(50,215,102) : Color.rgb(235,20,28));
    }

    private void scanUsb() {
        HashMap<String, UsbDevice> devices = usbManager.getDeviceList();
        activeDevice = devices.isEmpty() ? null : devices.values().iterator().next();
        if (activeDevice == null) {
            connectionStatus.setText("USB: NO DEVICE");
            connectionStatus.setTextColor(Color.LTGRAY);
            return;
        }
        connectionStatus.setText("USB: VID " + hex(activeDevice.getVendorId()) + "  PID " + hex(activeDevice.getProductId()));
        connectionStatus.setTextColor(Color.rgb(255,170,20));
    }

    private void connectUsb() {
        refreshOnlineState();
        scanUsb();
        if (activeDevice == null) {
            toast("ไม่พบอุปกรณ์ USB / OTG");
            return;
        }
        if (!usbManager.hasPermission(activeDevice)) {
            PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0,
                    new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
            usbManager.requestPermission(activeDevice, permissionIntent);
            return;
        }
        openUsb(activeDevice);
    }

    private void openUsb(UsbDevice device) {
        closeUsb();
        if (device.getInterfaceCount() == 0) {
            connectionStatus.setText("USB: NO INTERFACE");
            return;
        }
        usbInterface = device.getInterface(0);
        usbConnection = usbManager.openDevice(device);
        if (usbConnection != null && usbConnection.claimInterface(usbInterface, true)) {
            activeDevice = device;
            connectionStatus.setText("CONNECTED  VID " + hex(device.getVendorId()) + "  PID " + hex(device.getProductId()));
            connectionStatus.setTextColor(Color.rgb(50,215,102));
            toast("เชื่อมต่อ USB สำเร็จ");
        } else {
            connectionStatus.setText("USB: CONNECT FAILED");
            connectionStatus.setTextColor(Color.rgb(235,20,28));
            closeUsb();
        }
    }

    private void showLiveData() {
        refreshOnlineState();
        if (usbConnection == null) {
            toast("กด CONNECT ก่อน");
            return;
        }
        toast("REAL TIME พร้อมรับข้อมูลจาก ECU เมื่อกำหนดโปรโตคอลของสายแล้ว");
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!ACTION_USB_PERMISSION.equals(intent.getAction())) return;
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
            if (granted && device != null) openUsb(device);
            else toast("ไม่ได้รับสิทธิ์ใช้งาน USB");
        }
    };

    private void closeUsb() {
        if (usbConnection != null) {
            try {
                if (usbInterface != null) usbConnection.releaseInterface(usbInterface);
            } catch (Exception ignored) {}
            usbConnection.close();
        }
        usbConnection = null;
        usbInterface = null;
    }

    @Override
    protected void onDestroy() {
        closeUsb();
        try { unregisterReceiver(usbReceiver); } catch (Exception ignored) {}
        super.onDestroy();
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(3), 0, dp(3));
        return row;
    }

    private LinearLayout panel() {
        LinearLayout p = new LinearLayout(this);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.rgb(10,10,10));
        gd.setStroke(dp(1), Color.rgb(55,55,55));
        p.setBackground(gd);
        return p;
    }

    private View metric(String label, String value, String unit, int accent) {
        LinearLayout card = panel();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(4), dp(4), dp(4), dp(4));

        TextView a = text(label, 10, accent, true);
        a.setGravity(Gravity.CENTER);
        card.addView(a, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView v = text(value, 24, Color.WHITE, true);
        v.setGravity(Gravity.CENTER);
        card.addView(v, new LinearLayout.LayoutParams(-1, 0, 2));

        TextView u = text(unit, 10, Color.LTGRAY, false);
        u.setGravity(Gravity.CENTER);
        card.addView(u, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(dp(3), 0, dp(3), 0);
        wrapper.addView(card, new LinearLayout.LayoutParams(-1, -1));
        return wrapper;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, -1, 1);
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.rgb(125,0,6));
        gd.setStroke(dp(1), Color.rgb(235,20,28));
        b.setBackground(gd);
        return b;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return t;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private String hex(int v) {
        return String.format(Locale.US, "%04X", v);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
