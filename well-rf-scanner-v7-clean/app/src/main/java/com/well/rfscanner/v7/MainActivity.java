package com.well.rfscanner.v7;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String ACTION_USB_PERMISSION = "com.well.rfscanner.standard1.USB_PERMISSION";
    private static final int REALTEK_VID = 0x0BDA;
    private static final int RTL2838_PID = 0x2838;
    private static final int SAMPLE_RATE = 2_048_000;
    private static final long CENTER_HZ = 700_000_000L;

    private WebView webView;
    private UsbManager usbManager;
    private UsbDevice activeDevice;
    private UsbDeviceConnection activeConnection;
    private DirectRtlSdr rtl;
    private boolean receiverRegistered = false;
    private volatile boolean iqRunning = false;
    private volatile boolean scanRunning = false;
    private volatile boolean rfInitRunning = false;
    private volatile String lastRfError = "";
    private Thread iqThread;
    private Thread scanThread;

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
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                installRuntimeHooks();
            }
        });
        webView.addJavascriptInterface(new AndroidUsbBridge(), "AndroidUSB");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    public class AndroidUsbBridge {
        @JavascriptInterface public void connect() { runOnUiThread(() -> connectUsb()); }
        @JavascriptInterface public void disconnect() { runOnUiThread(() -> disconnectAll()); }
        @JavascriptInterface public void scan(double startMhz, double endMhz, double stepMhz, int micCount, double guardMhz, double channelMhz) {
            startNativeScan(startMhz, endMhz, stepMhz, micCount, guardMhz, channelMhz);
        }
        @JavascriptInterface public void stopScan() { stopScanOnly(); }
    }

    private void installRuntimeHooks() {
        js("(function(){"+
                "window.nativeRealIq=window.nativeRealIq||false;"+
                "window.nativeScanProgress=function(p,f){var r=document.getElementById('results');if(r)r.innerHTML='<div class=\"note\">กำลังสแกน RF จริง '+p+'% — '+Number(f).toFixed(3)+' MHz</div>';};"+
                "window.nativeScanDone=function(items,sweep){data=sweep;if(typeof draw==='function')draw();if(typeof waterfall==='function')waterfall();var html=items.map(function(x,i){var cls=x.score>82?'good':x.score>68?'mid':'bad';var label=x.score>82?'ดีมาก':x.score>68?'ใช้ได้':'ระวัง';return '<div class=\"result\"><div>'+String(i+1).padStart(2,'0')+'</div><div><div class=\"freq\">'+Number(x.f).toFixed(3)+' MHz</div><div class=\"score\">RF '+Number(x.db).toFixed(1)+' dBFS</div></div><div>'+x.score+'/100</div><div class=\"tag '+cls+'\">'+label+'</div></div>';}).join('');var r=document.getElementById('results');if(r)r.innerHTML=html||'ไม่พบช่องที่เหมาะสม';var c=document.getElementById('coordResults');if(c)c.innerHTML=html||'ไม่พบช่องที่เหมาะสม';document.getElementById('usbText').textContent='Well Connect USB — REAL RF SCAN COMPLETE';document.getElementById('engineVal').textContent='REAL IQ';};"+
                "window.nativeScanError=function(m){var r=document.getElementById('results');if(r)r.innerHTML='<div class=\"note warn\">'+m+'</div>';document.getElementById('engineVal').textContent=window.nativeRealIq?'REAL IQ':'USB OK';};"+
                "var oldTick=window.tick||tick;window.tick=function(){if(window.nativeRealIq){if(typeof draw==='function')draw();if(typeof waterfall==='function')waterfall();return;}return oldTick();};"+
                "var b=document.getElementById('scanBtn');if(b)b.onclick=function(){if(!window.AndroidUSB){nativeScanError('Android USB bridge unavailable');return;}var a=+document.getElementById('start').value,e=+document.getElementById('end').value,st=+document.getElementById('step').value,m=+document.getElementById('micCount').value,g=+document.getElementById('guard').value,ch=+document.getElementById('channel').value;document.getElementById('results').innerHTML='<div class=\"note\">กำลังเตรียม RF และ AUTO CLEAN SCAN...</div>';document.getElementById('engineVal').textContent='SCANNING';AndroidUSB.scan(a,e,st,m,g,ch);};"+
                "var sb=document.getElementById('stopBtn');if(sb)sb.onclick=function(){if(window.AndroidUSB)AndroidUSB.stopScan();if(typeof setLive==='function')setLive(false);document.getElementById('engineVal').textContent=window.nativeRealIq?'REAL IQ':'IDLE';};"+
                "})();");
    }

    private void connectUsb() {
        try {
            usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
            if (usbManager == null) { js("document.getElementById('usbText').textContent='Well Connect USB — USB HOST ไม่พร้อม';"); return; }
            registerReceiverOnce();
            UsbDevice d = findRtl();
            if (d == null) { js("document.getElementById('usbText').textContent='Well Connect USB — ไม่พบ RTL-SDR';"); return; }
            if (usbManager.hasPermission(d)) openDevice(d);
            else {
                Intent i = new Intent(ACTION_USB_PERMISSION).setPackage(getPackageName());
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
                usbManager.requestPermission(d, PendingIntent.getBroadcast(this, 77, i, flags));
                js("document.getElementById('usbText').textContent='Well Connect USB — รออนุญาต USB';");
            }
        } catch (Throwable t) {
            js("document.getElementById('usbText').textContent='Well Connect USB — USB ERROR';");
        }
    }

    private void openDevice(UsbDevice d) {
        stopScanOnly(); stopIqOnly(); closeRtlOnly(); closeUsbOnly();
        activeDevice = d;
        activeConnection = usbManager.openDevice(d);
        if (activeConnection == null) {
            js("nativeUsbDisconnected();document.getElementById('usbText').textContent='Well Connect USB — เปิด USB ไม่สำเร็จ';");
            return;
        }
        js("nativeUsbConnected();window.nativeRealIq=false;document.getElementById('usbText').textContent='Well Connect USB — CONNECTED / กำลังเปิด RF';document.getElementById('engineVal').textContent='RF START';");
        initRfWithRetry(null);
    }

    private void initRfWithRetry(Runnable onReady) {
        if (rfInitRunning) return;
        rfInitRunning = true;
        new Thread(() -> {
            Throwable last = null;
            for (int attempt=1; attempt<=3; attempt++) {
                DirectRtlSdr local = null;
                try {
                    stopIqOnly();
                    closeRtlOnly();
                    closeUsbOnly();
                    if (activeDevice == null || usbManager == null) throw new Exception("USB device missing");
                    activeConnection = usbManager.openDevice(activeDevice);
                    if (activeConnection == null) throw new Exception("เปิด USB ไม่สำเร็จ");
                    js("document.getElementById('usbText').textContent='Well Connect USB — RF INIT " + attempt + "/3';document.getElementById('engineVal').textContent='RF INIT';");
                    local = new DirectRtlSdr(activeDevice, activeConnection, 0.5);
                    local.open();
                    int realRate = local.setSampleRate(SAMPLE_RATE);
                    long realCenter = local.setCenterFrequency(CENTER_HZ);
                    local.resetBuffer();
                    rtl = local;
                    lastRfError = "";
                    double centerMhz = realCenter / 1_000_000.0;
                    double halfMhz = realRate / 2_000_000.0;
                    js(String.format(Locale.US,"window.nativeRealIq=true;window.nativeCaptureStart=%.6f;window.nativeCaptureEnd=%.6f;document.getElementById('usbText').textContent='Well Connect USB — REAL RF %.3f MHz';document.getElementById('engineVal').textContent='REAL IQ';",centerMhz-halfMhz,centerMhz+halfMhz,centerMhz));
                    startIq();
                    if (onReady != null) onReady.run();
                    rfInitRunning = false;
                    return;
                } catch (Throwable t) {
                    last = t;
                    lastRfError = t.getMessage()==null ? t.getClass().getSimpleName() : t.getMessage();
                    try { if (local != null) local.close(); } catch (Throwable ignored) {}
                    rtl = null;
                    closeUsbOnly();
                    try { Thread.sleep(180L * attempt); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                }
            }
            rfInitRunning = false;
            String m = escapeJs(lastRfError.length()==0 ? "RF INIT ERROR" : lastRfError);
            js("window.nativeRealIq=false;document.getElementById('usbText').textContent='Well Connect USB — CONNECTED / RF ERROR';document.getElementById('engineVal').textContent='USB OK';nativeScanError('RF ยังเปิดไม่ได้: " + m + "');");
        }, "WellRF-RetryInit").start();
    }

    private void startIq() {
        if (scanRunning || rtl == null) return;
        stopIqOnly();
        iqRunning = true;
        iqThread = new Thread(() -> {
            byte[] buf = new byte[16 * 1024];
            try {
                while (iqRunning && !scanRunning && rtl != null) {
                    int r = rtl.read(buf, 1000);
                    if (r <= 0) continue;
                    if ((r & 1) != 0) r--;
                    if (r >= 2048) pushSpectrumFromIq(buf, r);
                }
            } catch (Throwable t) {
                if (iqRunning && !scanRunning) {
                    lastRfError = t.getMessage()==null ? "IQ STREAM ERROR" : t.getMessage();
                    js("window.nativeRealIq=false;document.getElementById('engineVal').textContent='USB OK';");
                }
            }
        }, "WellRF-IQ");
        iqThread.start();
    }

    private void startNativeScan(double startMhz, double endMhz, double stepMhz, int micCount, double guardMhz, double channelMhz) {
        if (!(endMhz > startMhz) || startMhz < 24 || endMhz > 1766) { js("nativeScanError('ช่วงความถี่ไม่ถูกต้อง');"); return; }
        if (rtl == null || activeConnection == null) {
            js("document.getElementById('results').innerHTML='<div class=\"note\">RF ยังไม่พร้อม — กำลังเปิดใหม่อัตโนมัติ...</div>';document.getElementById('engineVal').textContent='RF RETRY';");
            initRfWithRetry(() -> startNativeScan(startMhz,endMhz,stepMhz,micCount,guardMhz,channelMhz));
            return;
        }
        stopScanOnly(); stopIqOnly(); scanRunning = true;
        final double scanStart=startMhz, scanEnd=endMhz, reqStep=Math.max(0.025,stepMhz);
        final int reqMics=Math.max(1,Math.min(32,micCount));
        final double minSpacing=Math.max(reqStep,guardMhz*2.0+channelMhz);

        scanThread = new Thread(() -> {
            try {
                final double windowStep=1.60;
                int windows=Math.max(1,(int)Math.ceil((scanEnd-scanStart)/windowStep));
                ArrayList<ScanPoint> points=new ArrayList<>();
                byte[] buf=new byte[16*1024];
                for(int wi=0;wi<=windows&&scanRunning;wi++){
                    double center=Math.min(scanEnd,scanStart+wi*windowStep+windowStep/2.0);
                    long actualHz=rtl.setCenterFrequency((long)(center*1_000_000.0));
                    rtl.resetBuffer();
                    for(int d=0;d<2;d++)rtl.read(buf,350);
                    int r=rtl.read(buf,700);
                    if(r>2048){if((r&1)!=0)r--;float[] sp=spectrum(buf,r,128);double actualCenterMhz=actualHz/1_000_000.0;double spanMhz=SAMPLE_RATE/1_000_000.0;for(int k=5;k<sp.length-5;k++){double f=actualCenterMhz-spanMhz/2.0+spanMhz*k/(sp.length-1.0);if(f>=scanStart&&f<=scanEnd)points.add(new ScanPoint(f,sp[k]));}}
                    if((wi%3)==0){int pct=(int)Math.min(99,Math.round(100.0*wi/Math.max(1,windows)));js(String.format(Locale.US,"nativeScanProgress(%d,%.6f);",pct,center));}
                }
                if(!scanRunning)return;
                if(points.isEmpty())throw new Exception("อ่าน IQ จาก RTL-SDR ไม่ได้");
                ArrayList<ScanPoint> rounded=new ArrayList<>();HashSet<Long> seen=new HashSet<>();
                for(ScanPoint p:points){double rf=Math.round(p.f/reqStep)*reqStep;long key=Math.round(rf*1_000_000.0);if(rf<scanStart||rf>scanEnd||seen.contains(key))continue;seen.add(key);rounded.add(new ScanPoint(rf,p.v));}
                Collections.sort(rounded,Comparator.comparingDouble(a->a.v));ArrayList<ScanPoint> chosen=new ArrayList<>();
                for(ScanPoint p:rounded){boolean ok=true;for(ScanPoint q:chosen)if(Math.abs(q.f-p.f)<minSpacing){ok=false;break;}if(ok){chosen.add(p);if(chosen.size()>=reqMics)break;}}
                Collections.sort(chosen,Comparator.comparingDouble(a->a.f));
                float[] sweep=compressSweep(points,scanStart,scanEnd,420);StringBuilder out=new StringBuilder(7000);out.append("nativeScanDone([");
                for(int i=0;i<chosen.size();i++){ScanPoint p=chosen.get(i);if(i>0)out.append(',');int score=(int)Math.max(0,Math.min(100,Math.round(100-p.v*82)));double dB=-90.0+p.v*75.0;out.append(String.format(Locale.US,"{f:%.6f,db:%.2f,score:%d}",p.f,dB,score));}
                out.append("],[");for(int i=0;i<sweep.length;i++){if(i>0)out.append(',');out.append(String.format(Locale.US,"%.4f",sweep[i]));}out.append("]);");js(out.toString());
            } catch(Throwable t){lastRfError=t.getMessage()==null?"AUTO SCAN ERROR":t.getMessage();if(scanRunning)js("nativeScanError('AUTO SCAN ERROR: "+escapeJs(lastRfError)+"');");}
            finally{scanRunning=false;scanThread=null;if(rtl!=null){try{rtl.setCenterFrequency(CENTER_HZ);rtl.resetBuffer();}catch(Throwable ignored){}startIq();}}
        },"WellRF-AutoScan");
        scanThread.start();
    }

    private float[] compressSweep(ArrayList<ScanPoint> points,double a,double b,int bins){float[] out=new float[bins];int[] cnt=new int[bins];for(ScanPoint p:points){int i=(int)Math.floor((p.f-a)/(b-a)*bins);if(i<0)i=0;if(i>=bins)i=bins-1;out[i]+=p.v;cnt[i]++;}float last=.08f;for(int i=0;i<bins;i++){if(cnt[i]>0){out[i]/=cnt[i];last=out[i];}else out[i]=last;}return out;}
    private static final class ScanPoint{final double f;final float v;ScanPoint(double f,float v){this.f=f;this.v=v;}}

    private void pushSpectrumFromIq(byte[] iq,int len){float[] out=spectrum(iq,len,192);if(out.length==0)return;StringBuilder sb=new StringBuilder(1800);sb.append("window.nativeRealIq=true;data=[");for(int i=0;i<out.length;i++){if(i>0)sb.append(',');sb.append(String.format(Locale.US,"%.4f",out[i]));}sb.append("];if(typeof draw==='function')draw();if(typeof waterfall==='function')waterfall();");js(sb.toString());}
    private float[] spectrum(byte[] iq,int len,int bins){final int n=Math.min(2048,len/2);if(n<512)return new float[0];double meanI=0,meanQ=0;for(int i=0;i<n;i++){meanI+=(iq[i*2]&0xff)-127.5;meanQ+=(iq[i*2+1]&0xff)-127.5;}meanI/=n;meanQ/=n;float[] out=new float[bins];for(int k=0;k<bins;k++){int signedK=k-bins/2;double re=0,im=0;for(int i=0;i<n;i+=8){double w=.5-.5*Math.cos(2*Math.PI*i/(n-1));double iv=((iq[i*2]&0xff)-127.5)-meanI;double qv=((iq[i*2+1]&0xff)-127.5)-meanQ;double ang=-2*Math.PI*signedK*i/bins;double ca=Math.cos(ang),sa=Math.sin(ang);re+=w*(iv*ca-qv*sa);im+=w*(iv*sa+qv*ca);}double mag=Math.sqrt(re*re+im*im)/(n/8.0);double db=20*Math.log10(mag/128.0+1e-9);double norm=(db+90)/75;if(norm<0)norm=0;if(norm>1)norm=1;out[k]=(float)norm;}return out;}

    private void stopScanOnly(){scanRunning=false;if(scanThread!=null)scanThread.interrupt();scanThread=null;}
    private void stopIqOnly(){iqRunning=false;if(iqThread!=null)iqThread.interrupt();iqThread=null;}
    private void closeRtlOnly(){try{if(rtl!=null)rtl.close();}catch(Throwable ignored){}rtl=null;}
    private void closeUsbOnly(){if(activeConnection!=null){try{activeConnection.close();}catch(Throwable ignored){}}activeConnection=null;}
    private void disconnectAll(){stopScanOnly();stopIqOnly();closeRtlOnly();closeUsbOnly();activeDevice=null;js("window.nativeRealIq=false;window.nativeCaptureStart=null;window.nativeCaptureEnd=null;nativeUsbDisconnected();document.getElementById('engineVal').textContent='IDLE';");}

    private void registerReceiverOnce(){if(receiverRegistered)return;IntentFilter f=new IntentFilter(ACTION_USB_PERMISSION);if(Build.VERSION.SDK_INT>=33)registerReceiver(permissionReceiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(permissionReceiver,f);receiverRegistered=true;}
    private UsbDevice findRtl(){HashMap<String,UsbDevice> devices=usbManager.getDeviceList();for(UsbDevice d:devices.values())if(d.getVendorId()==REALTEK_VID&&d.getProductId()==RTL2838_PID)return d;for(UsbDevice d:devices.values())if(d.getVendorId()==REALTEK_VID)return d;return null;}
    private String escapeJs(String s){return s.replace("\\","\\\\").replace("'","\\'").replace("\n"," ").replace("\r"," ");}
    private void js(String code){if(webView==null)return;runOnUiThread(()->webView.evaluateJavascript(code,null));}

    @SuppressWarnings("deprecation") private UsbDevice getUsbDevice(Intent intent){if(Build.VERSION.SDK_INT>=33)return intent.getParcelableExtra(UsbManager.EXTRA_DEVICE,UsbDevice.class);return (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);}
    @Override public void onBackPressed(){if(webView!=null&&webView.canGoBack())webView.goBack();else super.onBackPressed();}
    @Override protected void onDestroy(){disconnectAll();if(receiverRegistered){try{unregisterReceiver(permissionReceiver);}catch(Throwable ignored){}}if(webView!=null)webView.destroy();super.onDestroy();}
}
