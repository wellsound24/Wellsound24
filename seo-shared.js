import {esc} from './control-shared.js';
export const ORIGIN='https://wellsound24.vercel.app';
export const EXISTING_VERIFICATION='i_DWSty-hy7Q3bgE97dqsr2QhN_tIA0xvSWGhPIl6HQ';
export const EXISTING_ADS='AW-11531689060';
export const pageUrl=p=>ORIGIN+'/'+(p.slug==='home'?'':'?page='+encodeURIComponent(p.slug));
export function httpUrl(value,fallback='') {try {const u=new URL(value,ORIGIN);return ['http:','https:'].includes(u.protocol)&&!u.username&&!u.password?u.href:fallback;}catch{return fallback;}}
export function normalizeSEO(value={}) {
 const str=(v,n)=>typeof v==='string'?v.trim().slice(0,n):'';
 return {keywords:str(value.keywords,1000),h1:str(value.h1,250),h2:str(value.h2,250),text:str(value.text,12000),
 faq:(Array.isArray(value.faq)?value.faq:[]).slice(0,20).map(x=>({question:str(x.question,300),answer:str(x.answer,2000)})).filter(x=>x.question&&x.answer),
 links:(Array.isArray(value.links)?value.links:[]).slice(0,30).map(x=>({title:str(x.title,200),url:str(x.url,1000)})).filter(x=>x.title&&httpUrl(x.url).startsWith(ORIGIN+'/')),
 images:(Array.isArray(value.images)?value.images:[]).slice(0,100).map(x=>({src:str(x.src,2000),alt:str(x.alt,500),filename:str(x.filename,200)})).filter(x=>x.src),
 canonical:httpUrl(value.canonical||''),noindex:value.noindex===true};
}
export function applySEO(document,content,page) {
 const s=normalizeSEO(page.seo),canonical=page.seo?.canonical?httpUrl(page.seo.canonical,pageUrl(page)):pageUrl(page);
 const meta=(key,value,property=false)=>{const selector=`meta[${property?'property':'name'}="${key}"]`;document.querySelectorAll(selector).forEach(e=>e.remove());const e=document.createElement('meta');e.setAttribute(property?'property':'name',key);e.setAttribute('content',value);document.head.append(e);};
 document.title=page.seoTitle||page.title;
 meta('description',page.description||'');meta('og:title',document.title,true);meta('og:description',page.description||'',true);
 meta('og:image',httpUrl(page.image||'/assets/hero-stage.png'),true);meta('og:url',canonical,true);
 meta('twitter:title',document.title);meta('twitter:description',page.description||'');meta('twitter:image',httpUrl(page.image||'/assets/hero-stage.png'));
 document.querySelectorAll('link[rel="canonical"]').forEach(e=>e.remove());const link=document.createElement('link');link.rel='canonical';link.href=canonical;document.head.append(link);
 meta('robots',s.noindex?'noindex,follow':'index,follow');
 meta('google-site-verification',content.marketing?.verification??EXISTING_VERIFICATION);
 document.querySelectorAll('#w24-seo-content').forEach(e=>e.remove());
 if(s.h1){const h=document.querySelector('main h1');if(h)h.textContent=s.h1;else{const h=document.createElement('h1');h.className='wrap';h.textContent=s.h1;document.querySelector('main').prepend(h);}}
 if(s.h2||s.text||s.faq.length||s.links.length){const section=document.createElement('section');section.id='w24-seo-content';section.className='section';section.innerHTML=`<div class="wrap">${s.h2?`<h2>${esc(s.h2)}</h2>`:''}${s.text?`<p style="white-space:pre-line">${esc(s.text)}</p>`:''}${s.faq.map(x=>`<article><h3>${esc(x.question)}</h3><p>${esc(x.answer)}</p></article>`).join('')}${s.links.length?`<nav aria-label="เนื้อหาที่เกี่ยวข้อง" style="display:flex;flex-wrap:wrap">${s.links.map(x=>`<a href="${esc(httpUrl(x.url))}">${esc(x.title)}</a>`).join('')}</nav>`:''}</div>`;document.querySelector('main').append(section);}
 for(const image of document.querySelectorAll('main img[src]')){const entry=s.images.find(x=>httpUrl(x.src)===httpUrl(image.getAttribute('src')));if(entry)image.setAttribute('alt',entry.alt);}
 // Replace generated schema as a unit; FAQ schema must describe visible content.
 document.querySelectorAll('#w24-seo-schema').forEach(e=>e.remove());
 if(s.faq.length||page.slug!=='home')document.querySelectorAll('script[type="application/ld+json"]').forEach(e=>{try{if(JSON.parse(e.textContent)['@type']==='FAQPage')e.remove();}catch{}});
 const graph=[{'@type':'WebPage','@id':canonical,name:document.title,url:canonical,description:page.description||''}];
 if(s.faq.length)graph.push({'@type':'FAQPage',mainEntity:s.faq.map(x=>({'@type':'Question',name:x.question,acceptedAnswer:{'@type':'Answer',text:x.answer}}))});
 const schema=document.createElement('script');schema.id='w24-seo-schema';schema.type='application/ld+json';schema.textContent=JSON.stringify({'@context':'https://schema.org','@graph':graph}).replace(/</g,'\\u003c');document.head.append(schema);
}
export function auditDocument(doc,url) {
 const checks=[];const check=(name,ok,detail)=>checks.push({name,status:ok?'pass':'warn',detail});
 const title=doc.title.trim();check('Title',!!title&&title.length<=70,`${title.length} ตัวอักษร · ${title||'ไม่มี Title'}`);
 const description=doc.querySelector('meta[name="description"]')?.content||'';check('Meta description',!!description&&description.length<=170,`${description.length} ตัวอักษร`);
 const h1=[...doc.querySelectorAll('main h1')].filter(e=>!e.closest('[hidden]'));check('H1',h1.length===1,`${h1.length} หัวข้อหลัก`);
 const images=[...doc.querySelectorAll('main img[src]')].filter(e=>!e.closest('[hidden]'));
 const missing=images.filter(e=>!e.getAttribute('alt')?.trim());check('Image ALT',!missing.length,`${missing.length} จาก ${images.length} รูปไม่มีคำอธิบาย`);
 check('Canonical',doc.querySelectorAll('link[rel="canonical"]').length===1,doc.querySelector('link[rel="canonical"]')?.href||'ไม่มี');
 check('Indexing directive',!doc.querySelector('meta[name="robots"]')?.content.includes('noindex'),'สถานะ directive เท่านั้น ต้องตรวจ Google URL Inspection เพื่อยืนยัน index');
 let schemas=0,invalid=0;doc.querySelectorAll('script[type="application/ld+json"]').forEach(e=>{try{JSON.parse(e.textContent);schemas++;}catch{invalid++;}});check('Schema JSON-LD',schemas>0&&!invalid,`${schemas} ชุด · ผิดรูปแบบ ${invalid}`);
 const links=[...new Set([...doc.querySelectorAll('a[href]')].map(e=>e.getAttribute('href')).filter(Boolean))];
 const brokenAnchors=links.filter(h=>h.startsWith('#')&&h.length>1&&!doc.getElementById(h.slice(1)));check('ลิงก์ภายในหน้า',!brokenAnchors.length,brokenAnchors.join(', ')||'พบปลายทางครบ');
 return {url,title,checks,text:(doc.querySelector('main')?.textContent||'').slice(0,16000),headings:[...doc.querySelectorAll('main h1,main h2')].map(e=>e.textContent),images:images.map(e=>({src:e.getAttribute('src'),alt:e.getAttribute('alt')||''})),links:links.filter(h=>!h.startsWith('#')&&!/^(tel:|mailto:|javascript:|data:)/i.test(h))};
}
