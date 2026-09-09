export default async function handler(req,res){
  try{
    const r=await fetch('https://well-ads-ctmjmbdhw-wellsound25.vercel.app',{cache:'no-store'});
    const html=await r.text();
    const marker='document.close()})().catch';
    if(!html.includes(marker)){
      res.setHeader('Content-Type','text/plain; charset=utf-8');
      return res.status(500).send('DEBUG status='+r.status+' len='+html.length+' prefix='+html.slice(0,500));
    }
    return res.status(200).send(html);
  }catch(e){return res.status(500).send('ERR '+(e?.message||e));}
}
