export default async function handler(req,res){
  try{
    const r=await fetch('https://well-ads-108h1p8ec-wellsound25.vercel.app',{cache:'no-store'});
    const text=await r.text();
    res.setHeader('Cache-Control','no-store');
    res.setHeader('Content-Type','text/html; charset=utf-8');
    return res.status(r.status).send(text);
  }catch(e){
    return res.status(500).send('SOURCE_PROXY_ERROR: '+(e?.message||e));
  }
}
