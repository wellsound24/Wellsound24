from pathlib import Path

HTML=Path('well-assistant-pro-android/app/src/main/assets/index.html')
s=HTML.read_text()
marker='REAL_OVERVIEW_METER_ONLY_V1'
if marker not in s:
    s=s.replace("db:-60+Math.random()*28", "db:-Infinity")
    s=s.replace("function animate32Meters(){if(target==='sim'&&simPowered){channelStates.forEach(c=>{c.db+= (Math.random()-.5)*5;c.db=Math.max(-68,Math.min(-8,c.db));if(Math.random()<.004)c.fb=!c.fb});render32Channels()}setTimeout(animate32Meters,700)}", "function animate32Meters(){setTimeout(animate32Meters,700)}")
    addon=r'''<!-- REAL_OVERVIEW_METER_ONLY_V1 -->
<script id="realOverviewMeterOnly">
(function(){
  const previous=window.onM32Meters;
  window.onM32Meters=function(values){
    try{
      if(Array.isArray(values)&&window.channelStates){
        for(let i=0;i<32;i++){
          const v=Number(values[i]);
          channelStates[i].db=Number.isFinite(v)?v:-Infinity;
        }
        if(typeof render32Channels==='function')render32Channels();
      }
    }catch(e){}
    if(previous)previous(values);
  };
  function clearFake(){
    try{
      if(window.channelStates){channelStates.forEach(c=>c.db=-Infinity);if(typeof render32Channels==='function')render32Channels();}
    }catch(e){}
  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',clearFake);else clearFake();
})();
</script>'''
    s=s.replace('</body>',addon+'</body>')
    HTML.write_text(s)
