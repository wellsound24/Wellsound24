const SOURCE='https://well-ads-108h1p8ec-wellsound25.vercel.app/?_vercel_share=R1LDbzKnXF1xY1TsDFxEPAC389UqhOCz';

async function fetchWithCookies(url){
  let current=url;
  const jar=new Map();
  for(let i=0;i<10;i++){
    const cookie=[...jar.entries()].map(([k,v])=>`${k}=${v}`).join('; ');
    const r=await fetch(current,{redirect:'manual',cache:'no-store',headers:cookie?{cookie}:undefined});
    const raw=r.headers.getSetCookie?.() || (r.headers.get('set-cookie')?[r.headers.get('set-cookie')]:[]);
    for(const line of raw){
      const first=String(line).split(';',1)[0];
      const p=first.indexOf('=');
      if(p>0) jar.set(first.slice(0,p),first.slice(p+1));
    }
    const loc=r.headers.get('location');
    if(r.status>=300&&r.status<400&&loc){
      current=new URL(loc,current).toString();
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
    const html=await r.text();
    if(!html || html.length<1000 || !html.includes('DecompressionStream')) throw new Error('ต้นทางที่ได้ไม่ใช่โปรแกรมเดิม');
    res.setHeader('Content-Type','text/html; charset=utf-8');
    res.setHeader('Cache-Control','public, max-age=0, s-maxage=31536000, stale-while-revalidate=86400');
    return res.status(200).send(html);
  }catch(e){
    res.setHeader('Content-Type','text/plain; charset=utf-8');
    res.setHeader('Cache-Control','no-store');
    return res.status(500).send('SOURCE_PROXY_ERROR: '+(e?.message||e));
  }
}
