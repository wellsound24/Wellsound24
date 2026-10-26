import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {parseHTML} from 'linkedom';
import {contactChannel,recordContactClick,createContactRecorder} from '../contact-shared.js';
import {renderPublished} from '../api/site.js';
import {empty} from '../control-shared.js';
const template=readFileSync('site-template.html','utf8');
test('double taps do not inflate conversions, separate channels and later contacts still count',()=>{
 const {document}=parseHTML('<html><body><div class="mobile-contact"><a href="https://line.me/ti/p/test">LINE</a><a href="tel:0932614296">Phone</a></div></body></html>');
 const [line,phone]=document.querySelectorAll('a');
 let time=0;const record=createContactRecorder(()=>time),calls=[],tag=(...args)=>calls.push(args);
 assert.equal(record(line,undefined),false);
 assert.equal(record(line,tag),true);
 time=100;assert.equal(record(line,tag),false);
 assert.equal(record(phone,tag),true);
 time=1500;assert.equal(record(line,tag),true);
 assert.equal(calls.filter(x=>x[1]==='conversion').length,3);
 assert.equal(calls[0][2].contact_placement,'mobile_bar');
 assert.equal(JSON.stringify(calls).includes('0932614296'),false);
});
test('portfolio Facebook clicks never count as contact conversions',()=>{
 const {document}=parseHTML('<html><body><a class="text-link" href="https://www.facebook.com/WellSound24/">ดูผลงาน</a></body></html>');
 const calls=[];assert.equal(recordContactClick(document.querySelector('a'),(...x)=>calls.push(x)),false);assert.equal(calls.length,0);
});
test('contact buttons are distinguished from photo links, external and malformed URLs',()=>{
 const {document}=parseHTML('<html><body><div class="contact-options"><a href="https://www.facebook.com/SuPPerToNic">Facebook</a><a href="https://line.me/ti/p/test">LINE</a><a href="tel:0932614296">Phone</a><a href="https://facebook.com.evil.test/">Other</a><a href="https://www.facebook.com/x/photos">Photos</a><a href="tel:">Missing phone</a></div></body></html>');
 assert.deepEqual([...document.querySelectorAll('a')].map(contactChannel),['facebook','line','tel','','','']);
 const calls=[];recordContactClick(document.querySelector('a'),(...x)=>calls.push(x));assert.equal(calls.filter(x=>x[1]==='conversion').length,1);assert.equal(calls[0][1],'contact_click');assert.equal(calls[1][2].value,undefined);
});
test('service pages keep navigation connected to the homepage and SEO before contact',()=>{
 const c=empty();c.pages.push({slug:'sound',title:'Sound',sections:[{id:'el-114',type:'original'}],patches:{},seo:{h1:'Sound rental',h2:'FAQ',text:'Service details'}});
 const {document}=parseHTML(renderPublished(template,c,'sound'));
 assert.equal(document.querySelector('#main-nav a').getAttribute('href'),'/#services');
 assert.equal(document.querySelector('.brand').getAttribute('href'),'/');
 assert.equal(document.querySelector('main h1').textContent,'Sound rental');
 assert.equal(document.querySelector('#w24-seo-content').nextElementSibling.id,'contact');
 assert.equal(document.body.dataset.page,'sound');
});
test('optimized gallery keeps the full-size source for zoom',()=>{
 const c=empty();c.pages[0].sections=[{id:'el-38',type:'original'}];
 const source='https://sjcxywxixgrpdgeqaepk.supabase.co/storage/v1/object/public/w24-media/d5f76002-12a2-4444-ada6-492e0091ba9a.jpg';
 c.pages[0].patches={'el-53':{src:source}};
 const {document}=parseHTML(renderPublished(template,c));const img=document.querySelector('.photo-open img');
 assert.equal(img.dataset.fullSrc,source);assert.match(img.src,/\.webp$/);assert.match(img.getAttribute('srcset'),/480w/);
});
