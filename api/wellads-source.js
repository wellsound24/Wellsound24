export default async function handler(req,res){
  try{
    // ใช้ deployment เดิมจริงทั้งก้อน เพื่อคง UI / ฟอนต์ / สี / layout เดิม 100%
    const r=await fetch('https://well-ads-ctmjmbdhw-wellsound25.vercel.app',{cache:'no-store'});
    if(!r.ok) throw new Error('โหลดโปรแกรมเดิมไม่สำเร็จ: '+r.status);
    let html=await r.text();
    if(!html || html.length<1000) throw new Error('โปรแกรมต้นทางไม่สมบูรณ์');

    // Patch เฉพาะ logic เจนข้อความหลังจากหน้าเดิมถูกคลาย gzip และ render เสร็จแล้ว
    const patchCode = String.raw`
(()=>{
  const KEY='wellads_professional_history_v5';
  const clean=v=>String(v||'').replace(/\s+/g,' ').trim();
  const pick=a=>a[Math.floor(Math.random()*a.length)];
  const banned=['มีเรื่องดีๆมาแชร์','มีเรื่องดี ๆ มาแชร์','อยากนำมาแชร์'];

  function allFields(){
    return [...document.querySelectorAll('input,textarea,select')];
  }
  function textAround(el){
    const p=el.closest('label,.field,.form-group,.card,section,div');
    return clean((p?.innerText||'')+' '+(el.placeholder||'')+' '+(el.name||'')+' '+(el.id||''));
  }
  function valueBy(rx){
    for(const el of allFields()){
      if(rx.test(textAround(el)) && clean(el.value)) return clean(el.value);
    }
    return '';
  }
  function findCaption(){
    const els=[...document.querySelectorAll('textarea,input[type=text]')];
    let best=null,score=-1;
    for(const el of els){
      const t=textAround(el).toLowerCase();
      let s=0;
      if(/ข้อความโพสต์|โพสต์งาน|แคปชั่น|caption|ข้อความฉบับเต็ม/.test(t)) s+=10;
      if(/ผลลัพธ์|preview|ตัวอย่าง/.test(t)) s+=2;
      if((el.tagName==='TEXTAREA')) s+=1;
      if(s>score){score=s;best=el;}
    }
    return score>0?best:null;
  }
  function servicesFrom(text){
    const a=[];
    if(/เสียง|เครื่องเสียง|sound|audio/i.test(text))a.push('ระบบเสียง');
    if(/ไฟ|แสง|lighting/i.test(text))a.push('ระบบแสง');
    if(/เวที|stage|โครงสร้าง/i.test(text))a.push('เวที');
    if(/เครื่องดนตรี|วงดนตรี|music|band/i.test(text))a.push('เครื่องดนตรี');
    return a;
  }
  function makePost(){
    const job=valueBy(/ประเภทงาน|ชื่องาน|งานที่ไปทำ|ลักษณะงาน/i);
    const loc=valueBy(/สถานที่|พื้นที่|จังหวัด|อำเภอ|location/i);
    const note=valueBy(/รายละเอียดงาน|รายละเอียดเพิ่มเติม|บริการที่ใช้|บริการที่ให้|สิ่งที่ทำ|อุปกรณ์/i);
    const req=valueBy(/บอก ai|ต้องการให้ทำอะไร|โทนข้อความ|คำสั่ง/i);
    const joined=[job,note,req].filter(Boolean).join(' ');
    const sv=servicesFrom(joined);
    const focus=sv.length?sv.join(' + '):(job||'งานอีเวนต์');
    const where=loc?' | '+loc:'';

    const l1=[
      'ส่งมอบอีกหนึ่งงานเรียบร้อย | '+focus+where,
      'อีกหนึ่งผลงานที่ทีม Wellsound24 ได้รับความไว้วางใจให้ดูแล | '+focus+where,
      'อีกหนึ่งงานที่ Wellsound24 ได้ร่วมดูแล | '+focus+where,
      'Wellsound24 ดูแลงานอีกหนึ่งงานเรียบร้อย | '+focus+where,
      'อีกหนึ่งความไว้วางใจที่มอบให้ทีม Wellsound24 | '+focus+where
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
    for(let i=0;i<80;i++){
      out=pick(l1)+'\n\n'+pick(l2)+'\n\n'+pick(l3);
      if(!history.includes(out) && !banned.some(x=>out.includes(x))) break;
    }
    history.unshift(out); history=[...new Set(history)].slice(0,50);
    try{localStorage.setItem(KEY,JSON.stringify(history))}catch(e){}
    return out;
  }
  function setNative(el,v){
    const proto=el.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;
    const setter=Object.getOwnPropertyDescriptor(proto,'value')?.set;
    if(setter)setter.call(el,v); else el.value=v;
    el.dispatchEvent(new Event('input',{bubbles:true}));
    el.dispatchEvent(new Event('change',{bubbles:true}));
  }
  function overwriteCaption(){
    const el=findCaption();
    if(!el) return;
    setNative(el,makePost());
  }
  function attach(){
    const buttons=[...document.querySelectorAll('button')];
    for(const b of buttons){
      const t=clean(b.innerText).toLowerCase();
      if(/ai/.test(t) && /(เจน|เขียน|สร้าง|วิเคราะห์|ใหม่|generate)/i.test(t) && !b.dataset.welladsProfessional){
        b.dataset.welladsProfessional='1';
        b.addEventListener('click',()=>setTimeout(overwriteCaption,80));
      }
    }
  }
  attach();
  setTimeout(attach,500);
  setTimeout(attach,1500);
  new MutationObserver(()=>attach()).observe(document.documentElement,{childList:true,subtree:true});
})();`;

    // deployment เดิมเป็น self-contained gzip loader: แทรก patch หลัง document.close() โดยไม่แตะ HTML/CSS เดิม
    const marker='document.close()})().catch';
    if(html.includes(marker)){
      const safe=patchCode.replace(/<\/script/gi,'<\\/script');
      html=html.replace(marker,`document.close();setTimeout(()=>{try{(0,eval)(${JSON.stringify(safe)})}catch(e){console.error(e)}},120)})().catch`);
    } else {
      throw new Error('ไม่พบจุดโหลด UI เดิม');
    }

    res.setHeader('Cache-Control','no-store, max-age=0');
    res.setHeader('Content-Type','text/html; charset=utf-8');
    return res.status(200).send(html);
  }catch(e){
    return res.status(500).send('SOURCE_PROXY_ERROR: '+(e?.message||e));
  }
}
