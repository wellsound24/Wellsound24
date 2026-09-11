package com.wellsound.autofeedback;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.widget.*;
import android.view.View;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private EditText ip;
    private TextView status, log;
    private DatagramSocket socket;
    private volatile boolean running = false;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            buildUi();
        } catch (Throwable t) {
            TextView v = new TextView(this);
            v.setText("Well Auto Feedback M32R v1.3\n\nStartup error:\n" + t);
            v.setTextColor(Color.WHITE);
            v.setBackgroundColor(Color.rgb(8,11,16));
            v.setPadding(30,30,30,30);
            setContentView(v);
        }
    }

    private void buildUi() {
        ScrollView sc = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28,28,28,28);
        root.setBackgroundColor(Color.rgb(8,11,16));
        sc.addView(root);

        TextView title = tv("Well Auto Feedback • M32R", 24, true);
        root.addView(title);
        TextView ver = tv("Android v1.3 • Offline LAN", 13, false);
        ver.setTextColor(Color.LTGRAY);
        root.addView(ver);

        status = tv("READY — NOT CONNECTED", 16, true);
        status.setTextColor(Color.rgb(251,191,36));
        root.addView(status);

        ip = new EditText(this);
        ip.setSingleLine(true);
        ip.setText("192.168.1.100");
        ip.setTextColor(Color.WHITE);
        ip.setHintTextColor(Color.GRAY);
        ip.setHint("M32R IP");
        root.addView(ip, lp());

        Button connect = new Button(this);
        connect.setText("CONNECT M32R");
        root.addView(connect, lp());

        Spinner target = new Spinner(this);
        String[] targets = new String[17];
        targets[0] = "Main LR";
        for (int i=1;i<=16;i++) targets[i] = String.format("Bus %02d", i);
        target.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, targets));
        root.addView(target, lp());

        Spinner mode = new Spinner(this);
        mode.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"MONITOR","ASSIST","AUTO","RING OUT"}));
        mode.setSelection(1);
        root.addView(mode, lp());

        CheckBox arm = new CheckBox(this);
        arm.setText("ARM AUTO CUT (ยังล็อกไว้ในรุ่นทดสอบการเชื่อมต่อ)");
        arm.setTextColor(Color.WHITE);
        arm.setEnabled(false);
        root.addView(arm, lp());

        TextView note = tv("รุ่นนี้ทำให้ติดตั้ง/เปิดแอปและทดสอบการเชื่อม M32R ก่อน โดยยังไม่แก้ EQ อัตโนมัติจนกว่าจะยืนยันว่าเครื่องของคุณเปิดและเชื่อมได้ปกติ", 14, false);
        note.setTextColor(Color.rgb(148,163,184));
        root.addView(note, lp());

        log = tv("App started successfully.", 13, false);
        log.setTextColor(Color.LTGRAY);
        root.addView(log, lp());

        setContentView(sc);
        connect.setOnClickListener(v -> connectMixer());
    }

    private TextView tv(String s, int size, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s); v.setTextColor(Color.WHITE); v.setTextSize(size);
        v.setPadding(0,10,0,10);
        if (bold) v.setTypeface(null, 1);
        return v;
    }

    private LinearLayout.LayoutParams lp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,8,0,8); return p;
    }

    private void connectMixer() {
        final String host = ip.getText().toString().trim();
        if (host.length()==0) { Toast.makeText(this,"ใส่ IP M32R ก่อน",Toast.LENGTH_SHORT).show(); return; }
        stopSocket();
        status.setText("CONNECTING...");
        status.setTextColor(Color.rgb(251,191,36));
        new Thread(() -> {
            try {
                socket = new DatagramSocket();
                socket.setSoTimeout(1800);
                running = true;
                byte[] m = oscNoArgs("/info");
                InetAddress addr = InetAddress.getByName(host);
                socket.send(new DatagramPacket(m,m.length,addr,10023));
                byte[] buf = new byte[4096];
                DatagramPacket r = new DatagramPacket(buf,buf.length);
                socket.receive(r);
                runOnUiThread(() -> {
                    status.setText("M32R CONNECTED");
                    status.setTextColor(Color.rgb(52,211,153));
                    log.setText("OSC reply received from " + host + ":10023\nมือถือกับ M32R อยู่ใน LAN เดียวกันและคุยกันได้แล้ว");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("NO RESPONSE");
                    status.setTextColor(Color.rgb(248,113,113));
                    log.setText("ยังไม่ได้รับคำตอบจาก M32R\nตรวจ IP และ Wi-Fi/Router ว่าอยู่วงเดียวกัน\n\n" + e.getClass().getSimpleName() + ": " + e.getMessage());
                });
            }
        }, "m32-connect-test").start();
    }

    private byte[] oscNoArgs(String address) {
        byte[] a = pad(address);
        byte[] t = pad(",");
        byte[] out = new byte[a.length+t.length];
        System.arraycopy(a,0,out,0,a.length);
        System.arraycopy(t,0,out,a.length,t.length);
        return out;
    }

    private byte[] pad(String s) {
        byte[] raw = (s + "\0").getBytes(StandardCharsets.UTF_8);
        int n = ((raw.length + 3) / 4) * 4;
        byte[] out = new byte[n];
        System.arraycopy(raw,0,out,0,raw.length);
        return out;
    }

    private void stopSocket() {
        running = false;
        if (socket != null) { try { socket.close(); } catch (Exception ignored) {} socket = null; }
    }

    @Override protected void onDestroy() { stopSocket(); super.onDestroy(); }
}
