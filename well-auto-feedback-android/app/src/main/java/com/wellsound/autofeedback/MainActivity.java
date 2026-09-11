package com.wellsound.autofeedback;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.view.View;
import android.widget.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.util.*;

public class MainActivity extends Activity {
    EditText ip; TextView status,rtaState,candidate,geqState,log; Spinner target,operation,fbMode,fxSlot,side; CheckBox arm; SpectrumView spectrum;
    DatagramSocket socket; volatile boolean running=false,connected=false; volatile long lastRx=0,lastRta=0; String mixerIp="192.168.2.200"; int fxType=-1;
    final float[] rta=new float[100];
    final int[] RTAF={20,21,22,24,26,28,30,32,34,36,39,42,45,48,52,55,59,63,68,73,78,84,90,96,103,110,118,127,136,146,156,167,179,192,206,221,237,254,272,292,313,335,359,385,412,442,474,508,544,583,625,670,718,769,825,884,947,1020,1090,1170,1250,1340,1440,1540,1650,1770,1890,2030,2180,2330,2500,2680,2870,3080,3300,3540,3790,4060,4350,4670,5000,5360,5740,6160,6600,7070,7580,8120,8710,9330,10000,10720,11490,12310,13200,14140,15160,16250,17410,18660};
    final float[] GEQF={20,25,31.5f,40,50,63,80,100,125,160,200,250,315,400,500,630,800,1000,1250,1600,2000,2500,3150,4000,5000,6300,8000,10000,12500,16000,20000};
    final HashMap<Integer,Float> snapshot=new HashMap<>(), current=new HashMap<>(); final ArrayDeque<HashMap<Integer,Float>> undo=new ArrayDeque<>();
    long candStart=0; int candBand=-1;

    @Override public void onCreate(Bundle b){ super.onCreate(b); buildUi(); }

