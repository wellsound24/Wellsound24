import {readFileSync} from 'node:fs';
import {join} from 'node:path';
import {parseHTML} from 'linkedom';
import {renderContent} from '../content-render.js';
import {ENDPOINT} from '../control-shared.js';
export function renderPublished(template,published,slug='home'){
 const {document}=parseHTML(template);
 const main=document.querySelector('main'),original=main.innerHTML;
 const baseSections=new Map([...main.querySelectorAll('section[data-cc]')].map(e=>[e.dataset.cc,e.outerHTML]));
 renderContent(document,published,slug,original,baseSections);
 const source=document.createElement('template');source.id='w24-original';source.innerHTML=original;document.body.append(source);
 const data=document.createElement('script');data.id='w24-published';data.type='application/json';data.textContent=JSON.stringify(published).replace(/</g,'\\u003c');document.head.append(data);
 return document.toString();
}
export default async function handler(req,res){
 let html=readFileSync(join(process.cwd(),'site-template.html'),'utf8');
 const slug=String(req.query?.page||'home');
 res.setHeader('Content-Type','text/html; charset=utf-8');res.setHeader('Cache-Control','no-store');
 try{
  const response=await fetch(ENDPOINT,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action:'public'}),signal:AbortSignal.timeout(8000)});
  if(!response.ok)throw new Error('Content unavailable');
  const {published}=await response.json();
  if(published){if(!published.pages.some(p=>p.slug===slug)){res.statusCode=404;return res.end('<!doctype html><html lang="th"><meta charset="utf-8"><meta name="robots" content="noindex"><title>ไม่พบหน้า</title><h1>ไม่พบหน้าที่ต้องการ</h1><a href="/">กลับหน้าแรก</a></html>');}html=renderPublished(html,published,slug);}
  else if(slug!=='home'){res.statusCode=404;return res.end('Page not found');}
 }catch(e){console.error('Website content fallback:',e.message);if(slug!=='home'){res.statusCode=503;res.setHeader('Retry-After','60');return res.end('Content temporarily unavailable');}}
 res.statusCode=200;res.end(html);
}
