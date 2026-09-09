export default async function handler(req,res){
  try{
    const r=await fetch('https://well-ads-nd2dg797r-wellsound25.vercel.app',{cache:'no-store'});
    if(!r.ok) throw new Error('โหลดโปรแกรมเดิมไม่สำเร็จ: '+r.status);
    let html=await r.text();
    const trimmed=html.trim();
    if(!html || html.length<1000 || trimmed==='PLACEHOLDER') throw new Error('โปรแกรมต้นทางไม่สมบูรณ์');

    const patch = String.raw`<script id="welladsProfessionalPatch">
(()=>{
  const KEY='wellads_professional_history_v4';
  const $=id=>document.getElementById(id);
  const clean=v=>String(v||'').replace(/\s+/g,' ').trim();
  const pick=a=>a[Math.floor(Math.random()*a.length)];
  function services(){
    const t=(clean($('req')?.value)+' '+clean($('note')?.value)).toLowerCase();
    const a=[];
    if(/เสียง|เครื่องเสียง|sound|audio/.test(t))a.push('ระบบเสียง');
    if(/ไฟ|แสง|lighting/.test(t))a.push('ระบบแสง');
    if(/เวที|stage|โครงสร้าง/.test(t))a.push('เวที');
    if(/เครื่องดนตรี|วงดนตรี|music|band/.test(t))a.push('เครื่องดนตรี');
    return a;
  }
  function makePost(){
    const s=services();
    const note=clean($('note')?.value);
    const loc=clean($('loc')?.value);
    const req=clean($('req')?.value);
    const focus=s.length?s.join(' + '):'งานที่ได้รับมอบหมาย';
    const where=loc?' | '+loc:'';
    const l1=[
      'อีกหนึ่งผลงานที่ทีม Wellsound24 ได้รับความไว้วางใจให้ดูแล '+focus+where,
      'ส่งมอบอีกหนึ่งงานเรียบร้อย | '+focus+where,
      'อีกหนึ่งงานที่ทีม Wellsound24 ได้ร่วมดูแล | '+focus+where,
      'Wellsound24 ดูแลงานอีกหนึ่งงานเรียบร้อย | '+focus+where,
      'อีกหนึ่งความไว้วางใจที่มอบให้ Wellsound24 | '+focus+where,
      'อีกหนึ่งผลงานจากทีม Wellsound24 | '+focus+where
    ];
    const detail=note
      ?[
        'สำหรับงานนี้ทีม Wellsound24 ดูแล '+focus+' ตามรายละเอียดที่ได้รับมอบหมาย โดยมีรายละเอียดงาน: '+note,
        'งานนี้ทีมงานรับผิดชอบ '+focus+' ตามที่ลูกค้ามอบหมาย รายละเอียดงาน: '+note,
        'Wellsound24 ดูแล '+focus+' สำหรับงานครั้งนี้ โดยดำเนินงานตามรายละเอียด: '+note,
        'ทีมงานดูแล '+focus+' ให้เป็นไปตามรูปแบบของงาน โดยรายละเอียดที่ได้รับมอบหมายคือ '+note
      ]
      :[
        'สำหรับงานนี้ทีม Wellsound24 ดูแล '+focus+' ตามรายละเอียดที่ได้รับมอบหมาย พร้อมดูแลความเรียบร้อยตลอดงาน',
        'งานนี้ทีมงานรับผิดชอบ '+focus+' ตามที่ลูกค้ามอบหมาย และดูแลความพร้อมตลอดช่วงงาน',
        'Wellsound24 ดูแล '+focus+' สำหรับงานครั้งนี้ พร้อมประสานงานและดูแลความเรียบร้อยหน้างาน',
        'ทีมงานดูแล '+focus+' ตามรูปแบบของงานและรายละเอียดที่ได้รับมอบหมาย'
      ];
    const l3=[
      'ขอขอบพระคุณลูกค้าที่ไว้วางใจให้ Wellsound24 ได้ดูแลงานในครั้งนี้ครับ',
      'ขอบพระคุณลูกค้าสำหรับความไว้วางใจที่มอบให้ทีม Wellsound24 ครับ',
      'Wellsound24 ขอขอบพระคุณลูกค้าที่เลือกใช้บริการและไว้วางใจทีมงานของเราครับ',
      'ขอขอบพระคุณลูกค้าที่มอบหมายให้ Wellsound24 เป็นส่วนหนึ่งในการดูแลงานครั้งนี้ครับ',
      'ขอบพระคุณลูกค้าที่ไว้วางใจให้ทีม Wellsound24 ได้ร่วมดูแลงานครั้งนี้ครับ'
    ];
    let history=[];try{history=JSON.parse(localStorage.getItem(KEY)||'[]')}catch(e){}
    let out='';
    for(let i=0;i<80;i++){
      out=pick(l1)+'\n\n'+pick(detail)+'\n\n'+pick(l3);
      if(!history.includes(out)&&!out.includes('มีเรื่องดีๆ มาแชร์'))break;
    }
    history.unshift(out);history=[...new Set(history)].slice(0,50);
    try{localStorage.setItem(KEY,JSON.stringify(history))}catch(e){}
    return out;
  }
  function setCaption(){
    const el=$('caption'); if(!el)return;
    const v=makePost();
    const setter=Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value')?.set;
    if(setter)setter.call(el,v);else el.value=v;
    el.dispatchEvent(new Event('input',{bubbles:true}));
    el.dispatchEvent(new Event('change',{bubbles:true}));
    try{ if(typeof preview==='function') preview(); }catch(e){}
  }
  function attach(){
    const a=$('analyze'),rw=$('rewrite');
    if(a&&!a.dataset.welladsProfessional){
      a.dataset.welladsProfessional='1';
      a.addEventListener('click',()=>setTimeout(setCaption,30));
    }
    if(rw&&!rw.dataset.welladsProfessional){
      rw.dataset.welladsProfessional='1';
      rw.addEventListener('click',()=>setTimeout(setCaption,30));
    }
  }
  attach();
  document.addEventListener('DOMContentLoaded',attach,{once:true});
  setTimeout(attach,300);
})();
<\/script>`;

    html=/<\/body>/i.test(html)?html.replace(/<\/body>/i,patch+'</body>'):html+patch;
    res.setHeader('Cache-Control','no-store, max-age=0');
    res.setHeader('Content-Type','text/html; charset=utf-8');
    return res.status(200).send(html);
  }catch(e){
    return res.status(500).send('SOURCE_PROXY_ERROR: '+(e?.message||e));
  }
}
