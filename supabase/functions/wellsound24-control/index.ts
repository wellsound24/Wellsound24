import {createClient} from 'npm:@supabase/supabase-js@2.57.4';
const url=Deno.env.get('SUPABASE_URL')!;
const db=createClient(url,Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!);
const cors={'Access-Control-Allow-Origin':'*','Access-Control-Allow-Headers':'authorization,content-type','Access-Control-Allow-Methods':'POST,OPTIONS'};
const answer=(data:unknown,status=200)=>new Response(JSON.stringify(data),{status,headers:{...cors,'Content-Type':'application/json','Cache-Control':'no-store'}});
const hash=async(s:string)=>Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(s)))).map(x=>x.toString(16).padStart(2,'0')).join('');
const checked=(r:any)=>{if(r.error)throw r.error;return r.data;};
async function limit(req:Request,action:string,max:number,seconds:number){const ip=req.headers.get('x-forwarded-for')?.split(',')[0]||'unknown';return checked(await db.rpc('w24_limit',{p_key:action+':'+await hash(ip),p_max:max,p_seconds:seconds}));}
async function auth(req:Request){const token=(req.headers.get('authorization')||'').replace(/^Bearer /,'');if(!token)return null;const tokenHash=await hash(token);let account=null;
 for(const [sessions,accounts] of [['w24_sessions','w24_accounts'],['admin_sessions','admin_accounts']]){const session=checked(await db.from(sessions).select('admin_id').eq('token_hash',tokenHash).gt('expires_at',new Date().toISOString()).maybeSingle());if(session){account=checked(await db.from(accounts).select('id,username,display_name').eq('id',session.admin_id).eq('enabled',true).maybeSingle());break;}}
 if(!account)return null;const member=checked(await db.from('w24_members').select('role').eq('admin_id',account.id).maybeSingle());return member?{...account,role:member.role}:null;
}
const b64=(a:Uint8Array)=>btoa(String.fromCharCode(...a)).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');
async function passwordHash(password:string,saltText:string,iterations:number){const salt=Uint8Array.from(atob(saltText.replace(/-/g,'+').replace(/_/g,'/')),c=>c.charCodeAt(0));const key=await crypto.subtle.importKey('raw',new TextEncoder().encode(password),'PBKDF2',false,['deriveBits']);return b64(new Uint8Array(await crypto.subtle.deriveBits({name:'PBKDF2',hash:'SHA-256',salt,iterations},key,256)));}

