import {createClient} from 'npm:@supabase/supabase-js@2.57.4';
const url=Deno.env.get('SUPABASE_URL')||'';
const db=createClient(url,Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')||'');
const cors={'Access-Control-Allow-Origin':'*','Access-Control-Allow-Headers':'authorization,content-type','Access-Control-Allow-Methods':'POST,OPTIONS'};
const reply=(data:unknown,status=200)=>new Response(JSON.stringify(data),{status,headers:{...cors,'Content-Type':'application/json','Cache-Control':'no-store'}});
const checked=(r:any)=>{if(r.error)throw r.error;return r.data;};
const statuses=['new','waiting','quoted','confirmed','lost','needs_review'];
const sources=['unknown','website','line','phone','google_ads','facebook','other'];
const fields='id,name,contact,message,crm_status,event_name,event_date,location,amount,deposit_status,source,campaign,attribution_evidence,evidence,created_at,revision';
const uuid=(s:unknown)=>typeof s==='string'&&/^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i.test(s);
function clean(input:any){
 const x:any={};
 for(const [key,max] of Object.entries({name:120,contact:200,message:3000,event_name:300,location:500,campaign:300,attribution_evidence:1000})){
  if(typeof input[key]!=='string'||input[key].length>max)throw new Error('ข้อมูลไม่ถูกต้อง: '+key);x[key]=input[key].trim();
 }
 if(!x.name)throw new Error('กรุณาระบุชื่อลูกค้า');
 if(!statuses.includes(input.crm_status)||!sources.includes(input.source)||!['unverified','pending','received'].includes(input.deposit_status))throw new Error('สถานะไม่ถูกต้อง');
 x.crm_status=input.crm_status;x.source=input.source;x.deposit_status=input.deposit_status;
 if(input.source==='google_ads'&&!x.attribution_evidence)throw new Error('กรุณาระบุหลักฐานที่เชื่อมลูกค้ากับ Google Ads ก่อนเลือกแหล่งที่มานี้');
 if(input.event_date!==null&&(typeof input.event_date!=='string'||!/^\d{4}-\d{2}-\d{2}$/.test(input.event_date)||new Date(input.event_date).toISOString().slice(0,10)!==input.event_date))throw new Error('วันที่ไม่ถูกต้อง');
 x.event_date=input.event_date;
 if(input.amount!==null&&(typeof input.amount!=='number'||!Number.isFinite(input.amount)||input.amount<0||input.amount>=1e12))throw new Error('มูลค่างานไม่ถูกต้อง');x.amount=input.amount;
 if(!Array.isArray(input.evidence)||input.evidence.length>50)throw new Error('หลักฐานไม่ถูกต้อง');
 x.evidence=input.evidence.map((e:any)=>{
  if(!['owner','customer'].includes(e.speaker)||typeof e.text!=='string'||!e.text.trim()||e.text.length>3000||!(e.at===null||typeof e.at==='string'&&Number.isFinite(Date.parse(e.at))))throw new Error('ข้อความหลักฐานไม่ถูกต้อง');
  return {speaker:e.speaker,text:e.text.trim(),at:e.at,origin:'owner_recorded'};
 });
 x.status=x.crm_status==='confirmed'||x.crm_status==='lost'?'closed':x.crm_status==='new'?'new':'contacted';
 return x;
}
export async function handler(req:Request){
 if(req.method==='OPTIONS')return new Response(null,{headers:cors});
 if(req.method!=='POST')return reply({error:'method_not_allowed'},405);
 try{
  const authorization=req.headers.get('authorization')||'';
  if(!/^Bearer \S+$/.test(authorization))return reply({error:'กรุณาเข้าสู่ระบบ'},401);
  // Reuse existing Control Center authorization, including revocation and membership.
  const auth=await fetch(url+'/functions/v1/wellsound24-control',{method:'POST',headers:{Authorization:authorization,'Content-Type':'application/json'},body:JSON.stringify({action:'load'}),signal:AbortSignal.timeout(12000)});
  if(!auth.ok)return reply({error:'กรุณาเข้าสู่ระบบด้วยบัญชีผู้ดูแลเว็บไซต์'},auth.status===403?403:auth.status===401?401:503);
  const {profile}=await auth.json();if(!profile||!['owner','editor'].includes(profile.role))return reply({error:'ไม่มีสิทธิ์'},403);
  const raw=await req.text();if(raw.length>200000)return reply({error:'ข้อมูลใหญ่เกินกำหนด'},413);
  let b:any;try{b=JSON.parse(raw);}catch{return reply({error:'ข้อมูล JSON ไม่ถูกต้อง'},400);}
  if(b.action==='list'){
   const page=Number.isInteger(b.page)&&b.page>=0&&b.page<100000?b.page:0;
   let q=db.from('w24_leads').select(fields,{count:'exact'}).order('created_at',{ascending:false}).order('id').range(page*30,page*30+29);
   if(b.status==='pending')q=q.in('crm_status',['waiting','quoted']);
   else if(b.status&&b.status!=='all'){if(!statuses.includes(b.status))return reply({error:'สถานะไม่ถูกต้อง'},400);q=q.eq('crm_status',b.status);}
   const search=String(b.search||'').replace(/[%_,().\\]/g,' ').trim().slice(0,100);
   if(search)q=q.or(['name','contact','event_name','location'].map(k=>k+'.ilike.%'+search+'%').join(','));
   const items=await q;checked(items);const summary=checked(await db.rpc('w24_crm_summary'));
   return reply({items:items.data,total:items.count,page,summary,integration:{line:'not_connected',automaticConfirmation:false,googleAds:'not_connected'},checkedAt:new Date().toISOString()});
  }
  if(b.action==='save'){
   let values:any;try{values=clean(b.item);}catch(e){return reply({error:e instanceof Error?e.message:'ข้อมูลไม่ถูกต้อง'},400);}
   if(b.id){
    if(!uuid(b.id)||!Number.isInteger(b.revision)||b.revision<0)return reply({error:'รหัสเคสไม่ถูกต้อง'},400);
    const result=checked(await db.from('w24_leads').update({...values,revision:b.revision+1}).eq('id',b.id).eq('revision',b.revision).select(fields).maybeSingle());
    if(!result)return reply({error:'มีการแก้ไขเคสนี้จากอีกหน้าจอ กรุณาปิดแล้วเปิดเคสใหม่ก่อนแก้ไข'},409);
    return reply({item:result});
   }
   return reply({item:checked(await db.from('w24_leads').insert(values).select(fields).single())});
  }
  return reply({error:'unknown_action'},400);
 }catch(e){console.error('CRM operation failed',e instanceof Error?e.message:'database error');return reply({error:'โหลดหรือบันทึกข้อมูลลูกค้าไม่สำเร็จ กรุณาลองใหม่'},502);}
}
Deno.serve(handler);
