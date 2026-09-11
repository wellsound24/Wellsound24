package com.wellsound.autofeedback;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    EditText ip;
    TextView status, candidate, notchText, log;
    Spinner target, mode;
    CheckBox arm;
    DatagramSocket sock;
    volatile boolean running=false, connected=false;
    volatile long lastRx=0;
    String mixerIp="192.168.1.100";
    final ArrayList<Notch> bank=new ArrayList<>();
    final HashMap<String,Object> snapshot=new HashMap<>();
    final float[] rta=new float[100];
    long candStart=0; int candKey=-1;
    final int[] F={20,21,22,24,26,28,30,32,34,36,39,42,45,48,52,55,59,63,68,73,78,84,90,96,103,110,118,127,136,146,156,167,179,192,206,221,237,254,272,292,313,335,359,385,412,442,474,508,544,583,625,670,718,769,825,884,947,1020,1090,1170,1250,1340,1440,1540,1650,1770,1890,2030,2180,2330,2500,2680,2870,3080,3300,3540,3790,4060,4350,4670,5000,5360,5740,6160,6600,7070,7580,8120,8710,9330,10000,10720,11490,12310,13200,14140,15160,16250,17410,18660};

    static class Notch { int band,f,conf; float cut,q; Notch(int b,int ff,float c,float qq,int co){band=b;f=ff;cut=c;q=qq;conf=co;} }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        Thread.setDefaultUncaughtExceptionHandler((t,e)->{
            try{
                runOnUiThread(()->showFatal(e));
            }catch(Exception ignored){}
        });
        try{ buildUi(); }
        catch(Throwable e){ showFatal(e); }
    }

    void showFatal(Throwable e){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(30,30,30,30);root.setBackgroundColor(Color.rgb(8,11,16));
        TextView a=text("Well Auto Feedback M32R v1.2",23,true);root.addView(a);
        TextView b=text("แอปเปิดได้ แต่พบข้อผิดพลาดในหน้าจอ",16,true);b.setTextColor(Color.YELLOW);root.addView(b);
        TextView c=text(String.valueOf(e),13,false);c.setTextColor(Color.LTGRAY);root.addView(c);
        setContentView(root);
    }

    void buildUi(){
        ScrollView sv=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(28,28,28,28);root.setBackgroundColor(Color.rgb(8,11,16));sv.addView(root);
        root.addView(text("Well Auto Feedback • M32R",24,true));
        TextView ver=text("Android v1.2 • Direct OSC LAN",13,false);ver.setTextColor(Color.LTGRAY);root.addView(ver);
        status=text("DISCONNECTED",16,true);status.setTextColor(Color.rgb(248,113,113));root.addView(status);
        ip=new EditText(this);ip.setText("192.168.1.100");ip.setSingleLine(true);ip.setTextColor(Color.WHITE);ip.setHintTextColor(Color.GRAY);ip.setHint("M32R IP");root.addView(ip,lp());
        Button con=button("CONNECT M32R");root.addView(con,lp());
        ArrayList<String> tg=new ArrayList<>();tg.add("Main LR");for(int i=1;i<=16;i++)tg.add(String.format(Locale.US,"Bus %02d",i));
        target=new Spinner(this);ArrayAdapter<String> ta=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,tg);ta.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);target.setAdapter(ta);root.addView(target,lp());
        mode=new Spinner(this);ArrayAdapter<String> ma=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,new String[]{"MONITOR","ASSIST","AUTO","RING OUT"});ma.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);mode.setAdapter(ma);mode.setSelection(1);root.addView(mode,lp());
        arm=new CheckBox(this);arm.setText("ARM AUTO CUT");arm.setTextColor(Color.WHITE);root.addView(arm,lp());
        TextView safe=text("เริ่มต้น ARM = OFF • ทดสอบ ASSIST ก่อนเปิด AUTO",13,false);safe.setTextColor(Color.rgb(251,191,36));root.addView(safe);
        candidate=text("Candidate: —",18,true);root.addView(candidate);
        notchText=text("Auto Notches: 0 / 6",16,false);root.addView(notchText);
        Button undo=button("UNDO LAST CUT");root.addView(undo,lp());
        Button restore=button("RESTORE ORIGINAL EQ");root.addView(restore,lp());
        log=text("READY\nยังไม่มีการแก้ EQ จนกว่าจะเปิด ARM",13,false);log.setTextColor(Color.LTGRAY);root.addView(log,lp());
        setContentView(sv);
        con.setOnClickListener(v->connectMixer());undo.setOnClickListener(v->undoLast());restore.setOnClickListener(v->restoreEq());
        target.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){if(sock!=null){bank.clear();configure();snapshotEq();refresh();}}
            public void onNothingSelected(android.widget.AdapterView<?> p){}
        });
    }

    TextView text(String s,int size,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextColor(Color.WHITE);v.setTextSize(size);v.setPadding(0,10,0,10);if(bold)v.setTypeface(null,1);return v;}
    Button button(String s){Button b=new Button(this);b.setText(s);return b;}
    LinearLayout.LayoutParams lp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,8,0,8);return p;}
    String base(){String s=target==null?"Main LR":String.valueOf(target.getSelectedItem());if("Main LR".equals(s))return "/main/st";return "/bus/"+s.substring(4);}
    int source(){String s=target==null?"Main LR":String.valueOf(target.getSelectedItem());if("Main LR".equals(s))return 72;return 49+Integer.parseInt(s.substring(4));}

    void connectMixer(){
        mixerIp=ip.getText().toString().trim();
        if(mixerIp.isEmpty()){toast("กรุณาใส่ IP ของ M32R");return;}
        stopOsc();
        status.setText("CONNECTING...");status.setTextColor(Color.rgb(251,191,36));
        new Thread(()->{
            try{
                sock=new DatagramSocket();sock.setSoTimeout(600);running=true;
                sendNow("/info");configureNow();
                new Thread(this::rxLoop,"m32-rx").start();new Thread(this::keepAlive,"m32-keepalive").start();
                uiLog("Connecting to "+mixerIp+":10023");
                SystemClock.sleep(250);snapshotEq();
            }catch(Exception e){uiLog("Connect error: "+e.getMessage());runOnUiThread(()->{status.setText("CONNECT ERROR");status.setTextColor(Color.rgb(248,113,113));});}
        },"m32-connect").start();
    }
    void stopOsc(){running=false;connected=false;if(sock!=null){try{sock.close();}catch(Exception ignored){}sock=null;}}
    synchronized void sendNow(String addr,Object...args)throws Exception{if(sock==null)return;byte[] d=osc(addr,args);InetAddress h=InetAddress.getByName(mixerIp);sock.send(new DatagramPacket(d,d.length,h,10023));}
    void send(String addr,Object...args){new Thread(()->{try{sendNow(addr,args);}catch(Exception e){uiLog("Send: "+e.getMessage());}}).start();}
    void configure(){send("/-prefs/rta/source",source());send("/-prefs/rta/pos",0);send("/-prefs/rta/det",1);}
    void configureNow()throws Exception{sendNow("/-prefs/rta/source",source());sendNow("/-prefs/rta/pos",0);sendNow("/-prefs/rta/det",1);}
    void snapshotEq(){snapshot.clear();String b=base();send(b+"/eq/on");for(int i=1;i<=6;i++)for(String p:new String[]{"type","f","g","q"})send(b+"/eq/"+i+"/"+p);}
    void keepAlive(){long sub=0;while(running){try{sendNow("/xremote");if(System.currentTimeMillis()-sub>7000){configureNow();sendNow("/meters","/meters/15",1);sub=System.currentTimeMillis();}Thread.sleep(1000);}catch(Exception e){if(running)uiLog("KeepAlive: "+e.getMessage());}}}
    void rxLoop(){byte[] buf=new byte[65535];while(running){try{DatagramPacket p=new DatagramPacket(buf,buf.length);sock.receive(p);lastRx=System.currentTimeMillis();if(!connected){connected=true;runOnUiThread(()->{status.setText("M32R CONNECTED");status.setTextColor(Color.rgb(52,211,153));});uiLog("OSC response received");}parse(Arrays.copyOf(p.getData(),p.getLength()));}catch(SocketTimeoutException e){if(System.currentTimeMillis()-lastRx>4000)runOnUiThread(()->{status.setText("NO RESPONSE");status.setTextColor(Color.rgb(248,113,113));});}catch(Exception e){if(running)uiLog("RX: "+e.getMessage());}}}

    void parse(byte[] d){try{int[] o={0};String a=readString(d,o),tags=readString(d,o);if(a.equals("/meters/15")&&tags.contains("b")){int n=ByteBuffer.wrap(d,o[0],4).order(ByteOrder.BIG_ENDIAN).getInt();o[0]+=4;if(n>=200&&o[0]+n<=d.length)decodeRta(Arrays.copyOfRange(d,o[0],o[0]+n));return;}if((a.startsWith(base()+"/eq/")||a.equals(base()+"/eq/on"))&&tags.length()>1){Object val=null;if(tags.charAt(1)=='i')val=ByteBuffer.wrap(d,o[0],4).order(ByteOrder.BIG_ENDIAN).getInt();else if(tags.charAt(1)=='f')val=ByteBuffer.wrap(d,o[0],4).order(ByteOrder.BIG_ENDIAN).getFloat();if(val!=null&&!snapshot.containsKey(a))snapshot.put(a,val);}}catch(Exception ignored){}}
    void decodeRta(byte[] b){try{int off=b.length>=204?4:0;if(b.length-off<200)return;ByteBuffer bb=ByteBuffer.wrap(b,off,200).order(ByteOrder.LITTLE_ENDIAN);for(int i=0;i<100;i++)rta[i]=bb.getShort()/256f;detect();}catch(Exception ignored){}}
    void detect(){int bi=-1,bc=0;float be=0;for(int i=20;i<92;i++){if(rta[i]<-48||rta[i]<=rta[i-1]||rta[i]<=rta[i+1])continue;float sum=0;int n=0;for(int j=Math.max(0,i-4);j<Math.min(100,i+5);j++)if(Math.abs(j-i)>=2){sum+=rta[j];n++;}float ex=rta[i]-sum/n;if(ex<6.5f)continue;float near=Math.max(rta[i-1],rta[i+1]);int c=(int)Math.max(0,Math.min(99,45+ex*4.8+Math.max(0,rta[i]-near)*2+Math.max(0,rta[i]+48)*.7));if(c>bc){bc=c;bi=i;be=ex;}}if(bi<0){candKey=-1;candStart=0;return;}int f=F[bi],conf=bc;float ex=be,db=rta[bi];int key=(int)Math.round(Math.log(f)/.035);if(key!=candKey){candKey=key;candStart=System.currentTimeMillis();return;}long held=System.currentTimeMillis()-candStart;runOnUiThread(()->candidate.setText("Candidate: "+f+" Hz • "+conf+"% • "+String.format(Locale.US,"%.1f dB",db)));if(held>=350&&conf>=92){boolean exists=false;for(Notch z:bank)if(Math.abs(Math.log(z.f/(double)f))<.055)exists=true;String m=String.valueOf(mode.getSelectedItem());if(!exists&&arm.isChecked()&&("AUTO".equals(m)||"RING OUT".equals(m)))applyNotch(f,conf,ex);candKey=-1;candStart=0;}}
    synchronized void applyNotch(int f,int conf,float ex){if(bank.size()>=6)return;int band=bank.size()+1;float cut=-Math.min(9f,Math.max(3f,Math.round(ex*.65f*4)/4f)),q=Math.min(10f,Math.max(6f,7f+ex*.25f));send(base()+"/eq/on",1);send(base()+"/eq/"+band+"/type",2);send(base()+"/eq/"+band+"/f",nlog(f,20,20000));send(base()+"/eq/"+band+"/g",nlin(cut,-15,15));send(base()+"/eq/"+band+"/q",nlog(q,10,.3f));bank.add(new Notch(band,f,cut,q,conf));uiLog("AUTO CUT EQ"+band+" "+f+"Hz "+cut+"dB Q"+q);runOnUiThread(this::refresh);}
    void undoLast(){if(bank.isEmpty()){toast("ยังไม่มี Auto Cut");return;}Notch n=bank.remove(bank.size()-1);for(String p:new String[]{"type","f","g","q"}){String a=base()+"/eq/"+n.band+"/"+p;Object v=snapshot.get(a);if(v!=null)send(a,v);}refresh();uiLog("Undo last cut");}
    void restoreEq(){if(snapshot.isEmpty()){toast("ยังไม่มี EQ Snapshot — Connect M32R ก่อน");return;}for(Map.Entry<String,Object> e:snapshot.entrySet())send(e.getKey(),e.getValue());bank.clear();refresh();uiLog("Original EQ restored");}
    void refresh(){notchText.setText("Auto Notches: "+bank.size()+" / 6");}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    void uiLog(String s){runOnUiThread(()->{if(log!=null)log.setText(s+"\n"+log.getText());});}
    float nlog(float v,float lo,float hi){return(float)(Math.log(v/lo)/Math.log(hi/lo));}
    float nlin(float v,float lo,float hi){return Math.max(0,Math.min(1,(v-lo)/(hi-lo)));}

    byte[] osc(String a,Object...args)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream(),pay=new ByteArrayOutputStream();StringBuilder tags=new StringBuilder(",");for(Object x:args){if(x instanceof Integer){tags.append('i');pay.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt((Integer)x).array());}else if(x instanceof Float){tags.append('f');pay.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putFloat((Float)x).array());}else if(x instanceof String){tags.append('s');pay.write(padString((String)x));}}out.write(padString(a));out.write(padString(tags.toString()));out.write(pay.toByteArray());return out.toByteArray();}
    byte[] padString(String s)throws Exception{ByteArrayOutputStream o=new ByteArrayOutputStream();o.write(s.getBytes(StandardCharsets.UTF_8));o.write(0);while(o.size()%4!=0)o.write(0);return o.toByteArray();}
    String readString(byte[] d,int[] o){int i=o[0],e=i;while(e<d.length&&d[e]!=0)e++;String s=new String(d,i,e-i,StandardCharsets.UTF_8);o[0]=(e+4)&~3;return s;}
    @Override protected void onDestroy(){super.onDestroy();stopOsc();}
}
