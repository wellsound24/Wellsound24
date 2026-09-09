const ALLOWED_HOSTS = [
  'hook.eu1.make.com','hook.us1.make.com','hook.us2.make.com',
  'hooks.zapier.com','webhook.site'
];

function cors(res){
  res.setHeader('Access-Control-Allow-Origin','*');
  res.setHeader('Access-Control-Allow-Methods','GET,POST,OPTIONS');
  res.setHeader('Access-Control-Allow-Headers','Content-Type');
}

function isAllowedWebhook(raw){
  try{
    const u = new URL(raw);
    if(u.protocol !== 'https:') return false;
    const h = u.hostname.toLowerCase();
    return ALLOWED_HOSTS.includes(h) || h.endsWith('.m.pipedream.net');
  }catch{return false;}
}

module.exports = async function handler(req,res){
  cors(res);
  if(req.method === 'OPTIONS') return res.status(204).end();
  if(req.method === 'GET') return res.status(200).json({ok:true,service:'Well Ads Gateway',version:'1.0.0'});
  if(req.method !== 'POST') return res.status(405).json({ok:false,error:'Method not allowed'});

  try{
    const body = typeof req.body === 'string' ? JSON.parse(req.body || '{}') : (req.body || {});
    const webhookUrl = String(body.webhookUrl || '').trim();
    const payload = body.payload || {};
    if(!isAllowedWebhook(webhookUrl)){
      return res.status(400).json({ok:false,error:'Webhook URL is not an allowed provider. Supported: Make, Zapier, Pipedream.'});
    }

    const controller = new AbortController();
    const timeout = setTimeout(()=>controller.abort(),12000);
    let upstream;
    try{
      upstream = await fetch(webhookUrl,{
        method:'POST',
        headers:{'Content-Type':'application/json','User-Agent':'WellAdsGateway/1.0'},
        body:JSON.stringify({
          source:'well-ads-gateway',
          version:'1.0.0',
          sentAt:new Date().toISOString(),
          ...payload
        }),
        signal:controller.signal
      });
    }finally{clearTimeout(timeout);}

    const text = await upstream.text();
    return res.status(upstream.ok ? 200 : 502).json({
      ok:upstream.ok,
      status:upstream.status,
      response:text.slice(0,2000)
    });
  }catch(err){
    return res.status(500).json({ok:false,error:err && err.message ? err.message : 'Gateway error'});
  }
};