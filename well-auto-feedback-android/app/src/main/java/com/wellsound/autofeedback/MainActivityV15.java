package com.wellsound.autofeedback;

import android.os.Bundle;

/**
 * v1.5: keeps the M32R RTA meter subscription alive aggressively.
 * v1.4 proved OSC connectivity and RTA decoding on the user's M32R.
 * This class only strengthens RTA subscription recovery; the EQ logic
 * remains inherited unchanged from MainActivity.
 */
public class MainActivityV15 extends MainActivity {

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        uiLog("v1.5 • RTA Auto-Reconnect enabled");
    }

    @Override void keepAlive() {
        long lastSubscribe = 0;
        while (running) {
            try {
                final long now = System.currentTimeMillis();

                // Keep normal M32 parameter feedback alive.
                sendNow("/xremote");

                // M32 /meters subscriptions expire. Refresh normally every 3 s.
                // If RTA frames stop for > 1.8 s, recover more aggressively,
                // but rate-limit retry traffic to one request per 1.2 s.
                final boolean rtaStale = (lastRta == 0) || (now - lastRta > 1800);
                final boolean normalRefresh = now - lastSubscribe > 3000;
                final boolean staleRefresh = rtaStale && (now - lastSubscribe > 1200);

                if (normalRefresh || staleRefresh) {
                    configureNow();
                    sendNow("/meters", "/meters/15", 1);
                    lastSubscribe = now;

                    if (rtaStale && rtaState != null) {
                        runOnUiThread(() -> {
                            long age = lastRta == 0 ? -1 : System.currentTimeMillis() - lastRta;
                            if (age < 0) rtaState.setText("RTA: STARTING… • " + String.valueOf(target.getSelectedItem()));
                            else if (age > 1800) rtaState.setText("RTA: AUTO-RECONNECT… • " + String.valueOf(target.getSelectedItem()));
                        });
                    }
                }

                Thread.sleep(500);
            } catch (Exception e) {
                if (running) uiLog("RTA keepalive: " + e.getMessage());
                try { Thread.sleep(700); } catch (Exception ignored) {}
            }
        }
    }
}
