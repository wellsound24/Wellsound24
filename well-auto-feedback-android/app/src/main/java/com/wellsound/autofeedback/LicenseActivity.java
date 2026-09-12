package com.wellsound.autofeedback;

import android.app.*;
import android.os.*;
import android.provider.Settings;
import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import org.json.*;

public class LicenseActivity extends Activity {
    static final String VERIFY_URL = "https://sjcxywxixgrpdgeqaepk.supabase.co/functions/v1/verify-license";
    static final String PRODUCT = "WELL_AUTO_EQ_PRO";
    static final long OFFLINE_GRACE_MS = 72L * 60L * 60L * 1000L;

    EditText emailInput, keyInput;
    TextView statusText, deviceText, expiryText;
    Button activateBtn, clearBtn;
    SharedPreferences prefs;
    volatile boolean checking = false;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("well_auto_eq_license", MODE_PRIVATE);
        buildUi();
        String email = prefs.getString("email", "");
        String key = prefs.getString("license_key", "");
        emailInput.setText(email);
        keyInput.setText(key);
        deviceText.setText("DEVICE ID: " + shortDeviceId());
        if (!email.isEmpty() && !key.isEmpty()) verify(email, key, true);
    }

    void buildUi() {
        ScrollView sc = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 40, 32, 40);
        root.setBackgroundColor(Color.rgb(8, 11, 16));
        sc.addView(root);

        TextView title = text("Well Auto Feedback • M32R", 25, true);
        root.addView(title);
        TextView sub = text("LICENSE ACTIVATION • Well License Dashboard", 13, false);
        sub.setTextColor(Color.LTGRAY);
        root.addView(sub);

        statusText = text("กำลังตรวจสอบ License...", 16, true);
        statusText.setTextColor(Color.rgb(251, 191, 36));
        statusText.setPadding(0, 24, 0, 16);
        root.addView(statusText);

        root.addView(label("ACTIVATION EMAIL"));
        emailInput = new EditText(this);
        emailInput.setSingleLine(true);
        emailInput.setHint("อีเมลที่ลงทะเบียนใน Well License Dashboard");
        emailInput.setTextColor(Color.WHITE);
        emailInput.setHintTextColor(Color.GRAY);
        emailInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        root.addView(emailInput, lp());

        root.addView(label("LICENSE KEY"));
        keyInput = new EditText(this);
        keyInput.setSingleLine(true);
        keyInput.setHint("วาง License Key");
        keyInput.setTextColor(Color.WHITE);
        keyInput.setHintTextColor(Color.GRAY);
        root.addView(keyInput, lp());

        activateBtn = new Button(this);
        activateBtn.setText("VERIFY & ACTIVATE");
        root.addView(activateBtn, lp());

        clearBtn = new Button(this);
        clearBtn.setText("CHANGE / CLEAR LICENSE");
        root.addView(clearBtn, lp());

        deviceText = text("DEVICE ID: -", 12, false);
        deviceText.setTextColor(Color.GRAY);
        root.addView(deviceText);
        expiryText = text("EXPIRES: -", 13, false);
        expiryText.setTextColor(Color.LTGRAY);
        root.addView(expiryText);

        TextView note = text("License นี้ผูกกับอุปกรณ์เครื่องแรกที่ Activate • หากเปลี่ยนเครื่อง ให้ Reset Device จาก Well License Dashboard", 12, false);
        note.setTextColor(Color.GRAY);
        note.setPadding(0, 18, 0, 0);
        root.addView(note);

        setContentView(sc);
        activateBtn.setOnClickListener(v -> verify(emailInput.getText().toString().trim().toLowerCase(Locale.US), keyInput.getText().toString().trim(), false));
        clearBtn.setOnClickListener(v -> clearLicense());
    }

    TextView text(String s, int size, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s); v.setTextColor(Color.WHITE); v.setTextSize(size);
        if (bold) v.setTypeface(null, 1);
        return v;
    }
    TextView label(String s) { TextView v=text(s,12,true); v.setTextColor(Color.rgb(148,163,184)); return v; }
    LinearLayout.LayoutParams lp(){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,8,0,12); return p; }

    void clearLicense() {
        prefs.edit().clear().apply();
        emailInput.setText(""); keyInput.setText("");
        expiryText.setText("EXPIRES: -");
        setStatus("กรอก Email และ License Key ใหม่", false);
    }

    void verify(String email, String key, boolean automatic) {
        if (checking) return;
        if (email.isEmpty() || key.isEmpty()) {
            setStatus("กรุณากรอก Email และ License Key", false);
            return;
        }
        checking = true;
        activateBtn.setEnabled(false);
        setStatus(automatic ? "กำลังตรวจสอบ License อัตโนมัติ..." : "กำลังตรวจสอบ License...", null);
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("product", PRODUCT);
                body.put("license_key", key);
                body.put("email", email);
                body.put("device_id", deviceId());
                HttpURLConnection c = (HttpURLConnection) new URL(VERIFY_URL).openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(6500); c.setReadTimeout(6500);
                c.setRequestProperty("Content-Type", "application/json");
                c.setDoOutput(true);
                byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
                try(OutputStream os=c.getOutputStream()){ os.write(data); }
                int code = c.getResponseCode();
                InputStream in = code >= 200 && code < 400 ? c.getInputStream() : c.getErrorStream();
                String response = readAll(in);
                JSONObject j = new JSONObject(response);
                boolean allowed = j.optBoolean("allowed", false);
                String reason = j.optString("reason", "server_error");
                String message = j.optString("message", reason);
                String expiry = j.optString("expires_at", "");
                int days = j.optInt("days_remaining", -9999);
                if (allowed) {
                    long now = System.currentTimeMillis();
                    prefs.edit().putString("email", email).putString("license_key", key).putString("expires_at", expiry).putLong("last_verified", now).apply();
                    runOnUiThread(() -> {
                        checking=false; activateBtn.setEnabled(true);
                        setStatus(days>=0 && days<=7 ? "LICENSE ACTIVE • ใกล้หมดอายุ" : "LICENSE ACTIVE", true);
                        expiryText.setText("EXPIRES: "+(expiry.isEmpty()?"-":expiry)+(days>=0?" • เหลือ "+days+" วัน":""));
                        new Handler(getMainLooper()).postDelayed(this::openMain, 450);
                    });
                } else {
                    runOnUiThread(() -> {
                        checking=false; activateBtn.setEnabled(true);
                        setStatus(messageFor(reason, message), false);
                        expiryText.setText("EXPIRES: "+(expiry.isEmpty()?"-":expiry));
                    });
                }
            } catch (Exception e) {
                boolean offlineOk = cachedOfflineAllowed(email, key);
                runOnUiThread(() -> {
                    checking=false; activateBtn.setEnabled(true);
                    if (offlineOk) {
                        setStatus("OFFLINE LICENSE CACHE • ใช้งานได้ชั่วคราว", true);
                        expiryText.setText("EXPIRES: "+prefs.getString("expires_at","-")+" • จะตรวจออนไลน์อีกครั้งเมื่อมีอินเทอร์เน็ต");
                        new Handler(getMainLooper()).postDelayed(this::openMain, 450);
                    } else {
                        setStatus("ตรวจ License ไม่สำเร็จ • กรุณาต่ออินเทอร์เน็ตแล้วลองใหม่", false);
                    }
                });
            }
        }).start();
    }

    boolean cachedOfflineAllowed(String email, String key) {
        if (!email.equals(prefs.getString("email","")) || !key.equals(prefs.getString("license_key",""))) return false;
        long last = prefs.getLong("last_verified", 0L);
        if (last <= 0 || System.currentTimeMillis() - last > OFFLINE_GRACE_MS) return false;
        String exp = prefs.getString("expires_at", "");
        if (exp.isEmpty()) return false;
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US); f.setLenient(false);
            Date d = f.parse(exp); Date today = f.parse(f.format(new Date()));
            return d != null && today != null && !d.before(today);
        } catch(Exception e) { return false; }
    }

    String messageFor(String reason, String serverMessage) {
        if ("license_not_found".equals(reason)) return "ไม่พบ License หรือ License Key ไม่ถูกต้อง";
        if ("email_mismatch".equals(reason)) return "Email ไม่ตรงกับ License";
        if ("disabled".equals(reason)) return "License ถูกปิดใช้งาน";
        if ("expired".equals(reason)) return "License หมดอายุ";
        if ("device_mismatch".equals(reason)) return "License ถูกผูกกับอุปกรณ์อื่น • Reset Device จาก Dashboard ก่อน";
        if ("bind_failed".equals(reason)) return "ผูก License กับอุปกรณ์ไม่สำเร็จ";
        if ("invalid_request".equals(reason)) return "ข้อมูล License ไม่ครบ";
        return serverMessage == null || serverMessage.isEmpty() ? "License ใช้งานไม่ได้" : serverMessage;
    }

    void openMain() {
        Intent i = new Intent(this, MainActivity.class);
        startActivity(i);
        finish();
    }

    void setStatus(String s, Boolean good) {
        statusText.setText(s);
        statusText.setTextColor(good==null ? Color.rgb(251,191,36) : good ? Color.rgb(52,211,153) : Color.rgb(248,113,113));
    }

    String deviceId() {
        String raw = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        if (raw == null) raw = "unknown";
        return sha256("WELL_AUTO_EQ_PRO|" + raw + "|" + getPackageName());
    }
    String shortDeviceId() { String d=deviceId(); return d.length()>16?d.substring(0,16).toUpperCase(Locale.US):d; }
    String sha256(String s) {
        try { MessageDigest md=MessageDigest.getInstance("SHA-256"); byte[] x=md.digest(s.getBytes(StandardCharsets.UTF_8)); StringBuilder b=new StringBuilder(); for(byte v:x)b.append(String.format(Locale.US,"%02x",v)); return b.toString(); }
        catch(Exception e){ return Integer.toHexString(s.hashCode()); }
    }
    String readAll(InputStream in) throws IOException {
        if (in == null) return "{}";
        ByteArrayOutputStream o=new ByteArrayOutputStream(); byte[] b=new byte[2048]; int n;
        while((n=in.read(b))>0)o.write(b,0,n);
        return o.toString("UTF-8");
    }
}
