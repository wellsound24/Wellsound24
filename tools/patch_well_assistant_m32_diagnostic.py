from pathlib import Path
p=Path('well-assistant-pro-android/app/src/main/assets/index.html')
s=p.read_text()
if 'M32_DIAGNOSTIC_PANEL_V1' not in s:
    addon=r'''<!-- M32_DIAGNOSTIC_PANEL_V1 -->
<style>#m32Diag{position:fixed;z-index:9999;left:10px;right:10px;bottom:10px;background:#0c141d;border:1px solid #29445a;border-radius:12px;padding:10px;color:#dce8f2;font:12px system-ui;display:none;box-shadow:0 8px 30px #0008}#m32Diag .r{display:flex;justify-content:space-between;padding:3px 0;border-bottom:1px solid #182837}#m32Diag b{color:#45b7ef}#m32Diag .bad{color:#ff6b6b}#m32Diag .good{color:#62d28f}#m32Diag button{margin-top:8px;padding:8px 10px;border:0;border-radius:8px;background:#1c7eaf;color:white}</style>
<div id="m32Diag"><div style="font-weight:800;font-size:14px;margin-bottom:5px">M32 LIVE DIAGNOSTIC</div><div class="r"><span>OSC /info</span><b id="dgInfo">WAIT</b></div><div class="r"><span>Meter packets</span><b id="dgPk">0</b></div><div class="r"><span>Last meter update</span><b id="dgAge">--</b></div><div class="r"><span>CH01 real dB</span><b id="dgCh1">--</b></div><div class="r"><span>Result</span><b id="dgResult">WAITING</b></div><button onclick="document.getElementById('m32Diag').style.display='none'">CLOSE</button></div>
<script>
(function(){let pk=0,last=0;let oldM=window.onM32Meters,oldC=window.onM32Connect;
window.onM32Connect=function(ok,msg){if(oldC)oldC(ok,msg);let d=document.getElementById('m32Diag');if(d)d.style.display='block';let x=document.getElementById('dgInfo');if(x){x.textContent=ok?'REPLY OK':'NO REPLY';x.className=ok?'good':'bad'}let r=document.getElementById('dgResult');if(r)r.textContent=ok?'CONNECTED — WAITING METER':'OSC CONNECT FAILED'};
window.onM32Meters=function(v){pk++;last=Date.now();if(oldM)oldM(v);let a=document.getElementById('dgPk'),c=document.getElementById('dgCh1'),r=document.getElementById('dgResult');if(a)a.textContent=pk;if(c)c.textContent=(v&&v.length)?Number(v[0]).toFixed(1)+' dB':'NO VALUE';if(r){r.textContent=(v&&v.length)?'REAL METER RECEIVED':'PACKET DECODE FAILED';r.className=(v&&v.length)?'good':'bad'}};
setInterval(()=>{let a=document.getElementById('dgAge'),r=document.getElementById('dgResult');if(!a)return;if(!last){a.textContent='NO PACKET';return}let age=(Date.now()-last)/1000;a.textContent=age.toFixed(1)+' s';if(age>2&&r){r.textContent='METER STREAM STOPPED';r.className='bad'}},500);
window.addEventListener('load',()=>setTimeout(()=>{let d=document.getElementById('m32Diag');if(d)d.style.display='block'},1200));})();
</script>'''
    s=s.replace('</body>',addon+'</body>')
p.write_text(s)
