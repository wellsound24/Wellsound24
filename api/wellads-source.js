export default async function handler(req,res){
  try{
    const r=await fetch('https://well-ads-nd2dg797r-wellsound25.vercel.app',{cache:'no-store'});
    if(!r.ok) throw new Error('โหลดโปรแกรมเดิมไม่สำเร็จ: '+r.status);
    const html=await r.text();
    const trimmed=html.trim();
    if(!html || html.length<1000 || trimmed==='PLACEHOLDER' || /^<!doctype html><html><head>.*<body>placeholder<\/body><\/html>$/is.test(trimmed)) throw new Error('โปรแกรมต้นทางไม่สมบูรณ์');
    res.setHeader('Cache-Control','no-store, max-age=0');
    res.setHeader('Content-Type','text/html; charset=utf-8');
    return res.status(200).send(html);
  }catch(e){
    return res.status(500).send('SOURCE_PROXY_ERROR: '+(e?.message||e));
  }
}
