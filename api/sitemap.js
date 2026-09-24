import {ENDPOINT,esc} from '../control-shared.js';
import {pageUrl} from '../seo-shared.js';
export default async function handler(req,res){
 res.setHeader('Content-Type','application/xml; charset=utf-8');res.setHeader('Cache-Control','no-store');
 try{const r=await fetch(ENDPOINT,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action:'public'}),signal:AbortSignal.timeout(8000)});if(!r.ok)throw new Error('Unavailable');const {published}=await r.json();
 const pages=published?.pages||[{slug:'home'}];res.statusCode=200;res.end('<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">'+pages.filter(p=>!p.seo?.noindex&&(!p.seo?.canonical||p.seo.canonical===pageUrl(p))).map(p=>'<url><loc>'+esc(pageUrl(p))+'</loc></url>').join('')+'</urlset>');
 }catch{res.statusCode=503;res.setHeader('Retry-After','60');res.end('<error>Content temporarily unavailable</error>');}
}
