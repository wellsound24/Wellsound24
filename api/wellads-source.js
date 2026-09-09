const SOURCE='https://well-ads-ctmjmbdhw-wellsound25.vercel.app/?_vercel_share=CB6kaReA53ve319Lv5qRco2oAcjRX3Y0';

async function fetchWithCookies(url){
  let current=url;
  const jar=new Map();
  for(let i=0;i<8;i++){
    const cookie=[...jar.entries()].map(([k,v])=>`${k}=${v}`).join('; ');
    const r=await fetch(current,{redirect:'manual',cache:'no-store',headers:cookie?{cookie}:undefined});
    const set=r.headers.getSetCookie?.() || (r.headers.get('set-cookie')?[r.headers.get('set-cookie')]:[]);
    for(const line of set){
      const first=String(line).split(';',1)[0];
      const p=first.indexOf('=');
      if(p>0) jar.set(first.slice(0,p),first.slice(p+1));
    }
    if(r.status>=300&&r.status<400&&r.headers.get('location')){
      current=new URL(r.headers.get('location'),current).toString();
      continue;
    }
    return r;
  }
  throw new Error('redirect มากเกินไป');
}

export default async function handler(req,res){
  try{
    const r=await fetchWithCookies(SOURCE);
    if(!r.ok) throw new Error('โหลด UI เดิมไม่สำเร็จ: '+r.status);
    let html=await r.text();
    const marker='document.close()})().catch';
    if(!html.includes(marker)) throw new Error('ต้นทางที่ได้ไม่ใช่ UI เดิม');

    const patchCode=String.raw`
(()=>{
  const KEY='wellads_professional_history_v6';
  const clean=v=>String(v||'').replace(/\s+/g,' ').trim();
  const pick=a=>a[Math.floor(Math.random()*a.length)];
  const banned=['มีเรื่องดีๆมาแชร์','มีเรื่องดี ๆ มาแชร์','อยากนำมาแชร์'];
  function fields(){return [...document.querySelectorAll('input,textarea,select')];}
  function around(el){const p=el.closest('label,.field,.form-group,.card,section,div');return clean((p?.innerText||'')+' '+(el.placeholder||'')+' '+(el.name||'')+' '+(el.id||''));}
  function val(rx){for(const el of fields()){if(rx.test(around(el))&&clean(el.value))return clean(el.value)}return ''}
  function caption(){let best=null,score=-1;for(const el of document.querySelectorAll('textarea,input[type=text]')){const t=around(el).toLowerCase();let s=0;if(/ข้อความโพสต์|โพสต์งาน|แคปชั่น|caption|ข้อความฉบับเต็ม/.test(t))s+=10;if(el.tagName==='TEXTAREA')s++;if(s>score){score=s;best=el}}return score>0?best:null}
  function services(t){const a=[];if(/เสียง|เครื่องเสียง|sound|audio/i.test(t))a.push('ระบบเสียง');if(/ไฟ|แสง|lighting/i.test(t))a.push('ระบบแสง');if(/เวที|stage|โครงสร้าง/i.test(t))a.push('เวที');if(/เครื่องดนตรี|วงดนตรี|music|band/i.test(t))a.push('เครื่องดนตรี');return a}
  function make(){
    const job=val(/ประเภทงาน|ชื่องาน|งานที่ไปทำ|ลักษณะงาน/i),loc=val(/สถานที่|พื้นที่|จังหวัด|อำเภอ|location/i),note=val(/รายละเอียดงาน|รายละเอียดเพิ่มเติม|บริการที่ใช้|บริการที่ให้|สิ่งที่ทำ|อุปกรณ์/i),req=val(/บอก ai|ต้องการให้ทำอะไร|โทนข้อความ|คำสั่ง/i);
    const sv=services([job,note,req].join(' ')),focus=sv.length?sv.join(' + '):(job||'งานอีเวนต์'),where=loc?' | '+loc:'';
    const a=['ส่งมอบอีกหนึ่งงานเรียบร้อย | '+focus+where,'อีกหนึ่งผลงานที่ทีม Wellsound24 ได้รับความไว้วางใจให้ดูแล | '+focus+where,'อีกหนึ่งงานที่ Wellsound24 ได้ร่วมดูแล | '+focus+where,'Wellsound24 ดูแลงานอีกหนึ่งงานเรียบร้อย | '+focus+where];
    const b=note?['งานนี้ทีม Wellsound24 ดูแล '+focus+' ตามรายละเอียดที่ได้รับมอบหมาย โดยมีรายละเอียดงาน: '+note,'สำหรับงานนี้ทีมงานรับผิดชอบ '+focus+' และดูแลหน้างานตามรายละเอียด: '+note,'Wellsound24 ดูแล '+focus+' ให้เหมาะกับรูปแบบของงาน โดยรายละเอียดที่ได้รับมอบหมายคือ '+note]:['งานนี้ทีม Wellsound24 ดูแล '+focus+' ตามรายละเอียดที่ได้รับมอบหมาย พร้อมดูแลความเรียบร้อยตลอดงาน','สำหรับงานนี้ทีมงานรับผิดชอบ '+focus+' และดูแลความพร้อมตลอดช่วงงาน','Wellsound24 ดูแล '+focus+' สำหรับงานครั้งนี้ พร้อมประสานงานและดูแลความเรียบร้อยหน้างาน'];
    const c=['ขอขอบพระคุณลูกค้าที่ไว้วางใจให้ Wellsound24 ดูแลงานในครั้งนี้ครับ','ขอบพระคุณลูกค้าสำหรับความไว้วางใจที่มอบให้ทีม Wellsound24 ครับ','Wellsound24 ขอขอบพระคุณลูกค้าที่เลือกใช้บริการและไว้วางใจทีมงานของเราครับ','ขอขอบพระคุณลูกค้าที่มอบหมายให้ Wellsound24 เป็นส่วนหนึ่งในการดูแลงานครั้งนี้ครับ'];
    let h=[];try{h=JSON.parse(localStorage.getItem(KEY)||'[]')}catch(e){} let out='';
    for(let i=0;i<80;i++){out=pick(a)+'\n\n'+pick(b)+'\n\n'+pick(c);if(!h.includes(out)&&!banned.some(x=>out.includes(x)))break}
    h.unshift(out);try{localStorage.setItem(KEY,JSON.stringify([...new Set(h)].slice(0,50)))}catch(e){} return out;
  }
  function set(el,v){const p=el.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype,s=Object.getOwnPropertyDescriptor(p,'value')?.set;if(s)s.call(el,v);else el.value=v;el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}))}
  function run(){const el=caption();if(el)set(el,make())}
  function attach(){for(const btn of document.querySelectorAll('button')){const t=clean(btn.innerText);if(/ai/i.test(t)&&/(เจน|เขียน|สร้าง|วิเคราะห์|ใหม่|generate)/i.test(t)&&!btn.dataset.welladsProfessional){btn.dataset.welladsProfessional='1';btn.addEventListener('click',()=>setTimeout(run,80))}}}
  attach();setTimeout(attach,500);setTimeout(attach,1500);new MutationObserver(attach).observe(document.documentElement,{childList:true,subtree:true});
})();`;

    const safe=patchCode.replace(/<\/script/gi,'<\\/script');
    html=html.replace(marker,`document.close();setTimeout(()=>{try{(0,eval)(${JSON.stringify(safe)})}catch(e){console.error(e)}},150)})().catch`);

    res.setHeader('Content-Type','text/html; charset=utf-8');
    res.setHeader('Cache-Control','public, max-age=0, s-maxage=31536000, stale-while-revalidate=86400');
    return res.status(200).send(html);
  }catch(e){
    res.setHeader('Content-Type','text/plain; charset=utf-8');
    return res.status(500).send('SOURCE_PROXY_ERROR: '+(e?.message||e));
  }
}
