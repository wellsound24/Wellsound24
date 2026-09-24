import {spawnSync} from 'node:child_process';
import {readFileSync} from 'node:fs';
import {renderPublished} from '../api/site.js';
import {empty} from '../control-shared.js';
const files=['control-admin.js','control-render.js','control-shared.js','content-render.js','seo-admin.js','seo-shared.js','api/site.js','api/sitemap.js'];
for(const file of files){const result=spawnSync(process.execPath,['--check',file],{encoding:'utf8'});if(result.status)throw new Error(result.stderr);}
const template=readFileSync('site-template.html','utf8'),content=empty();content.pages[0].sections=[{id:'el-16',type:'original'}];
const html=renderPublished(template,content);if(!html.includes('w24-published')||!html.includes('rel="canonical"'))throw new Error('SSR build failed');
JSON.parse(readFileSync('vercel.json','utf8').replace(/^\uFEFF/,''));console.log('SEO modules and server renderer build passed');
