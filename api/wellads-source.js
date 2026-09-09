import { gunzipSync } from 'node:zlib';

export default async function handler(req,res){
  try{
    const r=await fetch('https://well-ads-108h1p8ec-wellsound25.vercel.app',{cache:'no-store'});
    if(!r.ok) throw new Error('โหลดโปรแกรมเดิมไม่สำเร็จ: '+r.status);
    const shell=await r.text();
    const m=shell.match(/atob\("([A-Za-z0-9+/=]+)"\)/);
    if(!m) throw new Error('ไม่พบข้อมูลโปรแกรมเดิม');
    const inner=gunzipSync(Buffer.from(m[1],'base64')).toString('utf8');
    res.setHeader('Cache-Control','no-store, max-age=0');
    res.setHeader('Content-Type','text/html; charset=utf-8');
    return res.status(200).send(inner);
  }catch(e){
    return res.status(500).send('SOURCE_PROXY_ERROR: '+(e?.message||e));
  }
}
