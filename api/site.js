import {readFileSync} from 'node:fs';
import {join} from 'node:path';
const escape=s=>String(s||'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
export default async function handler(req,res){
 let html=readFileSync(join(process.cwd(),'site-template.html'),'utf8');
 try{
 const response=await fetch('https://sjcxywxixgrpdgeqaepk.supabase.co/functions/v1/wellsound24-control',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action:'public'}),signal:AbortSignal.timeout(5000)});
 if(!response.ok)throw new Error('Content unavailable');
 const {published}=await response.json();
 if(published){const slug=String(req.query?.page||'home'),page=published.pages.find(p=>p.slug===slug);if(!page){res.statusCode=404;res.setHeader('Content-Type','text/html; charset=utf-8');return res.end('<!doctype html><html lang="th"><meta charset="utf-8"><title>ไม่พบหน้า</title><h1>ไม่พบหน้าที่ต้องการ</h1><a href="/">กลับหน้าแรก</a></html>');}
 html=html.replace(/<title>[^<]*<\/title>/,`<title>${escape(page.seoTitle||page.title)}</title>`).replace(/(<meta name="description" content=")[^"]*/,(_,p)=>p+escape(page.description)).replace(/(<meta property="og:title" content=")[^"]*/,(_,p)=>p+escape(page.seoTitle||page.title)).replace(/(<meta property="og:description" content=")[^"]*/,(_,p)=>p+escape(page.description));
 const origin='https://wellsound24.vercel.app';const image=page.image?new URL(page.image,origin).href:origin+'/assets/hero-stage.png';
 html=html.replace('</head>',`<meta property="og:image" content="${escape(image)}"><link rel="canonical" href="${origin}/${slug==='home'?'':'?page='+encodeURIComponent(slug)}"><script id="w24-published" type="application/json">${JSON.stringify(published).replace(/</g,'\\u003c')}</script></head>`);
 }
 }catch(e){console.error('Website content fallback:',e.message);}
 res.setHeader('Content-Type','text/html; charset=utf-8');res.setHeader('Cache-Control','no-store');res.statusCode=200;res.end(html);
}
