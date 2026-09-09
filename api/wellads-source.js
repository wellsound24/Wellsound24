import { gunzipSync } from 'node:zlib';

const SOURCE='https://well-ads-108h1p8ec-wellsound25.vercel.app';

export default async function handler(req,res){
  try{
    const r=await fetch(SOURCE,{cache:'no-store'});
    if(!r.ok) throw new Error('โหลด UI เดิมไม่สำเร็จ: '+r.status);
    const shell=await r.text();
    const m=shell.match(/atob\(\"([A-Za-z0-9+/=]+)\"\)/);
    if(!m) throw new Error('ไม่พบข้อมูล UI เดิม');

    let html=gunzipSync(Buffer.from(m[1],'base64')).toString('utf8');
    if(!html || html.length<1000) throw new Error('UI เดิมไม่สมบูรณ์');

    const patch=String.raw`<script id="wellads-post-fix">
(()=>{
  const KEY='wellads_professional_post_history_v7';
  const clean=v=>String(v||'').replace(/\s+/g,' ').trim();
  const pick=a=>a[Math.floor(Math.random()*a.length)];
  const banned=['มีเรื่องดีๆมาแชร์','มีเรื่องดี ๆ มาแชร์','อยากนำมาแชร์'];

  function fields(){return [...document.querySelectorAll('input,textarea,select')];}
  function around(el){
    const p=el.closest('label,.field,.form-group,.card,section,div');
    return clean((p?.innerText||'')+' '+(el.placeholder||'')+' '+(el.name||'')+' '+(el.id||''));
  }
  function val(rx){
    for(const el of fields()) if(rx.test(around(el))&&clean(el.value)) return clean(el.value);
    return '';
  }
  function caption(){
    let best=null,score=-1;
    for(const el of document.querySelectorAll('textarea,input[type=text]')){
      const t=around(el).toLowerCase(); let s=0;
      if(/ข้อความโพสต์|โพสต์งาน|แคปชั่น|caption|ข้อความฉบับเต็ม|post text/.test(t)) s+=10;
      if(el.tagName==='TEXTAREA') s+=1;
      if(s>score){score=s;best=el;}
    }
    return score>0?best:null;
  }
  function services(t){
    const a=[];
    if(/เสียง|เครื่องเสียง|sound|audio/i.test(t)) a.push('ระบบเสียง');
    if(/ไฟ|แสง|lighting/i.test(t)) a.push('ระบบแสง');
    if(/เวที|stage|โครงสร้าง/i.test(t)) a.push('เวที');
    if(/เครื่องดนตรี|วงดนตรี|music|band/i.test(t)) a.push('เครื่องดนตรี');
    return a;
  }
  function make(){
    const job=val(/ประเภทงาน|ชื่องาน|งานที่ไปทำ|ลักษณะงาน/i);
    const loc=val(/สถานที่|พื้นที่|จังหวัด|อำเภอ|location/i);
    const note=val(/รายละเอียดงาน|รายละเอียดเพิ่มเติม|บริการที่ใช้|บริการที่ให้|สิ่งที่ทำ|อุปกรณ์/i);
    const req=val(/บอก ai|ต้องการให้ทำอะไร|คำสั่ง|โทนข้อความ/i);
    const sv=services([job,note,req].join(' '));
    const focus=sv.length?sv.join(' + '):(job||'งานอีเวนต์');
    const where=loc?' | '+loc:'';

    const l1=[
      'ส่งมอบอีกหนึ่งงานเรียบร้อย | '+focus+where,
      'อีกหนึ่งผลงานที่ทีม Wellsound24 ได้รับความไว้วางใจให้ดูแล | '+focus+where,
      'อีกหนึ่งงานที่ Wellsound24 ได้ร่วมดูแล | '+focus+where,
      'Wellsound24 ดูแลงานอีกหนึ่งงานเรียบร้อย | '+focus+where,
      'อีกหนึ่งผลงานจากทีม Wellsound24 | '+focus+where
    ];
    const l2=note?[
      'งานนี้ทีม Wellsound24 ดูแล '+focus+' ตามรายละเอียดที่ได้รับมอบหมาย โดยมีรายละเอียดงาน: '+note,
      'สำหรับงานนี้ทีมงานรับผิดชอบ '+focus+' และดูแลหน้างานตามรายละเอียด: '+note,
      'Wellsound24 ดูแล '+focus+' ให้เหมาะกับรูปแบบของงาน โดยรายละเอียดที่ได้รับมอบหมายคือ '+note,
      'ครั้งนี้ทีมงานดูแล '+focus+' พร้อมดำเนินงานตามรายละเอียด: '+note
    ]:[
      'งานนี้ทีม Wellsound24 ดูแล '+focus+' ตามรายละเอียดที่ได้รับมอบหมาย พร้อมดูแลความเรียบร้อยตลอดงาน',
      'สำหรับงานนี้ทีมงานรับผิดชอบ '+focus+' และดูแลความพร้อมตลอดช่วงงาน',
      'Wellsound24 ดูแล '+focus+' สำหรับงานครั้งนี้ พร้อมประสานงานและดูแลความเรียบร้อยหน้างาน',
      'ทีมงานดูแล '+focus+' ให้เหมาะกับรูปแบบของงานและการใช้งานจริง'
    ];
    const l3=[
      'ขอขอบพระคุณลูกค้าที่ไว้วางใจให้ Wellsound24 ดูแลงานในครั้งนี้ครับ',
      'ขอบพระคุณลูกค้าสำหรับความไว้วางใจที่มอบให้ทีม Wellsound24 ครับ',
      'Wellsound24 ขอขอบพระคุณลูกค้าที่เลือกใช้บริการและไว้วางใจทีมงานของเราครับ',
      'ขอขอบพระคุณลูกค้าที่มอบหมายให้ Wellsound24 เป็นส่วนหนึ่งในการดูแลงานครั้งนี้ครับ',
      'ขอบพระคุณลูกค้าที่ไว้วางใจให้ทีม Wellsound24 ได้ร่วมดูแลงานครั้งนี้ครับ'
    ];

    let history=[]; try{history=JSON.parse(localStorage.getItem(KEY)||'[]')}catch(e){}
    let out='';
    for(let i=0;i<100;i++){
      out=pick(l1)+'\n\n'+pick(l2)+'\n\n'+pick(l3);
      if(!history.includes(out)&&!banned.some(x=>out.includes(x))) break;
    }
    history.unshift(out);
    try{localStorage.setItem(KEY,JSON.stringify([...new Set(history)].slice(0,50)))}catch(e){}
    return out;
  }
  function setNative(el,v){
    const proto=el.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;
    const setter=Object.getOwnPropertyDescriptor(proto,'value')?.set;
    if(setter) setter.call(el,v); else el.value=v;
    el.dispatchEvent(new Event('input',{bubbles:true}));
    el.dispatchEvent(new Event('change',{bubbles:true}));
  }
  function run(){const el=caption(); if(el) setNative(el,make());}
  function attach(){
    for(const b of document.querySelectorAll('button')){
      const t=clean(b.innerText);
      if(/ai/i.test(t)&&/(เจน|เขียน|สร้าง|วิเคราะห์|ใหม่|generate)/i.test(t)&&!b.dataset.welladsPostFix){
        b.dataset.welladsPostFix='1';
        b.addEventListener('click',()=>setTimeout(run,60));
      }
    }
  }
  attach();
  document.addEventListener('DOMContentLoaded',attach,{once:true});
  setTimeout(attach,300); setTimeout(attach,1000);
  new MutationObserver(attach).observe(document.documentElement,{childList:true,subtree:true});
})();
<\/script>`;

    html=/<\/body>/i.test(html)?html.replace(/<\/body>/i,patch+'</body>'):html+patch;

    res.setHeader('Content-Type','text/html; charset=utf-8');
    res.setHeader('Cache-Control','no-store, max-age=0');
    return res.status(200).send(html);
  }catch(e){
    res.setHeader('Content-Type','text/plain; charset=utf-8');
    res.setHeader('Cache-Control','no-store');
    return res.status(500).send('SOURCE_ERROR: '+(e?.message||e));
  }
}