    void buildUi(){
        ScrollView sc=new ScrollView(this); LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(24,24,24,24); root.setBackgroundColor(Color.rgb(8,11,16)); sc.addView(root);
        root.addView(tv("Well Auto Feedback • M32R",24,true)); TextView ver=tv("Android v1.8 SAFE • Dual GEQ • Offline LAN",13,false); ver.setTextColor(Color.LTGRAY); root.addView(ver);
        status=tv("READY — NOT CONNECTED",16,true); status.setTextColor(Color.rgb(251,191,36)); root.addView(status);
        ip=new EditText(this); ip.setSingleLine(true); ip.setText("192.168.2.200"); ip.setTextColor(Color.WHITE); ip.setHintTextColor(Color.GRAY); root.addView(ip,lp());
        Button connect=btn("CONNECT M32R"); root.addView(connect,lp());
        root.addView(label("RTA SOURCE")); target=spin(makeTargets(),1); root.addView(target,lp());
        root.addView(label("FUNCTION")); operation=spin(new String[]{"FEEDBACK","FLAT EQ"},0); root.addView(operation,lp()); fbMode=spin(new String[]{"MONITOR","ASSIST","AUTO","RING OUT"},1); root.addView(fbMode,lp());
        root.addView(label("DUAL GEQ EFFECT")); LinearLayout row=new LinearLayout(this); fxSlot=spin(new String[]{"FX5","FX6","FX7","FX8"},1); side=spin(new String[]{"A","B"},0); row.addView(fxSlot,new LinearLayout.LayoutParams(0,-2,1)); row.addView(side,new LinearLayout.LayoutParams(0,-2,1)); root.addView(row,lp());
        geqState=tv("GEQ: waiting for connection",13,false); geqState.setTextColor(Color.LTGRAY); root.addView(geqState);
        arm=new CheckBox(this); arm.setText("ARM GEQ WRITE"); arm.setTextColor(Color.WHITE); arm.setEnabled(false); root.addView(arm,lp());
        Button flat=btn("ANALYZE / APPLY FLAT EQ"); root.addView(flat,lp());
        spectrum=new SpectrumView(this); root.addView(spectrum,new LinearLayout.LayoutParams(-1,380));
        rtaState=tv("RTA: waiting for M32R",13,false); rtaState.setTextColor(Color.LTGRAY); root.addView(rtaState);
        candidate=tv("Feedback Candidate: —",17,true); root.addView(candidate);
        Button undoBtn=btn("UNDO LAST GEQ CHANGE"); root.addView(undoBtn,lp()); Button restore=btn("RESTORE ORIGINAL GEQ"); root.addView(restore,lp());
        log=tv("READY\nARM = OFF",13,false); log.setTextColor(Color.LTGRAY); root.addView(log,lp()); setContentView(sc);
        connect.setOnClickListener(v->connectMixer()); flat.setOnClickListener(v->applyFlat()); undoBtn.setOnClickListener(v->undoLast()); restore.setOnClickListener(v->restore());
        operation.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){fbMode.setVisibility(pos==0?View.VISIBLE:View.GONE);candidate.setVisibility(pos==0?View.VISIBLE:View.GONE); if(arm!=null) arm.setChecked(false);} public void onNothingSelected(android.widget.AdapterView<?> p){}});
        android.widget.AdapterView.OnItemSelectedListener change=new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){if(connected){arm.setChecked(false);requestGeq();configureRta();}} public void onNothingSelected(android.widget.AdapterView<?> p){}}; target.setOnItemSelectedListener(change);fxSlot.setOnItemSelectedListener(change);side.setOnItemSelectedListener(change);
    }

    String[] makeTargets(){String[] a=new String[17];a[0]="Main LR";for(int i=1;i<=16;i++)a[i]=String.format(Locale.US,"Bus %02d",i);return a;}
    TextView tv(String s,int sz,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextColor(Color.WHITE);v.setTextSize(sz);v.setPadding(0,8,0,8);if(b)v.setTypeface(null,1);return v;}
    TextView label(String s){TextView v=tv(s,12,true);v.setTextColor(Color.rgb(148,163,184));return v;} Button btn(String s){Button b=new Button(this);b.setText(s);return b;}
    Spinner spin(String[] a,int sel){Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,a));s.setSelection(sel);return s;}
    LinearLayout.LayoutParams lp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,6,0,6);return p;}
    void ui(String s){runOnUiThread(()->log.setText(s+"\n"+log.getText()));}

    int fx(){return 5+fxSlot.getSelectedItemPosition();} int param(int band){return 1+band+(side.getSelectedItemPosition()==0?0:32);} String pa(int p){return String.format(Locale.US,"/fx/%d/par/%02d",fx(),p);} String targetName(){return String.valueOf(target.getSelectedItem());}
    int source(){String s=targetName(); if("Main LR".equals(s)) return 70; return 47+Integer.parseInt(s.substring(4));}
    boolean geqOk(){return fxType==0||fxType==2;}

    void connectMixer(){mixerIp=ip.getText().toString().trim(); if(mixerIp.length()<7)return; stop(); status.setText("CONNECTING..."); arm.setEnabled(false); new Thread(()->{try{socket=new DatagramSocket();socket.setSoTimeout(700);running=true;sendNow("/info");byte[] b=new byte[4096];DatagramPacket p=new DatagramPacket(b,b.length);socket.receive(p);connected=true;lastRx=System.currentTimeMillis();runOnUiThread(()->{status.setText("M32R CONNECTED");status.setTextColor(Color.rgb(52,211,153));arm.setEnabled(true);});requestGeq();configureRtaNow();new Thread(this::rxLoop).start();new Thread(this::keepAlive).start();ui("Connected "+mixerIp+":10023");}catch(Exception e){runOnUiThread(()->status.setText("NO RESPONSE"));ui(e.toString());stop();}},"connect").start();}
    void stop(){running=false;connected=false;if(socket!=null){try{socket.close();}catch(Exception e){}socket=null;}}
    synchronized void sendNow(String addr,Object...args)throws Exception{if(socket==null)return;byte[] d=osc(addr,args);socket.send(new DatagramPacket(d,d.length,InetAddress.getByName(mixerIp),10023));}
    void send(String addr,Object...args){new Thread(()->{try{sendNow(addr,args);}catch(Exception e){}}).start();}
    void configureRta(){send("/-prefs/rta/source",source());send("/-prefs/rta/pos",0);send("/-prefs/rta/det",1);} void configureRtaNow()throws Exception{sendNow("/-prefs/rta/source",source());sendNow("/-prefs/rta/pos",0);sendNow("/-prefs/rta/det",1);}
    void requestGeq(){snapshot.clear();current.clear();undo.clear();fxType=-1;send("/fx/"+fx()+"/type");for(int i=0;i<31;i++)send(pa(param(i)));runOnUiThread(()->geqState.setText("GEQ: reading FX"+fx()+" / "+(side.getSelectedItemPosition()==0?"A":"B")));}
    void keepAlive(){long sub=0;while(running){try{long n=System.currentTimeMillis();sendNow("/xremote");if(n-sub>2500||lastRta==0||n-lastRta>1800){configureRtaNow();sendNow("/meters","/meters/15",1);sub=n;}Thread.sleep(700);}catch(Exception e){}}}
    void rxLoop(){byte[] buf=new byte[65535];while(running){try{DatagramPacket p=new DatagramPacket(buf,buf.length);socket.receive(p);lastRx=System.currentTimeMillis();parse(Arrays.copyOf(p.getData(),p.getLength()));}catch(SocketTimeoutException e){}catch(Exception e){}}}
    void parse(byte[] d){try{int[] o={0};String a=readString(d,o),tags=readString(d,o);if(a.equals("/meters/15")&&tags.contains("b")){int n=ByteBuffer.wrap(d,o[0],4).order(ByteOrder.BIG_ENDIAN).getInt();o[0]+=4;if(n>=200&&o[0]+n<=d.length){decode(Arrays.copyOfRange(d,o[0],o[0]+n));lastRta=System.currentTimeMillis();runOnUiThread(()->rtaState.setText("RTA: LIVE • "+targetName()));}return;}if(a.equals("/fx/"+fx()+"/type")&&tags.length()>1&&tags.charAt(1)=='i'){fxType=ByteBuffer.wrap(d,o[0],4).order(ByteOrder.BIG_ENDIAN).getInt();runOnUiThread(()->geqState.setText("GEQ: FX"+fx()+" / "+(side.getSelectedItemPosition()==0?"A":"B")+" • type "+fxType+(geqOk()?" OK":" NOT GEQ")));return;}String pre="/fx/"+fx()+"/par/";if(a.startsWith(pre)&&tags.length()>1&&tags.charAt(1)=='f'){int p=Integer.parseInt(a.substring(pre.length()));float v=ByteBuffer.wrap(d,o[0],4).order(ByteOrder.BIG_ENDIAN).getFloat();current.put(p,v);if(!snapshot.containsKey(p))snapshot.put(p,v);}}catch(Exception e){}}
    void decode(byte[] b){try{int off=b.length>=204?4:0;ByteBuffer bb=ByteBuffer.wrap(b,off,200).order(ByteOrder.LITTLE_ENDIAN);for(int i=0;i<100;i++)rta[i]=bb.getShort()/256f;runOnUiThread(()->spectrum.setData(rta));if(operation.getSelectedItemPosition()==0)detect();}catch(Exception e){}}

    void detect(){int bi=-1;float best=0;for(int i=18;i<94;i++){if(rta[i]<-48||rta[i]<=rta[i-1]||rta[i]<=rta[i+1])continue;float s=0;int n=0;for(int j=Math.max(0,i-4);j<Math.min(100,i+5);j++)if(Math.abs(j-i)>=2){s+=rta[j];n++;}float ex=rta[i]-s/n;if(ex>best&&ex>6.5){best=ex;bi=i;}}if(bi<0){candStart=0;candBand=-1;runOnUiThread(()->candidate.setText("Feedback Candidate: —"));return;}int band=nearestBand(RTAF[bi]);int f=(int)GEQF[band];if(band!=candBand){candBand=band;candStart=System.currentTimeMillis();return;}long held=System.currentTimeMillis()-candStart;runOnUiThread(()->candidate.setText("Feedback Candidate: "+f+" Hz • "+held+" ms"));if(held>450&&arm.isChecked()&&geqOk()&&fbMode.getSelectedItemPosition()>=2){pushUndo();float old=current.containsKey(param(band))?denorm(current.get(param(band))):0;float cut=Math.max(-12,old-3);writeBand(band,cut);ui("FEEDBACK CUT "+f+" Hz → "+cut+" dB");candStart=0;}}
    int nearestBand(float f){int best=0;double d=99;for(int i=0;i<31;i++){double x=Math.abs(Math.log(GEQF[i]/f));if(x<d){d=x;best=i;}}return best;}
    void applyFlat(){if(!connected||lastRta==0)return;float[] smooth=new float[31];for(int i=0;i<31;i++){int idx=nearestRta(GEQF[i]);float s=0;int n=0;for(int j=Math.max(0,idx-2);j<=Math.min(99,idx+2);j++){s+=rta[j];n++;}smooth[i]=s/n;}float med=smooth[15];float[] cp=smooth.clone();Arrays.sort(cp);med=cp[15];if(!arm.isChecked()||!geqOk()){ui("FLAT ANALYZE ready — ARM OFF");return;}pushUndo();for(int i=0;i<31;i++){float gain=Math.max(-6,Math.min(3,med-smooth[i]));writeBand(i,gain);}ui("FLAT EQ APPLIED • 31 GEQ bands");}
    int nearestRta(float f){int best=0;double d=99;for(int i=0;i<100;i++){double x=Math.abs(Math.log(RTAF[i]/f));if(x<d){d=x;best=i;}}return best;}
    void pushUndo(){undo.push(new HashMap<Integer,Float>(current));if(undo.size()>8)undo.removeLast();}
    void writeBand(int i,float db){float v=norm(db);int p=param(i);current.put(p,v);send(pa(p),v);}
    float norm(float db){return Math.max(0,Math.min(1,(db+15f)/30f));} float denorm(float v){return v*30f-15f;}
    void undoLast(){if(undo.isEmpty())return;HashMap<Integer,Float> m=undo.pop();for(Map.Entry<Integer,Float> e:m.entrySet())send(pa(e.getKey()),e.getValue());current.clear();current.putAll(m);ui("UNDO GEQ");}
    void restore(){if(snapshot.isEmpty())return;arm.setChecked(false);for(Map.Entry<Integer,Float> e:snapshot.entrySet())send(pa(e.getKey()),e.getValue());current.clear();current.putAll(snapshot);ui("ORIGINAL GEQ RESTORED");}

    byte[] osc(String addr,Object...args)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream(),pay=new ByteArrayOutputStream();StringBuilder tags=new StringBuilder(",");for(Object x:args){if(x instanceof Integer){tags.append('i');pay.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt((Integer)x).array());}else if(x instanceof Float){tags.append('f');pay.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putFloat((Float)x).array());}else if(x instanceof String){tags.append('s');pay.write(pad((String)x));}}out.write(pad(addr));out.write(pad(tags.toString()));out.write(pay.toByteArray());return out.toByteArray();}
    byte[] pad(String s)throws Exception{ByteArrayOutputStream o=new ByteArrayOutputStream();o.write(s.getBytes(StandardCharsets.UTF_8));o.write(0);while(o.size()%4!=0)o.write(0);return o.toByteArray();}
    String readString(byte[] d,int[] o){int i=o[0],e=i;while(e<d.length&&d[e]!=0)e++;String s=new String(d,i,e-i,StandardCharsets.UTF_8);o[0]=(e+4)&~3;return s;}
    @Override protected void onDestroy(){stop();super.onDestroy();}

    public static class SpectrumView extends View { Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);float[] data=new float[100];SpectrumView(android.content.Context c){super(c);Arrays.fill(data,-90);setBackgroundColor(Color.rgb(5,8,12));}void setData(float[] d){data=d.clone();invalidate();}@Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();p.setColor(Color.rgb(35,48,65));p.setStrokeWidth(1);for(int i=1;i<10;i++){float y=h*i/10f;c.drawLine(0,y,w,y,p);}Path path=new Path();p.setColor(Color.rgb(56,189,248));p.setStrokeWidth(3);for(int i=0;i<100;i++){float x=w*i/99f,db=Math.max(-90,Math.min(0,data[i])),y=(-db/90f)*(h-12)+6;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}c.drawPath(path,p);}}
}
