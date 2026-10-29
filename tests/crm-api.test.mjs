import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import vm from 'node:vm';
const source=stripTypeScriptTypes(readFileSync('supabase/functions/wellsound24-crm/index.ts','utf8').replace(/^import .*\n/,'').replace('export async function handler','async function handler'));
function fixture({authStatus=200,role='owner',row={id:'test'},count=0}={}){let handler;const calls=[];let saved;
 const query={select(){return this},order(){return this},range(){return this},eq(){return this},in(){return this},or(){return this},insert(v){saved=v;return this},update(v){saved=v;return this},async single(){return {data:row}},async maybeSingle(){return {data:row}},then(resolve){return Promise.resolve({data:[],count}).then(resolve)}};
 vm.runInNewContext(source,{Deno:{env:{get:k=>k==='SUPABASE_URL'?'https://test.supabase.co':'test'},serve:f=>handler=f},createClient:()=>({from(){calls.push('db');return query},rpc:async()=>({data:{total:0,new:0,waiting:0,confirmed:0,value:0}})}),Response,Request,AbortSignal,Date,Number,console,fetch:async()=>{calls.push('auth');return new Response(JSON.stringify({profile:{role}}),{status:authStatus})}});
 return {handler,calls,get saved(){return saved}};
}
const request=(body,token=true)=>new Request('https://test',{method:'POST',headers:token?{Authorization:'Bearer test'}:{},body:JSON.stringify(body)});
const item=()=>({name:'Customer',contact:'',message:'',event_name:'Event',event_date:null,location:'',amount:null,crm_status:'confirmed',deposit_status:'unverified',source:'unknown',campaign:'',attribution_evidence:'',evidence:[{speaker:'customer',text:'คอนเฟิร์มครับ',at:null}]});
test('CRM requires a live Control Center session before any database access',async()=>{for(const [token,authStatus] of [[false,200],[true,401],[true,403]]){const f=fixture({authStatus});const r=await f.handler(request({action:'list'},token));assert.ok([401,403].includes(r.status));assert.ok(!f.calls.includes('db'));}});
test('CRM rejects nonmembers, invalid prices, and unproven Google Ads attribution',async()=>{const f=fixture({role:'none'});assert.equal((await f.handler(request({action:'list'}))).status,403);for(const changes of [{amount:-1},{source:'google_ads'},{event_date:'2026-02-31'}]){const g=fixture();assert.equal((await g.handler(request({action:'save',item:{...item(),...changes}}))).status,400);assert.ok(!g.calls.includes('db'));}});
test('confirming a booking does not imply a deposit or invent a price or ad source',async()=>{const f=fixture();assert.equal((await f.handler(request({action:'save',item:item()}))).status,200);assert.equal(f.saved.crm_status,'confirmed');assert.equal(f.saved.deposit_status,'unverified');assert.equal(f.saved.amount,null);assert.equal(f.saved.source,'unknown');assert.equal(f.saved.evidence[0].origin,'owner_recorded');});
test('concurrent edits conflict rather than overwriting another device',async()=>{const f=fixture({row:null});const r=await f.handler(request({action:'save',id:'00000000-0000-0000-0000-000000000001',revision:0,item:item()}));assert.equal(r.status,409);});
test('customer list exposes integration limits and does not invent records',async()=>{const f=fixture();const r=await f.handler(request({action:'list',status:'pending'}));const d=await r.json();assert.deepEqual(d.items,[]);assert.equal(d.integration.line,'not_connected');assert.equal(d.integration.automaticConfirmation,false);});
