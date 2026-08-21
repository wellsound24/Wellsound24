package com.well.rfscanner.v7;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(40, 40, 40, 40);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("WELL RF SCANNER PRO");
        title.setTextColor(Color.BLACK);
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);

        TextView status = new TextView(this);
        status.setText("V7 CLEAN BASE\nAPP START OK");
        status.setTextColor(Color.rgb(0, 128, 0));
        status.setTextSize(20);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 24, 0, 0);

        root.addView(title);
        root.addView(status);
        setContentView(root);
    }
}