function validContent(c:any){return c&&c.schema===1&&Array.isArray(c.pages)&&c.pages.length>0&&c.pages.length<=100&&c.pages.some((p:any)=>p.slug==='home')&&new Set(c.pages.map((p:any)=>p.slug)).size===c.pages.length&&c.pages.every((p:any)=>/^[a-z0-9-]{1,70}$/.test(p.slug)&&Array.isArray(p.sections)&&p.sections.length<=100)&&JSON.stringify(c).length<1500000;}
Deno.serve(async(req)=>{
 if(req.method==='OPTIONS')return new Response(null,{headers:cors});
 if(req.method!=='POST')return answer({error:'method_not_allowed'},405);
 try{
 const raw=await req.text();if(raw.length>2000000)return answer({error:'ข้อมูลใหญ่เกินกำหนด'},413);const b=JSON.parse(raw),action=b.action;
 if(action==='public'){const s=checked(await db.from('w24_state').select('published,published_at').eq('id',1).single());return answer(s);}
 if(action==='login'){
   if(!await limit(req,'login',12,900))return answer({error:'ลองเข้าสู่ระบบอีกครั้งใน 15 นาที'},429);
   const web=checked(await db.from('w24_accounts').select('*').eq('username',String(b.username||'').toLowerCase()).eq('enabled',true).maybeSingle());
   if(web){const derived=await passwordHash(String(b.password||''),web.password_salt,web.password_iterations);let diff=derived.length^web.password_hash.length;for(let i=0;i<derived.length;i++)diff|=derived.charCodeAt(i)^(web.password_hash.charCodeAt(i)||0);if(diff)return answer({error:'ชื่อผู้ใช้หรือรหัสผ่านไม่ถูกต้อง'},401);const member=checked(await db.from('w24_members').select('role').eq('admin_id',web.id).maybeSingle());if(!member)return answer({error:'บัญชีนี้ไม่มีสิทธิ์จัดการเว็บไซต์'},403);const token=b64(crypto.getRandomValues(new Uint8Array(32)));checked(await db.from('w24_sessions').insert({admin_id:web.id,token_hash:await hash(token),expires_at:new Date(Date.now()+12*3600000).toISOString()}));return answer({token,profile:{id:web.id,username:web.username,display_name:web.display_name,role:member.role}});}
   const r=await fetch(url+'/functions/v1/admin-license',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action:'login',username:b.username,password:b.password})});const d=await r.json();
   if(!r.ok||!d.session)return answer({error:'ชื่อผู้ใช้หรือรหัสผ่านไม่ถูกต้อง'},401);
   const m=checked(await db.from('w24_members').select('role').eq('admin_id',d.profile.id).maybeSingle());if(!m)return answer({error:'บัญชีนี้ไม่มีสิทธิ์จัดการเว็บไซต์'},403);
   return answer({token:d.session.token,profile:{...d.profile,role:m.role}});
 }
 if(action==='lead'){
   if(b.website)return answer({ok:true});
   if(!await limit(req,'lead',5,3600))return answer({error:'ส่งข้อความถี่เกินไป กรุณาติดต่อทางโทรศัพท์'},429);
   const name=String(b.name||'').trim(),contact=String(b.contact||'').trim(),message=String(b.message||'').trim();
   if(!name||name.length>120||!contact||contact.length>200||!message||message.length>3000||b.consent!==true)return answer({error:'กรุณากรอกข้อมูลให้ครบและยินยอมให้ติดต่อกลับ'},400);
   checked(await db.from('w24_leads').insert({name,contact,message}));return answer({ok:true});
 }
 if(action==='event'){if(!['view','contact'].includes(b.kind)||!await limit(req,'events',120,3600))return answer({ok:true});checked(await db.from('w24_events').insert({kind:b.kind,page:String(b.page||'home').slice(0,100)}));return answer({ok:true});}
 const user=await auth(req);if(!user)return answer({error:'กรุณาเข้าสู่ระบบใหม่'},401);
 if(action==='load'){const state=checked(await db.from('w24_state').select('*').eq('id',1).single());return answer({state,profile:user});}
 if(action==='save'||action==='publish'){
   if(action==='publish'&&user.role!=='owner')return answer({error:'เฉพาะเจ้าของเว็บไซต์ที่เผยแพร่ได้'},403);
   if(!validContent(b.content))return answer({error:'รูปแบบข้อมูลไม่ถูกต้อง'},400);
   const r=await db.rpc('w24_save',{p_content:b.content,p_revision:b.revision,p_admin:user.id,p_publish:action==='publish'});
   if(r.error?.message.includes('revision_conflict'))return answer({error:'มีการแก้ไขจากอีกหน้าจอ กรุณาสำรองข้อมูลก่อนโหลดใหม่'},409);
   return answer({state:checked(r)});
 }
 if(action==='history')return answer({items:checked(await db.from('w24_versions').select('id,created_at,admin_id').order('id',{ascending:false}).limit(100))});
 if(action==='version')return answer({item:checked(await db.from('w24_versions').select('*').eq('id',b.id).single())});
 if(action==='leads')return answer({items:checked(await db.from('w24_leads').select('*').order('created_at',{ascending:false}).limit(500))});
 if(action==='lead_status'){if(!['new','contacted','closed'].includes(b.status))return answer({error:'invalid_status'},400);checked(await db.from('w24_leads').update({status:b.status}).eq('id',b.id));return answer({ok:true});}
 if(action==='stats'){const since=new Date(Date.now()-30*86400000).toISOString();const views=await db.from('w24_events').select('id',{count:'exact',head:true}).eq('kind','view').gte('created_at',since);const contacts=await db.from('w24_events').select('id',{count:'exact',head:true}).eq('kind','contact').gte('created_at',since);checked(views);checked(contacts);return answer({views:views.count,contacts:contacts.count});}
 if(action==='media'){return answer({items:checked(await db.storage.from('w24-media').list('',{limit:1000,sortBy:{column:'created_at',order:'desc'}})).map((x:any)=>({...x,url:url+'/storage/v1/object/public/w24-media/'+encodeURIComponent(x.name)}))});}
 if(action==='delete_media'){
   if(user.role!=='owner'||!/^[-a-z0-9]+\.(jpg|png|webp|gif|mp4|webm)$/.test(b.name))return answer({error:'ไม่มีสิทธิ์ลบไฟล์นี้'},403);
   const state=checked(await db.from('w24_state').select('draft,published').eq('id',1).single());const versions=checked(await db.from('w24_versions').select('content'));
   if(JSON.stringify([state,versions]).includes(b.name))return answer({error:'ไฟล์นี้ถูกใช้อยู่ในเว็บไซต์หรือประวัติเวอร์ชัน จึงยังลบไม่ได้'},409);
   checked(await db.storage.from('w24-media').remove([b.name]));return answer({ok:true});
 }
 if(action==='create_member'){
   if(user.role!=='owner')return answer({error:'ไม่มีสิทธิ์'},403);
   const username=String(b.username||'').toLowerCase(),password=String(b.password||''),display_name=String(b.display_name||'').trim();
   if(!/^[a-z0-9._-]{3,32}$/.test(username)||password.length<12||password.length>128||!display_name||display_name.length>80||!['owner','editor'].includes(b.role))return answer({error:'ตรวจชื่อผู้ใช้ รหัสผ่านอย่างน้อย 12 ตัวอักษร และชื่อที่แสดง'},400);
   const salt=crypto.getRandomValues(new Uint8Array(16));const key=await crypto.subtle.importKey('raw',new TextEncoder().encode(password),'PBKDF2',false,['deriveBits']);const bits=new Uint8Array(await crypto.subtle.deriveBits({name:'PBKDF2',hash:'SHA-256',salt,iterations:120000},key,256));const b64=(a:Uint8Array)=>btoa(String.fromCharCode(...a)).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');
   const result=await db.from('w24_accounts').insert({username,display_name,password_salt:b64(salt),password_hash:b64(bits),password_iterations:120000}).select('id').single();if(result.error?.code==='23505')return answer({error:'ชื่อผู้ใช้นี้มีอยู่แล้ว'},409);const a=checked(result);const member=await db.from('w24_members').insert({admin_id:a.id,role:b.role});if(member.error){await db.from('w24_accounts').delete().eq('id',a.id);throw member.error;}return answer({ok:true});
 }
 if(action==='upload_url'){
   if(!['image/jpeg','image/png','image/webp','image/gif','video/mp4','video/webm'].includes(b.type)||!(b.size>0&&b.size<=20971520))return answer({error:'รองรับรูปและวิดีโอขนาดไม่เกิน 20 MB'},400);
   const ext={'image/jpeg':'jpg','image/png':'png','image/webp':'webp','image/gif':'gif','video/mp4':'mp4','video/webm':'webm'}[b.type as string];const path=crypto.randomUUID()+'.'+ext;
   const data=checked(await db.storage.from('w24-media').createSignedUploadUrl(path));return answer({signedUrl:data.signedUrl,url:url+'/storage/v1/object/public/w24-media/'+path});
 }
 if(action==='members'){if(user.role!=='owner')return answer({error:'ไม่มีสิทธิ์'},403);const members=checked(await db.from('w24_members').select('admin_id,role'));const accounts=[...checked(await db.from('admin_accounts').select('id,username,display_name').eq('enabled',true)),...checked(await db.from('w24_accounts').select('id,username,display_name').eq('enabled',true))];return answer({members,accounts});}
 if(action==='member_set'){if(user.role!=='owner'||b.id===user.id||!['owner','editor','none'].includes(b.role))return answer({error:'ไม่สามารถเปลี่ยนสิทธิ์นี้ได้'},403);if(b.role==='none')checked(await db.from('w24_members').delete().eq('admin_id',b.id));else checked(await db.from('w24_members').upsert({admin_id:b.id,role:b.role}));return answer({ok:true});}
 if(action==='logout'){checked(await db.from('w24_sessions').delete().eq('token_hash',await hash((req.headers.get('authorization')||'').replace(/^Bearer /,''))));checked(await db.from('admin_sessions').delete().eq('token_hash',await hash((req.headers.get('authorization')||'').replace(/^Bearer /,''))));return answer({ok:true});}
 return answer({error:'unknown_action'},400);
 }catch(e){console.error(e);return answer({error:'ไม่สามารถดำเนินการได้ กรุณาลองใหม่'},500);}
});
