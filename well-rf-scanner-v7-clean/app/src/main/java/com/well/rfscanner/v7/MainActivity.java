package com.well.rfscanner.v7;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.*;
import android.graphics.*;
import android.hardware.usb.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.HashMap;

public class MainActivity extends Activity {
    private static final String ACTION_USB_PERMISSION="com.well.rfscanner.v7stable.USB_PERMISSION";
    private static final int REALTEK_VID=0x0BDA, RTL2838_PID=0x2838;
    private UsbManager usbManager; private UsbDeviceConnection activeConnection; private boolean receiverRegistered=false;
    private TextView usbState, deviceInfo;

    private final BroadcastReceiver permissionReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c, Intent i){
            if(!ACTION_USB_PERMISSION.equals(i.getAction())) return;
            UsbDevice d=getUsbDevice(i);
            if(i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false)&&d!=null) openDevice(d);
            else setUsbState("ไม่อนุญาต USB",false);
        }
    };

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        try{ buildUi(); }catch(Throwable t){
            TextView e=new TextView(this); e.setText("WELL RF SCANNER PRO\nUI ERROR: "+t.getClass().getSimpleName());
            e.setTextColor(Color.WHITE); e.setTextSize(18); e.setGravity(Gravity.CENTER); e.setBackgroundColor(Color.rgb(5,12,18)); setContentView(e);
        }
    }

    private void buildUi(){
        int bg=Color.rgb(4,13,19), panel=Color.rgb(12,25,34), field=Color.rgb(7,18,25), muted=Color.rgb(139,156,168), cyan=Color.rgb(26,211,223), green=Color.rgb(38,227,131);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(12),dp(14),dp(12),dp(18)); root.setBackgroundColor(bg);
        LinearLayout top=row(); LinearLayout ttl=row(); ttl.addView(text("WELL ",15,Color.WHITE,true)); ttl.addView(text("RF SCANNER PRO",15,cyan,true)); top.addView(ttl,new LinearLayout.LayoutParams(0,dp(48),1));
        Button install=button("ติดตั้งแอป",cyan,Color.rgb(1,32,38)); install.setTextSize(12); top.addView(install,new LinearLayout.LayoutParams(dp(94),dp(42))); root.addView(top);

        LinearLayout usbCard=box(panel); usbState=text("Well Connect USB",14,Color.WHITE,true); usbCard.addView(usbState);
        deviceInfo=text("สถานะ: OFFLINE",11,muted,false); deviceInfo.setPadding(0,dp(4),0,dp(8)); usbCard.addView(deviceInfo);
        LinearLayout ub=row(); Button detect=button("ตรวจหา USB",Color.rgb(19,37,48),Color.WHITE); Button demo=button("โหมดทดลอง",Color.rgb(19,37,48),Color.WHITE);
        detect.setOnClickListener(v->{press(v);connectUsb();}); demo.setOnClickListener(v->{press(v);closeUsb();setUsbState("โหมดทดลอง",true);}); ub.addView(detect,weight(0,46)); ub.addView(demo,weight(0,46)); usbCard.addView(ub); root.addView(usbCard);

        spacer(root,9); LinearLayout range=box(panel); range.addView(text("ช่วงความถี่",15,Color.WHITE,true));
        LinearLayout r1=row(); r1.addView(fieldBox("เริ่มต้น MHz","500.000",field,muted),weight(0,74)); r1.addView(fieldBox("สิ้นสุด MHz","900.000",field,muted),weight(0,74)); range.addView(r1);
        LinearLayout r2=row(); r2.addView(fieldBox("Step","50 kHz",field,muted),weight(0,68)); r2.addView(fieldBox("Guard Band","150 kHz",field,muted),weight(0,68)); range.addView(r2);
        LinearLayout r3=row(); r3.addView(fieldBox("Channel Width","200 kHz",field,muted),weight(0,68)); r3.addView(fieldBox("จำนวนไมค์","8",field,muted),weight(0,68)); range.addView(r3);
        TextView span=text("ช่วงค้นหารวม 400.000 MHz",11,muted,false); span.setPadding(dp(4),dp(4),0,dp(6)); range.addView(span);
        Button scan=button("AUTO CLEAN SCAN",cyan,Color.rgb(0,35,40)); LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,dp(48)); slp.setMargins(dp(4),0,dp(4),dp(4)); range.addView(scan,slp); root.addView(range);

        spacer(root,9); LinearLayout specBox=box(panel); LinearLayout sh=row(); sh.addView(text("Spectrum Analyzer",15,Color.WHITE,true),new LinearLayout.LayoutParams(0,dp(38),1)); TextView liveTag=text("LIVE",10,green,true); liveTag.setGravity(Gravity.CENTER); sh.addView(liveTag,new LinearLayout.LayoutParams(dp(48),dp(34))); specBox.addView(sh);
        LinearLayout modes=row(); for(String s:new String[]{"LIVE","PEAK HOLD","AVERAGE","FREEZE","CLEAR MARK"}){Button b=button(s,s.equals("LIVE")?green:Color.rgb(20,34,43),s.equals("LIVE")?Color.rgb(0,35,25):muted);b.setTextSize(8);modes.addView(b,new LinearLayout.LayoutParams(0,dp(34),1));} specBox.addView(modes);
        SpectrumView sv=new SpectrumView(this); specBox.addView(sv,new LinearLayout.LayoutParams(-1,dp(180))); TextView hint=text("แตะตำแหน่งสัญญาณซ้าย–ขวา เพื่ออ่าน MHz, dBFS และระดับสัญญาณ",10,muted,false); hint.setPadding(dp(3),dp(5),dp(3),dp(5)); specBox.addView(hint); root.addView(specBox);

        spacer(root,9); LinearLayout wf=box(panel); wf.addView(text("Waterfall",15,Color.WHITE,true)); wf.addView(new WaterfallView(this),new LinearLayout.LayoutParams(-1,dp(90))); root.addView(wf);
        spacer(root,9); LinearLayout sid=box(panel); LinearLayout ih=row(); ih.addView(text("Signal Identification",15,Color.WHITE,true),new LinearLayout.LayoutParams(0,dp(36),1)); ih.addView(text("Estimated RF Type",10,muted,false)); sid.addView(ih); sid.addView(text("Wireless Mic Candidate",17,Color.WHITE,true)); LinearLayout info=row(); info.addView(fieldLabel("Frequency","500.000 MHz",field,muted),weight(0,64)); info.addView(fieldLabel("Level","-97.0 dBFS",field,muted),weight(0,64)); sid.addView(info); root.addView(sid);

        spacer(root,8); LinearLayout nav=row(); for(String s:new String[]{"SCAN","COORDINATE","MONITOR","PROJECT"}){TextView t=text(s,9,s.equals("SCAN")?cyan:muted,s.equals("SCAN"));t.setGravity(Gravity.CENTER);nav.addView(t,new LinearLayout.LayoutParams(0,dp(42),1));} root.addView(nav);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.addView(root); setContentView(scroll);
    }

    private void connectUsb(){try{usbManager=(UsbManager)getSystemService(Context.USB_SERVICE);if(usbManager==null){setUsbState("USB HOST ไม่พร้อม",false);return;}registerReceiverOnce();UsbDevice d=findRtl();if(d==null){setUsbState("ไม่พบ RTL-SDR",false);return;}deviceInfo.setText("RTL-SDR: "+(d.getProductName()==null?"RTL2838":d.getProductName())+"  VID 0x"+Integer.toHexString(d.getVendorId()).toUpperCase()+" PID 0x"+Integer.toHexString(d.getProductId()).toUpperCase());if(usbManager.hasPermission(d))openDevice(d);else{Intent i=new Intent(ACTION_USB_PERMISSION).setPackage(getPackageName());int f=PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0);usbManager.requestPermission(d,PendingIntent.getBroadcast(this,77,i,f));setUsbState("รอสิทธิ์ USB",true);}}catch(Throwable t){setUsbState("USB ERROR",false);}}
    private void registerReceiverOnce(){if(receiverRegistered)return;IntentFilter f=new IntentFilter(ACTION_USB_PERMISSION);if(Build.VERSION.SDK_INT>=33)registerReceiver(permissionReceiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(permissionReceiver,f);receiverRegistered=true;}
    private UsbDevice findRtl(){HashMap<String,UsbDevice> m=usbManager.getDeviceList();for(UsbDevice d:m.values())if(d.getVendorId()==REALTEK_VID||d.getProductId()==RTL2838_PID)return d;return null;}
    private void openDevice(UsbDevice d){closeUsb();activeConnection=usbManager.openDevice(d);setUsbState(activeConnection!=null?"เชื่อมต่อ USB แล้ว":"เปิด USB ไม่สำเร็จ",activeConnection!=null);}
    private void closeUsb(){if(activeConnection!=null)try{activeConnection.close();}catch(Throwable ignored){}activeConnection=null;}
    private void setUsbState(String s,boolean ok){if(usbState!=null)usbState.setText("Well Connect USB  •  "+s);if(deviceInfo!=null&&ok&&s.contains("เชื่อม"))deviceInfo.setTextColor(Color.rgb(38,227,131));}
    @SuppressWarnings("deprecation") private UsbDevice getUsbDevice(Intent i){return Build.VERSION.SDK_INT>=33?i.getParcelableExtra(UsbManager.EXTRA_DEVICE,UsbDevice.class):(UsbDevice)i.getParcelableExtra(UsbManager.EXTRA_DEVICE);}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout box(int c){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(10),dp(10),dp(10),dp(10));l.setBackgroundColor(c);return l;}
    private TextView text(String s,int z,int c,boolean b){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);if(b)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
    private Button button(String s,int bg,int fg){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(fg);b.setBackgroundColor(bg);return b;}
    private LinearLayout fieldBox(String l,String v,int bg,int muted){LinearLayout b=box(bg);b.addView(text(l,10,muted,false));EditText e=new EditText(this);e.setText(v);e.setSingleLine(true);e.setTextColor(Color.WHITE);e.setTextSize(14);e.setBackgroundColor(Color.TRANSPARENT);e.setPadding(0,0,0,0);b.addView(e);return b;}
    private LinearLayout fieldLabel(String l,String v,int bg,int muted){LinearLayout b=box(bg);b.addView(text(l,9,muted,false));b.addView(text(v,13,Color.WHITE,true));return b;}
    private LinearLayout.LayoutParams weight(int w,int h){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,dp(h),1);p.setMargins(dp(3),dp(3),dp(3),dp(3));return p;}
    private void spacer(LinearLayout r,int h){r.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
    private void press(View v){v.animate().scaleX(.97f).scaleY(.97f).setDuration(60).withEndAction(()->v.animate().scaleX(1).scaleY(1).setDuration(80).start()).start();}
    private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
    class SpectrumView extends View{Paint p=new Paint(1);SpectrumView(Context c){super(c);setBackgroundColor(Color.rgb(4,16,22));}protected void onDraw(Canvas c){super.onDraw(c);int w=getWidth(),h=getHeight();p.setStrokeWidth(1);p.setColor(Color.rgb(28,53,63));for(int i=1;i<5;i++)c.drawLine(0,h*i/5f,w,h*i/5f,p);float[] xs={.08f,.22f,.40f,.63f,.82f};float[] amps={.55f,.36f,.62f,.86f,.58f};int[] cols={Color.rgb(45,190,117),Color.rgb(130,198,91),Color.rgb(208,200,75),Color.rgb(232,159,75),Color.rgb(227,98,94)};for(int k=0;k<xs.length;k++){p.setColor(cols[k]);p.setStrokeWidth(2);Path path=new Path();float cx=w*xs[k];path.moveTo(Math.max(0,cx-45),h*.82f);for(int x=-45;x<=45;x+=3){float y=(float)(h*.82-h*amps[k]*Math.exp(-(x*x)/140.0));path.lineTo(cx+x,y);}c.drawPath(path,p);}}}
    class WaterfallView extends View{Paint p=new Paint();WaterfallView(Context c){super(c);}protected void onDraw(Canvas c){int w=getWidth(),h=getHeight();c.drawColor(Color.rgb(7,27,95));int[] xs={(int)(w*.17),(int)(w*.38),(int)(w*.63),(int)(w*.82)};for(int x:xs){for(int r=26;r>2;r-=3){p.setColor(Color.rgb(Math.min(255,80+r*6),Math.min(255,140+r*3),20));c.drawRect(x-r/2,0,x+r/2,h,p);}}}}
    @Override protected void onDestroy(){closeUsb();if(receiverRegistered)try{unregisterReceiver(permissionReceiver);}catch(Throwable ignored){}super.onDestroy();}
}
