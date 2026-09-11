package com.wellsound.autofeedback;

import android.app.*;
import android.os.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    EditText ip;
    TextView status,rtaState,candidate,geqState,log,flatText;
    Spinner target,operation,fbMode,fxSlot,side;
    CheckBox arm;
    SpectrumView spectrum;
    DatagramSocket socket;
    volatile boolean running=false,connected=false;
    volatile long lastRx=0,lastRta=0;
    String mixerIp="192.168.2.200";
    int fxType=-1;

    final float[] rta=new float[100];
    final int[] RTAF={20,21,22,24,26,28,30,32,34,36,39,42,45,48,52,55,59,63,68,73,78,84,90,96,103,110,118,127,136,146,156,167,179,192,206,221,237,254,272,292,313,335,359,385,412,442,474,508,544,583,625,670,718,769,825,884,947,1020,1090,1170,1250,1340,1440,1540,1650,1770,1890,2030,2180,2330,2500,2680,2870,3080,3300,3540,3790,4060,4350,4670,5000,5360,5740,6160,6600,7070,7580,8120,8710,9330,10000,10720,11490,12310,13200,14140,15160,16250,17410,18660};
    final float[] GEQF={20f,25f,31.5f,40f,50f,63f,80f,100f,125f,160f,200f,250f,315f,400f,500f,630f,800f,1000f,1250f,1600f,2000f,2500f,3150f,4000f,5000f,6300f,8000f,10000f,12500f,16000f,20000f};

    final HashMap<Integer,Float> geqSnapshot=new HashMap<>();
    final HashMap<Integer,Float> geqCurrent=new HashMap<>();
    final ArrayDeque<HashMap<Integer,Float>> undoStack=new ArrayDeque<>();
    final HashSet<Integer> feedbackBands=new HashSet<>();
    long candStart=0; int candKey=-1;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        try{buildUi();}catch(Throwable e){
            TextView v=new TextView(this);v.setText("Well Auto Feedback M32R v1.7\n\nStartup error:\n"+e);v.setTextColor(Color.WHITE);v.setBackgroundColor(Color.rgb(8,11,16));v.setPadding(30,30,30,30);setContentView(v);
        }
    }

    void buildUi(){
        ScrollView sc=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(28,28,28,28);root.setBackgroundColor(Color.rgb(8,11,16));sc.addView(root);
        root.addView(tv("Well Auto Feedback • M32R",24,true));
        TextView ver=tv("Android v1.7 • Dual GEQ Engine • Offline LAN",13,false);ver.setTextColor(Color.LTGRAY);root.addView(ver);
        status=tv("READY — NOT CONNECTED",16,true);status.setTextColor(Color.rgb(251,191,36));root.addView(status);

        ip=new EditText(this);ip.setSingleLine(true);ip.setText("192.168.2.200");ip.setTextColor(Color.WHITE);ip.setHintTextColor(Color.GRAY);ip.setHint("M32R IP");root.addView(ip,lp());
        Button con=button("CONNECT M32R");root.addView(con,lp());

        root.addView(label("RTA SOURCE / TARGET"));
        String[] targets=new String[17];targets[0]="Main LR";for(int i=1;i<=16;i++)targets[i]=String.format(Locale.US,"Bus %02d",i);
        target=spin(targets,1);root.addView(target,lp());

        root.addView(label("FUNCTION"));
        operation=spin(new String[]{"FEEDBACK","FLAT EQ"},0);root.addView(operation,lp());
        fbMode=spin(new String[]{"MONITOR","ASSIST","AUTO","RING OUT"},1);root.addView(fbMode,lp());

        root.addView(label("M32R DUAL GEQ EFFECT"));
        LinearLayout geqRow=new LinearLayout(this);geqRow.setOrientation(LinearLayout.HORIZONTAL);
        fxSlot=spin(new String[]{"FX5","FX6","FX7","FX8"},1);side=spin(new String[]{"A","B"},0);
        geqRow.addView(fxSlot,new LinearLayout.LayoutParams(0,-2,1));geqRow.addView(side,new LinearLayout.LayoutParams(0,-2,1));root.addView(geqRow,lp());
        geqState=tv("GEQ: waiting for connection",13,false);geqState.setTextColor(Color.LTGRAY);root.addView(geqState);

        arm=new CheckBox(this);arm.setText("ARM GEQ WRITE");arm.setTextColor(Color.WHITE);arm.setEnabled(false);root.addView(arm,lp());
        TextView safe=tv("FEEDBACK และ FLAT EQ ใช้ Dual GEQ ตัวเดียวกัน • ค่าเริ่มต้น FX6 / A • Feedback ลดแบนด์ GEQ ที่ใกล้ความถี่หอน • Flat EQ ปรับ GEQ 31 แบนด์แบบ Smooth",13,false);safe.setTextColor(Color.rgb(251,191,36));root.addView(safe);

        Button flat=button("ANALYZE / APPLY FLAT EQ");root.addView(flat,lp());
        flatText=tv("Flat EQ: waiting for RTA",13,false);flatText.setTextColor(Color.LTGRAY);root.addView(flatText);

        spectrum=new SpectrumView(this);root.addView(spectrum,new LinearLayout.LayoutParams(-1,420));
        rtaState=tv("RTA: waiting for M32R",13,false);rtaState.setTextColor(Color.LTGRAY);root.addView(rtaState);
        candidate=tv("Feedback Candidate: —",18,true);root.addView(candidate);
        Button undo=button("UNDO LAST GEQ CHANGE");root.addView(undo,lp());
        Button restore=button("RESTORE ORIGINAL GEQ");root.addView(restore,lp());
        log=tv("READY\nARM = OFF จึงยังไม่เขียน GEQ",13,false);log.setTextColor(Color.LTGRAY);root.addView(log,lp());
        setContentView(sc);

        con.setOnClickListener(v->connectMixer());
        flat.setOnClickListener(v->analyzeFlat());
        undo.setOnClickListener(v->undoLast());
        restore.setOnClickListener(v->restoreGeq());
        arm.setOnCheckedChangeListener((b,on)->{
            if(on&&!connected){arm.setChecked(false);toast("ต้อง CONNECT M32R ก่อน");return;}
            if(on&&!isDualGeq()){arm.setChecked(false);toast("FX Slot นี้ยังไม่ยืนยันว่าเป็น Dual GEQ / True Dual GEQ");return;}
            if(on)new AlertDialog.Builder(this).setTitle("เปิด ARM GEQ WRITE?").setMessage("แอปจะเขียนค่า Dual GEQ จริงที่ "+fxName()+" / "+sideName()).setPositiveButton("เปิด ARM",(d,w)->uiLog("ARM GEQ WRITE = ON")).setNegativeButton("ยกเลิก",(d,w)->arm.setChecked(false)).show();
            else uiLog("ARM GEQ WRITE = OFF");
        });
        android.widget.AdapterView.OnItemSelectedListener listener=new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){if(connected){arm.setChecked(false);snapshotGeq();configureRta();}}public void onNothingSelected(android.widget.AdapterView<?> p){}};
        target.setOnItemSelectedListener(listener);fxSlot.setOnItemSelectedListener(listener);side.setOnItemSelectedListener(listener);
        operation.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){boolean fb=pos==0;fbMode.setVisibility(fb?View.VISIBLE:View.GONE);candidate.setVisibility(fb?View.VISIBLE:View.GONE);arm.setChecked(false);}public void onNothingSelected(android.widget.AdapterView<?> p){}});
    }

    TextView label(String s){TextView v=tv(s,12,true);v.setTextColor(Color.rgb(148,163,184));return v;}
    Spinner spin(String[] a,int sel){Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,a));s.setSelection(sel);return s;}
    TextView tv(String s,int size,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextColor(Color.WHITE);v.setTextSize(size);v.setPadding(0,10,0,10);if(bold)v.setTypeface(null,1);return v;}
    Button button(String s){Button b=new Button(this);b.setText(s);return b;}
    LinearLayout.LayoutParams lp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,8,0,8);return p;}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}

    String targetName(){return String.valueOf(target.getSelectedItem());}
    int rtaSource(){String s=targetName();if("Main LR".equals(s))return 70;return 47+Integer.parseInt(s.substring(4));}
    int fxNumber(){return 5+fxSlot.getSelectedItemPosition();}
    String fxName(){return "FX"+fxNumber();}
    String sideName(){return side.getSelectedItemPosition()==0?"A":"B";}
    int geqParam(int bandIndex0){return 1+bandIndex0+(side.getSelectedItemPosition()==0?0:32);}
    String parAddr(int p){return String.format(Locale.US,"/fx/%d/par/%02d",fxNumber(),p);}
    boolean isDualGeq(){return fxType==0||fxType==2;}

    void connectMixer(){
        mixerIp=ip.getText().toString().trim();if(mixerIp.isEmpty()){toast("ใส่ IP M32R ก่อน");return;}
        stopOsc();status.setText("CONNECTING...");status.setTextColor(Color.rgb(251,191,36));arm.setEnabled(false);arm.setChecked(false);
        new Thread(()->{
            try{
                socket=new DatagramSocket();socket.setSoTimeout(650);running=true;sendNow("/info");byte[] buf=new byte[8192];DatagramPacket r=new DatagramPacket(buf,buf.length);socket.receive(r);
                connected=true;lastRx=System.currentTimeMillis();runOnUiThread(()->{status.setText("M32R CONNECTED");status.setTextColor(Color.rgb(52,211,153));arm.setEnabled(true);});
                uiLog("OSC reply received from "+mixerIp+":10023");configureRtaNow();snapshotGeq();new Thread(this::rxLoop,"m32-rx").start();new Thread(this::keepAlive,"m32-keepalive").start();
            }catch(Exception e){runOnUiThread(()->{status.setText("NO RESPONSE");status.setTextColor(Color.rgb(248,113,113));});uiLog(e.getClass().getSimpleName()+": "+e.getMessage());stopOsc();}
        },"m32-connect").start();
    }

    void stopOsc(){running=false;connected=false;if(socket!=null){try{socket.close();}catch(Exception ignored){}socket=null;}}
    synchronized void sendNow(String addr,Object...args)throws Exception{if(socket==null)return;byte[] d=osc(addr,args);InetAddress h=InetAddress.getByName(mixerIp);socket.send(new DatagramPacket(d,d.length,h,10023));}
    void send(String addr,Object...args){new Thread(()->{try{sendNow(addr,args);}catch(Exception e){if(running)uiLog("Send: "+e.getMessage());}},"m32-tx").start();}

    void configureRta(){send("/-prefs/rta/source",rtaSource());send("/-prefs/rta/pos",0);send("/-prefs/rta/det",1);}
    void configureRtaNow()throws Exception{sendNow("/-prefs/rta/source",rtaSource());sendNow("/-prefs/rta/pos",0);sendNow("/-prefs/rta/det",1);}
    void snapshotGeq(){
        geqSnapshot.clear();geqCurrent.clear();undoStack.clear();feedbackBands.clear();fxType=-1;send("/fx/"+fxNumber()+"/type");
        for(int i=0;i<31;i++)send(parAddr(geqParam(i)));
        runOnUiThread(()->geqState.setText("GEQ: reading "+fxName()+" / "+sideName()+" snapshot..."));
    }

    void keepAlive(){long lastSub=0;while(running){try{long now=System.currentTimeMillis();sendNow("/xremote");boolean stale=lastRta==0||now-lastRta>1800;if(now-lastSub>2500||stale){configureRtaNow();sendNow("/meters","/meters/15",1);lastSub=now;if(stale)runOnUiThread(()->rtaState.setText("RTA: AUTO-RECONNECT…"));}Thread.sleep(700);}catch(Exception e){if(running)uiLog("KeepAlive: "+e.getMessage());}}}
    void rxLoop(){byte[] buf=new byte[65535];while(running){try{DatagramPacket p=new DatagramPacket(buf,buf.length);socket.receive(p);lastRx=System.currentTimeMillis();parse(Arrays.copyOf(p.getData(),p.getLength()));}catch(SocketTimeoutException e){if(System.currentTimeMillis()-lastRx>4500)runOnUiThread(()->{status.setText("M32R LINK LOST");status.setTextColor(Color.rgb(248,113,113));arm.setChecked(false);});}catch(Exception e){if(running)uiLog("RX: "+e.getMessage());}}}

    void parse(byte[] d){
        try{
            int[] o={0};String a=readString(d,o),tags=readString(d,o);
            if(a.equals("/meters/15")&&tags.contains("b")){
                int n=ByteBuffer.wrap(d,o[0],4).order(ByteOrder.BIG_ENDIAN).getInt();o[0]+=4;if(n>=200&&o[0]+n<=d.length){decodeRta(Arrays.copyOfRange(d,o[0],o[0]+n));lastRta=System.currentTimeMillis();runOnUiThread(()->rtaState.setText("RTA: LIVE • "+targetName()));}return;
            }
            if(a.equals("/fx/"+fxNumber()+"/type")&&tags.length()>1&&tags.charAt(1)=='i'){
                fxType=ByteBuffer.wrap(d,o[0],4).order(ByteOrder.BIG_ENDIAN).getInt();runOnUiThread(()->{String t=fxType==0?"Dual GEQ":fxType==2?"Dual TrueEQ":"Type "+fxType+" (ไม่ใช่ Dual GEQ)";geqState.setText("GEQ: "+fxName()+" / "+sideName()+" • "+t);if(!isDualGeq())arm.setChecked(false);});return;
            }
            String prefix="/fx/"+fxNumber()+"/par/";
            if(a.startsWith(prefix)&&tags.length()>1&&tags.charAt(1)=='f'){
                int p=Integer.parseInt(a.substring(prefix.length()));float v=ByteBuffer.wrap(d,o[0],4).order(ByteOrder.BIG_ENDIAN).getFloat();geqCurrent.put(p,v);if(!geqSnapshot.containsKey(p))geqSnapshot.put(p,v);return;
            }
        }catch(Exception ignored){}
    }

    void decodeRta(byte[] b){try{int off=b.length>=204?4:0;if(b.length-off<200)return;ByteBuffer bb=ByteBuffer.wrap(b,off,200).order(ByteOrder.LITTLE_ENDIAN);for(int i=0;i<100;i++)rta[i]=bb.getShort()/256f;runOnUiThread(()->spectrum.setData(rta));if("FEEDBACK".equals(String.valueOf(operation.getSelectedItem())))detectFeedback();}catch(Exception ignored){}}

    void detectFeedback(){
        int bi=-1,bc=0;float be=0;
        for(int i=18;i<94;i++){
            if(rta[i]<-48||rta[i]<=rta[i-1]||rta[i]<=rta[i+1])continue;float sum=0;int n=0;for(int j=Math.max(0,i-4);j<Math.min(100,i+5);j++)if(Math.abs(j-i)>=2){sum+=rta[j];n++;}float ex=rta[i]-sum/n;if(ex<6.5f)continue;float near=Math.max(rta[i-1],rta[i+1]);int c=(int)Math.max(0,Math.min(99,45+ex*4.8+Math.max(0,rta[i]-near)*2+Math.max(0,rta[i]+48)*.7));if(c>bc){bc=c;bi=i;be=ex;}
        }
        if(bi<0){candKey=-1;candStart=0;runOnUiThread(()->candidate.setText("Feedback Candidate: —"));return;}
        int f=RTAF[bi],conf=bc;float ex=be,db=rta[bi];int key=(int)Math.round(Math.log(f)/.035);if(key!=candKey){candKey=key;candStart=System.currentTimeMillis();return;}long held=System.currentTimeMillis()-candStart;
        runOnUiThread(()->candidate.setText("Feedback Candidate: "+f+" Hz • "+conf+"% • "+String.format(Locale.US,"%.1f dB",db)+" • "+held+" ms"));
        if(held>=350&&conf>=92){String m=String.valueOf(fbMode.getSelectedItem());if(arm.isChecked()&&("AUTO".equals(m)||"RING OUT".equals(m)))cutFeedbackGeq(f,ex);candKey=-1;candStart=0;}
    }

    synchronized void cutFeedbackGeq(int f,float excess){
        int idx=nearestGeqBand(f),p=geqParam(idx);if(feedbackBands.size()>=8&&!feedbackBands.contains(idx)){uiLog("Feedback GEQ limit reached (8 bands)");return;}
        float prev=geqCurrent.containsKey(p)?geqCurrent.get(p):(geqSnapshot.containsKey(p)?geqSnapshot.get(p):dbToNorm(0));float prevDb=normToDb(prev);float cut=Math.min(6f,Math.max(2.0f,Math.round(excess*.45f*4)/4f));float nextDb=Math.max(-15f,prevDb-cut);
        HashMap<Integer,Float> action=new HashMap<>();action.put(p,prev);undoStack.push(action);writeGeq(p,nextDb);feedbackBands.add(idx);uiLog("FEEDBACK → "+fxName()+" "+sideName()+" • "+fmtFreq(GEQF[idx])+" Hz • "+String.format(Locale.US,"%.2f → %.2f dB",prevDb,nextDb));
    }

    void analyzeFlat(){
        if(!connected){toast("CONNECT M32R ก่อน");return;}if(lastRta==0||System.currentTimeMillis()-lastRta>2500){toast("รอ RTA LIVE ก่อน");return;}
        float[] level=new float[31];for(int i=0;i<31;i++)level[i]=smoothAt(GEQF[i]);
        ArrayList<Float> mid=new ArrayList<>();for(int i=0;i<31;i++)if(GEQF[i]>=63&&GEQF[i]<=12500)mid.add(level[i]);Collections.sort(mid);float ref=mid.get(mid.size()/2);
        float[] raw=new float[31];for(int i=0;i<31;i++)raw[i]=clamp(ref-level[i],-6f,3f);
        float[] corr=new float[31];for(int i=0;i<31;i++){float a=raw[Math.max(0,i-1)],b=raw[i],c=raw[Math.min(30,i+1)];corr[i]=clamp((a+2*b+c)/4f,-6f,3f);}
        StringBuilder sb=new StringBuilder("Smooth Flat GEQ suggestion\nReference ").append(String.format(Locale.US,"%.1f dB",ref)).append("\n");for(int i=0;i<31;i+=3)sb.append(fmtFreq(GEQF[i])).append(" ").append(String.format(Locale.US,"%+.1f",corr[i])).append(" dB   ");
        runOnUiThread(()->flatText.setText(sb.toString()));
        if(!arm.isChecked()){uiLog("FLAT EQ analyzed only — ARM OFF");return;}if(!isDualGeq()){toast("เลือก FX ที่เป็น Dual GEQ ก่อน");return;}
        HashMap<Integer,Float> action=new HashMap<>();for(int i=0;i<31;i++){int p=geqParam(i);float prev=geqCurrent.containsKey(p)?geqCurrent.get(p):(geqSnapshot.containsKey(p)?geqSnapshot.get(p):dbToNorm(0));action.put(p,prev);float baseDb=geqSnapshot.containsKey(p)?normToDb(geqSnapshot.get(p)):0f;writeGeq(p,clamp(baseDb+corr[i],-15f,15f));}undoStack.push(action);uiLog("FLAT EQ → APPLIED to "+fxName()+" / "+sideName()+" • 31 GEQ bands");
    }

    float smoothAt(float f){int bi=0;double best=1e9;for(int i=0;i<RTAF.length;i++){double d=Math.abs(Math.log(RTAF[i]/(double)f));if(d<best){best=d;bi=i;}}float sum=0,wt=0;for(int j=Math.max(0,bi-2);j<=Math.min(99,bi+2);j++){float w=(j==bi?3:(Math.abs(j-bi)==1?2:1));sum+=rta[j]*w;wt+=w;}return sum/wt;}
    int nearestGeqBand(float f){int bi=0;double best=1e9;for(int i=0;i<31;i++){double d=Math.abs(Math.log(GEQF[i]/f));if(d<best){best=d;bi=i;}}return bi;}
    String fmtFreq(float f){if(f>=1000)return (f%1000==0?String.format(Locale.US,"%.0fk",f/1000f):String.format(Locale.US,"%.1fk",f/1000f));return f==(int)f?String.format(Locale.US,"%d",(int)f):String.format(Locale.US,"%.1f",f);}

    void writeGeq(int p,float db){float v=dbToNorm(db);send(parAddr(p),v);geqCurrent.put(p,v);}
    float dbToNorm(float db){return clamp((db+15f)/30f,0f,1f);}
    float normToDb(float n){return n*30f-15f;}
    float clamp(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}

    void undoLast(){if(undoStack.isEmpty()){toast("ยังไม่มี GEQ change");return;}HashMap<Integer,Float> a=undoStack.pop();for(Map.Entry<Integer,Float> e:a.entrySet()){send(parAddr(e.getKey()),e.getValue());geqCurrent.put(e.getKey(),e.getValue());}uiLog("UNDO last GEQ change");}
    void restoreGeq(){if(geqSnapshot.isEmpty()){toast("ยังไม่มี GEQ Snapshot");return;}arm.setChecked(false);for(Map.Entry<Integer,Float> e:geqSnapshot.entrySet()){send(parAddr(e.getKey()),e.getValue());geqCurrent.put(e.getKey(),e.getValue());}undoStack.clear();feedbackBands.clear();uiLog("RESTORE ORIGINAL GEQ → "+fxName()+" / "+sideName());}
    void uiLog(String s){runOnUiThread(()->{if(log!=null)log.setText(s+"\n"+log.getText());});}

    byte[] osc(String a,Object...args)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream(),pay=new ByteArrayOutputStream();StringBuilder tags=new StringBuilder(",");for(Object x:args){if(x instanceof Integer){tags.append('i');pay.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt((Integer)x).array());}else if(x instanceof Float){tags.append('f');pay.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putFloat((Float)x).array());}else if(x instanceof String){tags.append('s');pay.write(padString((String)x));}}out.write(padString(a));out.write(padString(tags.toString()));out.write(pay.toByteArray());return out.toByteArray();}
    byte[] padString(String s)throws Exception{ByteArrayOutputStream o=new ByteArrayOutputStream();o.write(s.getBytes(StandardCharsets.UTF_8));o.write(0);while(o.size()%4!=0)o.write(0);return o.toByteArray();}
    String readString(byte[] d,int[] o){int i=o[0],e=i;while(e<d.length&&d[e]!=0)e++;String s=new String(d,i,e-i,StandardCharsets.UTF_8);o[0]=(e+4)&~3;return s;}

    @Override protected void onDestroy(){stopOsc();super.onDestroy();}

    public static class SpectrumView extends View{
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);float[] data=new float[100];SpectrumView(android.content.Context c){super(c);Arrays.fill(data,-90);setBackgroundColor(Color.rgb(5,8,12));}void setData(float[] d){data=d.clone();invalidate();}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();p.setStrokeWidth(1);p.setColor(Color.rgb(35,48,65));for(int i=1;i<10;i++){float y=h*i/10f;c.drawLine(0,y,w,y,p);}p.setStrokeWidth(3);p.setColor(Color.rgb(56,189,248));p.setStyle(Paint.Style.STROKE);Path path=new Path();for(int i=0;i<100;i++){float x=w*i/99f,db=Math.max(-90,Math.min(0,data[i])),y=(-db/90f)*(h-12)+6;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}c.drawPath(path,p);p.setStyle(Paint.Style.FILL);}
    }
}
