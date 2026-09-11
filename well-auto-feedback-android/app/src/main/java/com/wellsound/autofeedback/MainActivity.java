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
    TextView status, candidate, notchText, log, rtaState, flatInfo;
    Spinner target, operation, mode;
    CheckBox arm;
    Button flatButton;
    SpectrumView spectrum;
    DatagramSocket socket;
    volatile boolean running=false, connected=false;
    volatile long lastRx=0, lastRta=0;
    String mixerIp="192.168.2.200";
    final ArrayList<Notch> bank=new ArrayList<>();
    final HashMap<String,Object> snapshot=new HashMap<>();
    final float[] rta=new float[100];
    long candStart=0; int candKey=-1;

    final int[] F={20,21,22,24,26,28,30,32,34,36,39,42,45,48,52,55,59,63,68,73,78,84,90,96,103,110,118,127,136,146,156,167,179,192,206,221,237,254,272,292,313,335,359,385,412,442,474,508,544,583,625,670,718,769,825,884,947,1020,1090,1170,1250,1340,1440,1540,1650,1770,1890,2030,2180,2330,2500,2680,2870,3080,3300,3540,3790,4060,4350,4670,5000,5360,5740,6160,6600,7070,7580,8120,8710,9330,10000,10720,11490,12310,13200,14140,15160,16250,17410,18660};
    final int[] FLAT_CENTER={125,250,500,1000,2500,6300};
    final int[][] FLAT_RANGE={{80,180},{180,355},{355,710},{710,1400},{1400,3500},{3500,12000}};

    static class Notch { int band,f,conf; float cut,q; Notch(int b,int ff,float c,float qq,int co){band=b;f=ff;cut=c;q=qq;conf=co;} }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        try{ buildUi(); }
        catch(Throwable e){
            TextView v=new TextView(this);v.setText("Well Auto Feedback M32R v1.6\n\nStartup error:\n"+e);v.setTextColor(Color.WHITE);v.setBackgroundColor(Color.rgb(8,11,16));v.setPadding(30,30,30,30);setContentView(v);
        }
    }

    void buildUi(){
        ScrollView sc=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(28,28,28,28);root.setBackgroundColor(Color.rgb(8,11,16));sc.addView(root);
        root.addView(tv("Well Auto Feedback • M32R",24,true));
        TextView ver=tv("Android v1.6 • Offline LAN • Feedback / Smooth Flat EQ",13,false);ver.setTextColor(Color.LTGRAY);root.addView(ver);
        status=tv("READY — NOT CONNECTED",16,true);status.setTextColor(Color.rgb(251,191,36));root.addView(status);

        ip=new EditText(this);ip.setSingleLine(true);ip.setText("192.168.2.200");ip.setTextColor(Color.WHITE);ip.setHintTextColor(Color.GRAY);ip.setHint("M32R IP");root.addView(ip,lp());
        Button con=button("CONNECT M32R");root.addView(con,lp());

        String[] targets=new String[17];targets[0]="Main LR";for(int i=1;i<=16;i++)targets[i]=String.format(Locale.US,"Bus %02d",i);
        target=new Spinner(this);target.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,targets));target.setSelection(1);root.addView(target,lp());

        operation=new Spinner(this);
        operation.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"FEEDBACK","FLAT EQ"}));
        root.addView(operation,lp());

        mode=new Spinner(this);mode.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"MONITOR","ASSIST","AUTO","RING OUT"}));mode.setSelection(1);root.addView(mode,lp());

        arm=new CheckBox(this);arm.setText("ARM AUTO CUT");arm.setTextColor(Color.WHITE);arm.setEnabled(false);root.addView(arm,lp());
        TextView safe=tv("FEEDBACK = คัทเสียงหอนแบบ Notch แคบ • FLAT EQ = ปรับกว้างแบบ Smooth สูงสุด +3 / -6 dB",13,false);safe.setTextColor(Color.rgb(251,191,36));root.addView(safe);

        flatButton=button("ANALYZE / APPLY FLAT EQ");flatButton.setVisibility(View.GONE);root.addView(flatButton,lp());
        flatInfo=tv("FLAT EQ: รอข้อมูล RTA",13,false);flatInfo.setTextColor(Color.LTGRAY);flatInfo.setVisibility(View.GONE);root.addView(flatInfo);

        spectrum=new SpectrumView(this);root.addView(spectrum,new LinearLayout.LayoutParams(-1,420));
        rtaState=tv("RTA: waiting for M32R",13,false);rtaState.setTextColor(Color.LTGRAY);root.addView(rtaState);
        candidate=tv("Candidate: —",18,true);root.addView(candidate);
        notchText=tv("Auto Notches: 0 / 6",16,false);root.addView(notchText);
        Button undo=button("UNDO LAST CUT");root.addView(undo,lp());
        Button restore=button("RESTORE ORIGINAL EQ");root.addView(restore,lp());
        log=tv("READY\nARM = OFF จึงยังไม่แก้ EQ",13,false);log.setTextColor(Color.LTGRAY);root.addView(log,lp());
        setContentView(sc);

        con.setOnClickListener(v->connectMixer());
        undo.setOnClickListener(v->undoLast());
        restore.setOnClickListener(v->restoreEq());
        flatButton.setOnClickListener(v->runFlatEq());

        operation.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){
                arm.setChecked(false);
                boolean flat=pos==1;
                mode.setVisibility(flat?View.GONE:View.VISIBLE);
                flatButton.setVisibility(flat?View.VISIBLE:View.GONE);
                flatInfo.setVisibility(flat?View.VISIBLE:View.GONE);
                candidate.setVisibility(flat?View.GONE:View.VISIBLE);
                arm.setText(flat?"ARM FLAT EQ WRITE":"ARM AUTO CUT");
                notchText.setText(flat?"FLAT EQ: ยังไม่ได้ Apply":"Auto Notches: "+bank.size()+" / 6");
                uiLog("Mode → "+(flat?"FLAT EQ":"FEEDBACK"));
            }
            public void onNothingSelected(android.widget.AdapterView<?> p){}
        });

        arm.setOnCheckedChangeListener((b,isChecked)->{
            if(isChecked && !connected){ arm.setChecked(false); toast("ต้อง CONNECT M32R ก่อน"); return; }
            if(isChecked){
                boolean flat=isFlat();
                String msg=flat?"แอปจะเขียน PEQ 1-6 ของ "+String.valueOf(target.getSelectedItem())+" เพื่อปรับ Smooth Flat EQ":"แอปจะสามารถคัท Feedback และเขียน PEQ จริงของ "+String.valueOf(target.getSelectedItem())+" เมื่อเลือก AUTO หรือ RING OUT";
                new AlertDialog.Builder(this).setTitle(flat?"เปิด ARM FLAT EQ?":"เปิด ARM AUTO CUT?").setMessage(msg).setPositiveButton("เปิด ARM",(d,w)->uiLog("ARM = ON")).setNegativeButton("ยกเลิก",(d,w)->arm.setChecked(false)).show();
            } else uiLog("ARM = OFF");
        });

        target.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){if(socket!=null&&connected){arm.setChecked(false);bank.clear();configure();snapshotEq();refresh();uiLog("Target → "+String.valueOf(target.getSelectedItem()));}}
            public void onNothingSelected(android.widget.AdapterView<?> p){}
        });
    }

    boolean isFlat(){return operation!=null && operation.getSelectedItemPosition()==1;}
    TextView tv(String s,int size,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextColor(Color.WHITE);v.setTextSize(size);v.setPadding(0,10,0,10);if(bold)v.setTypeface(null,1);return v;}
    Button button(String s){Button b=new Button(this);b.setText(s);return b;}
    LinearLayout.LayoutParams lp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,8,0,8);return p;}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}

    String base(){String s=target==null?"Main LR":String.valueOf(target.getSelectedItem());if("Main LR".equals(s))return "/main/st";return "/bus/"+s.substring(4);}
    int source(){String s=target==null?"Main LR":String.valueOf(target.getSelectedItem());if("Main LR".equals(s))return 72;return 49+Integer.parseInt(s.substring(4));}

    void connectMixer(){
        mixerIp=ip.getText().toString().trim();if(mixerIp.isEmpty()){toast("ใส่ IP M32R ก่อน");return;}
        stopOsc();status.setText("CONNECTING...");status.setTextColor(Color.rgb(251,191,36));arm.setEnabled(false);arm.setChecked(false);
        new Thread(()->{
            try{
                socket=new DatagramSocket();socket.setSoTimeout(700);running=true;
                sendNow("/info");
                byte[] buf=new byte[8192];DatagramPacket r=new DatagramPacket(buf,buf.length);socket.receive(r);
                connected=true;lastRx=System.currentTimeMillis();
                runOnUiThread(()->{status.setText("M32R CONNECTED");status.setTextColor(Color.rgb(52,211,153));arm.setEnabled(true);});
                uiLog("OSC reply received from "+mixerIp+":10023");
                configureNow();snapshotEq();
                new Thread(this::rxLoop,"m32-rx").start();new Thread(this::keepAlive,"m32-keepalive").start();
            }catch(Exception e){runOnUiThread(()->{status.setText("NO RESPONSE");status.setTextColor(Color.rgb(248,113,113));rtaState.setText("RTA: not connected");});uiLog(e.getClass().getSimpleName()+": "+e.getMessage());stopOsc();}
        },"m32-connect").start();
    }

    void stopOsc(){running=false;connected=false;if(socket!=null){try{socket.close();}catch(Exception ignored){}socket=null;}}
    synchronized void sendNow(String addr,Object...args)throws Exception{if(socket==null)return;byte[] d=osc(addr,args);InetAddress h=InetAddress.getByName(mixerIp);socket.send(new DatagramPacket(d,d.length,h,10023));}
    void send(String addr,Object...args){new Thread(()->{try{sendNow(addr,args);}catch(Exception e){if(running)uiLog("Send: "+e.getMessage());}},"m32-tx").start();}

    void configure(){send("/-prefs/rta/source",source());send("/-prefs/rta/pos",0);send("/-prefs/rta/det",1);}
    void configureNow()throws Exception{sendNow("/-prefs/rta/source",source());sendNow("/-prefs/rta/pos",0);sendNow("/-prefs/rta/det",1);}
    void snapshotEq(){snapshot.clear();String b=base();send(b+"/eq/on");for(int i=1;i<=6;i++)for(String p:new String[]{"type","f","g","q"})send(b+"/eq/"+i+"/"+p);}

    void keepAlive(){
        long lastSub=0;
        while(running){
            try{
                long now=System.currentTimeMillis();
                sendNow("/xremote");
                boolean rtaStale=(lastRta==0)||(now-lastRta>1800);
                if(now-lastSub>3000 || rtaStale){
                    configureNow();
                    sendNow("/meters","/meters/15",1);
                    lastSub=now;
                    if(rtaStale && connected)runOnUiThread(()->rtaState.setText("RTA: AUTO-RECONNECT…"));
                }
                Thread.sleep(900);
            }catch(Exception e){if(running)uiLog("KeepAlive: "+e.getMessage());}
        }
    }

    void rxLoop(){byte[] buf=new byte[65535];while(running){try{DatagramPacket p=new DatagramPacket(buf,buf.length);socket.receive(p);lastRx=System.currentTimeMillis();parse(Arrays.copyOf(p.getData(),p.getLength()));}catch(SocketTimeoutException e){if(System.currentTimeMillis()-lastRx>4500)runOnUiThread(()->{status.setText("M32R LINK LOST");status.setTextColor(Color.rgb(248,113,113));arm.setChecked(false);});}catch(Exception e){if(running)uiLog("RX: "+e.getMessage());}}}

    void parse(byte[] d){
        try{
            int[] o={0};String a=readString(d,o),tags=readString(d,o);
            if(a.equals("/meters/15")&&tags.contains("b")){
                int n=ByteBuffer.wrap(d,o[0],4).order(ByteOrder.BIG_ENDIAN).getInt();o[0]+=4;
                if(n>=200&&o[0]+n<=d.length){decodeRta(Arrays.copyOfRange(d,o[0],o[0]+n));lastRta=System.currentTimeMillis();runOnUiThread(()->rtaState.setText("RTA: LIVE • "+String.valueOf(target.getSelectedItem())));}
                return;
            }
            if((a.startsWith(base()+"/eq/")||a.equals(base()+"/eq/on"))&&tags.length()>1){
                Object val=null;if(tags.charAt(1)=='i')val=ByteBuffer.wrap(d,o[0],4).order(ByteOrder.BIG_ENDIAN).getInt();else if(tags.charAt(1)=='f')val=ByteBuffer.wrap(d,o[0],4).order(ByteOrder.BIG_ENDIAN).getFloat();if(val!=null&&!snapshot.containsKey(a))snapshot.put(a,val);
            }
        }catch(Exception ignored){}
    }

    void decodeRta(byte[] b){
        try{int off=b.length>=204?4:0;if(b.length-off<200)return;ByteBuffer bb=ByteBuffer.wrap(b,off,200).order(ByteOrder.LITTLE_ENDIAN);for(int i=0;i<100;i++)rta[i]=bb.getShort()/256f;runOnUiThread(()->spectrum.setData(rta));if(!isFlat())detect();}catch(Exception ignored){}
    }

    void detect(){
        int bi=-1,bc=0;float be=0;
        for(int i=20;i<92;i++){
            if(rta[i]<-48||rta[i]<=rta[i-1]||rta[i]<=rta[i+1])continue;
            float sum=0;int n=0;for(int j=Math.max(0,i-4);j<Math.min(100,i+5);j++)if(Math.abs(j-i)>=2){sum+=rta[j];n++;}
            float ex=rta[i]-sum/n;if(ex<6.5f)continue;float near=Math.max(rta[i-1],rta[i+1]);int c=(int)Math.max(0,Math.min(99,45+ex*4.8+Math.max(0,rta[i]-near)*2+Math.max(0,rta[i]+48)*.7));if(c>bc){bc=c;bi=i;be=ex;}
        }
        if(bi<0){candKey=-1;candStart=0;runOnUiThread(()->candidate.setText("Candidate: —"));return;}
        int f=F[bi],conf=bc;float ex=be,db=rta[bi];int key=(int)Math.round(Math.log(f)/.035);
        if(key!=candKey){candKey=key;candStart=System.currentTimeMillis();return;}
        long held=System.currentTimeMillis()-candStart;runOnUiThread(()->candidate.setText("Candidate: "+f+" Hz • "+conf+"% • "+String.format(Locale.US,"%.1f dB",db)+" • "+held+" ms"));
        if(held>=350&&conf>=92){
            boolean exists=false;for(Notch z:bank)if(Math.abs(Math.log(z.f/(double)f))<.055)exists=true;
            String m=String.valueOf(mode.getSelectedItem());
            if(!exists&&arm.isChecked()&&("AUTO".equals(m)||"RING OUT".equals(m)))applyNotch(f,conf,ex);
            candKey=-1;candStart=0;
        }
    }

    synchronized void applyNotch(int f,int conf,float ex){
        if(bank.size()>=6){uiLog("MAX 6 NOTCHES reached");return;}
        int band=bank.size()+1;float cut=-Math.min(9f,Math.max(3f,Math.round(ex*.65f*4)/4f)),q=Math.min(10f,Math.max(6f,7f+ex*.25f));
        send(base()+"/eq/on",1);send(base()+"/eq/"+band+"/type",2);send(base()+"/eq/"+band+"/f",nlog(f,20,20000));send(base()+"/eq/"+band+"/g",nlin(cut,-15,15));send(base()+"/eq/"+band+"/q",nlog(q,10,.3f));
        bank.add(new Notch(band,f,cut,q,conf));uiLog("AUTO CUT → EQ"+band+"  "+f+" Hz  "+String.format(Locale.US,"%.2f",cut)+" dB  Q "+String.format(Locale.US,"%.1f",q)+"  conf "+conf+"%");runOnUiThread(this::refresh);
    }

    void runFlatEq(){
        if(!connected){toast("ต้อง CONNECT M32R ก่อน");return;}
        if(lastRta==0 || System.currentTimeMillis()-lastRta>2200){toast("RTA ยังไม่พร้อม");return;}
        float[] levels=new float[6];
        for(int b=0;b<6;b++){
            float sum=0;int n=0;
            for(int i=0;i<F.length;i++){
                if(F[i]>=FLAT_RANGE[b][0] && F[i]<FLAT_RANGE[b][1]){
                    float sm=smoothRta(i);sum+=sm;n++;
                }
            }
            levels[b]=n>0?sum/n:-90f;
        }
        float[] sorted=levels.clone();Arrays.sort(sorted);float ref=(sorted[2]+sorted[3])/2f;
        float avg=0;for(float v:levels)avg+=v;avg/=6f;
        if(avg<-72f){toast("ระดับสัญญาณต่ำเกินไปสำหรับ Flat EQ");return;}

        final float[] corr=new float[6];
        StringBuilder s=new StringBuilder("Smooth Flat suggestion\n");
        for(int i=0;i<6;i++){
            float c=ref-levels[i];
            if(Math.abs(c)<0.75f)c=0;
            c=Math.max(-6f,Math.min(3f,c));
            c=Math.round(c*4f)/4f;
            corr[i]=c;
            s.append(FLAT_CENTER[i]).append(" Hz  ").append(String.format(Locale.US,"%+.2f dB",c)).append("\n");
        }
        flatInfo.setText(s.toString()+"Reference "+String.format(Locale.US,"%.1f dB",ref));
        if(!arm.isChecked()){
            uiLog("FLAT EQ analyzed only — ARM OFF");
            toast("วิเคราะห์แล้ว • เปิด ARM FLAT EQ หากต้องการเขียนค่า");
            return;
        }
        new AlertDialog.Builder(this).setTitle("Apply Smooth Flat EQ?").setMessage(s.toString()+"\nจะเขียน PEQ 1-6 ของ "+String.valueOf(target.getSelectedItem())).setPositiveButton("APPLY",(d,w)->applyFlat(corr)).setNegativeButton("ยกเลิก",null).show();
    }

    float smoothRta(int i){
        float sum=0,weight=0;
        for(int k=-2;k<=2;k++){
            int j=Math.max(0,Math.min(99,i+k));float w=(k==0?3f:(Math.abs(k)==1?2f:1f));sum+=rta[j]*w;weight+=w;
        }
        return sum/weight;
    }

    void applyFlat(float[] corr){
        bank.clear();String b=base();send(b+"/eq/on",1);
        for(int i=0;i<6;i++){
            int band=i+1;float q=1.10f;
            send(b+"/eq/"+band+"/type",2);
            send(b+"/eq/"+band+"/f",nlog(FLAT_CENTER[i],20,20000));
            send(b+"/eq/"+band+"/g",nlin(corr[i],-15,15));
            send(b+"/eq/"+band+"/q",nlog(q,10,.3f));
        }
        notchText.setText("FLAT EQ: APPLIED • 6 Smooth Bands");
        uiLog("SMOOTH FLAT EQ applied to "+String.valueOf(target.getSelectedItem())+" • Boost ≤ +3 dB • Cut ≤ -6 dB");
    }

    void undoLast(){
        if(isFlat()){
            restoreEq();return;
        }
        if(bank.isEmpty()){toast("ยังไม่มี Auto Cut");return;}Notch n=bank.remove(bank.size()-1);for(String p:new String[]{"type","f","g","q"}){String a=base()+"/eq/"+n.band+"/"+p;Object v=snapshot.get(a);if(v!=null)send(a,v);}refresh();uiLog("Undo EQ"+n.band+" • "+n.f+" Hz");
    }
    void restoreEq(){if(snapshot.isEmpty()){toast("ยังไม่มี EQ Snapshot — Connect M32R ก่อน");return;}arm.setChecked(false);for(Map.Entry<String,Object> e:snapshot.entrySet())send(e.getKey(),e.getValue());bank.clear();refresh();uiLog("Original EQ snapshot restored");}
    void refresh(){runOnUiThread(()->notchText.setText(isFlat()?"FLAT EQ: Original / Ready":"Auto Notches: "+bank.size()+" / 6"));}
    void uiLog(String s){runOnUiThread(()->{if(log!=null)log.setText(s+"\n"+log.getText());});}
    float nlog(float v,float lo,float hi){return(float)(Math.log(v/lo)/Math.log(hi/lo));}
    float nlin(float v,float lo,float hi){return Math.max(0,Math.min(1,(v-lo)/(hi-lo)));}

    byte[] osc(String a,Object...args)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream(),pay=new ByteArrayOutputStream();StringBuilder tags=new StringBuilder(",");
        for(Object x:args){if(x instanceof Integer){tags.append('i');pay.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt((Integer)x).array());}else if(x instanceof Float){tags.append('f');pay.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putFloat((Float)x).array());}else if(x instanceof String){tags.append('s');pay.write(padString((String)x));}}
        out.write(padString(a));out.write(padString(tags.toString()));out.write(pay.toByteArray());return out.toByteArray();
    }
    byte[] padString(String s)throws Exception{ByteArrayOutputStream o=new ByteArrayOutputStream();o.write(s.getBytes(StandardCharsets.UTF_8));o.write(0);while(o.size()%4!=0)o.write(0);return o.toByteArray();}
    String readString(byte[] d,int[] o){int i=o[0],e=i;while(e<d.length&&d[e]!=0)e++;String s=new String(d,i,e-i,StandardCharsets.UTF_8);o[0]=(e+4)&~3;return s;}

    @Override protected void onDestroy(){stopOsc();super.onDestroy();}

    public static class SpectrumView extends View {
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);float[] data=new float[100];
        SpectrumView(android.content.Context c){super(c);Arrays.fill(data,-90);setBackgroundColor(Color.rgb(5,8,12));}
        void setData(float[] d){data=d.clone();invalidate();}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();p.setStrokeWidth(1);p.setColor(Color.rgb(35,48,65));for(int i=1;i<10;i++){float y=h*i/10f;c.drawLine(0,y,w,y,p);}p.setStrokeWidth(3);p.setColor(Color.rgb(56,189,248));Path path=new Path();for(int i=0;i<100;i++){float x=w*i/99f;float db=Math.max(-90,Math.min(0,data[i]));float y=(-db/90f)*(h-12)+6;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}c.drawPath(path,p);}
    }
}
