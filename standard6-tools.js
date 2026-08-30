if('serviceWorker' in navigator){navigator.serviceWorker.register('/sw.js',{scope:'/'}).catch(()=>{})}
(()=>{
  const frame=document.getElementById('dash');
  const loading=document.getElementById('loading');
  const DELETE_API='https://sjcxywxixgrpdgeqaepk.supabase.co/functions/v1/admin-customer-delete';
  const GROUP_API='https://sjcxywxixgrpdgeqaepk.supabase.co/functions/v1/admin-ea-groups';
  const PASSWORD_API='https://sjcxywxixgrpdgeqaepk.supabase.co/functions/v1/admin-password-recovery';
  let doc=null,win=null,licenseObserver=null,customerObserver=null,lastLicenseRows=null,lastCustomerRows=null;
  let currentCustomerId=null,customerCache=[],groups=[],memberships=[],activeGroup='all',groupsLoaded=false;
  const ONLINE_MS=8*60*1000,WAITING_MS=15*60*1000;

  function token(){return win?.sessionStorage.getItem('well_admin_session')||''}
  async function post(url,body,needsAuth=true){
    const headers={'Content-Type':'application/json'};
    if(needsAuth){const t=token();if(!t)throw new Error('unauthorized');headers.Authorization='Bearer '+t}
    const r=await fetch(url,{method:'POST',headers,body:JSON.stringify(body)});
    const j=await r.json().catch(()=>({ok:false,error:'server_error'}));
    if(!r.ok||!j.ok)throw new Error(j.error||'server_error');
    return j;
  }
  async function nextApi(action,payload={}){
    const t=token();if(!t)throw new Error('unauthorized');
    const r=await win.fetch('/api/next',{method:'POST',headers:{'Content-Type':'application/json','Authorization':'Bearer '+t},body:JSON.stringify({action,...payload})});
    const j=await r.json().catch(()=>({ok:false,error:'server_error'}));
    if(!r.ok||!j.ok)throw new Error(j.error||'server_error');
    return j;
  }
  function toast(msg,bad=false){if(win&&typeof win.toast==='function')win.toast(msg,bad?'bad':undefined);else alert(msg)}

  function licenseState(x){
    const today=new Date().toISOString().slice(0,10);
    if(!String(x.expires_at||'').startsWith('9999')&&String(x.expires_at||'')<today)return['Expired','bad'];
    if(!x.enabled)return['Disabled','bad'];
    if(x.ib_status==='pending')return['IB Pending','warn'];
    if(x.ib_status==='rejected')return['Blocked','warn'];
    return['Active','good'];
  }
  function connState(x){
    if(!x.last_check_at)return['Offline','offline'];
    const age=Math.max(0,Date.now()-new Date(x.last_check_at).getTime());
    if(age<=ONLINE_MS)return['Online','online'];
    if(age<=WAITING_MS)return['รอตรวจสอบ','waiting'];
    return['Offline','offline'];
  }
  function relative(iso){
    if(!iso)return'ยังไม่เคยเชื่อมต่อ';
    const m=Math.floor(Math.max(0,Date.now()-new Date(iso).getTime())/60000);
    if(m<1)return'เมื่อสักครู่';
    if(m<60)return m+' นาทีที่แล้ว';
    const h=Math.floor(m/60);if(h<24)return h+' ชม.ที่แล้ว';
    return new Date(iso).toLocaleString('th-TH',{dateStyle:'short',timeStyle:'short'});
  }
  function injectStyle(){
    if(!doc||doc.getElementById('standard6ExtraStyle'))return;
    const s=doc.createElement('style');s.id='standard6ExtraStyle';s.textContent=`
      #licenseRows td:nth-child(7){min-width:128px}.s6-live{line-height:1.2;min-width:120px}.s6-conn{display:flex;align-items:center;gap:5px;font-size:11px;font-weight:800}.s6-dot{width:7px;height:7px;border-radius:50%;display:inline-block;background:#7c8798}.s6-conn.online{color:#45d69a}.s6-conn.online .s6-dot{background:#45d69a;box-shadow:0 0 0 3px rgba(69,214,154,.12)}.s6-conn.waiting{color:#f2bc55}.s6-conn.waiting .s6-dot{background:#f2bc55}.s6-conn.offline{color:#ff7777}.s6-conn.offline .s6-dot{background:#ff7777}.s6-license{font-size:10px;color:#9aa8bc;margin-top:3px}.s6-license .good{color:#45d69a}.s6-license .warn{color:#f2bc55}.s6-license .bad{color:#ff7777}.s6-last{font-size:9px;color:#7f8da3;margin-top:2px;white-space:nowrap}
      .s6-email-line{display:flex;align-items:center;gap:6px;flex-wrap:wrap}.s6-copy-email{padding:3px 7px!important;min-height:24px!important;font-size:10px!important;white-space:nowrap}.s6-delete-customer{margin-right:auto!important;background:#4b1f27!important;border-color:#8b3644!important;color:#ffd9df!important}
      .s6-group-filter{display:flex;gap:7px;flex-wrap:wrap;margin:0 0 12px}.s6-group-chip{border:1px solid #2a3d56;background:#101a29;color:#aebcd0;border-radius:999px;padding:6px 11px;font-size:11px;cursor:pointer}.s6-group-chip.active{background:#183d33;border-color:#2f8066;color:#63e1b8}.s6-badges{display:flex;gap:4px;flex-wrap:wrap;margin-top:4px}.s6-badge{font-size:9px;padding:2px 6px;border-radius:999px;border:1px solid #28445a;background:#102033;color:#a9c8df}
      .s6-group-box{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:7px;margin-top:4px}.s6-group-choice{display:flex;align-items:center;gap:7px;padding:8px 9px;border:1px solid #26384f;border-radius:10px;background:#0d1725;font-size:11px}.s6-group-choice input{width:15px;height:15px}
      .s6-group-panel{grid-column:1/-1}.s6-group-create{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:8px;margin-top:12px}.s6-group-row{display:flex;align-items:center;justify-content:space-between;gap:10px;padding:10px;border:1px solid #26384f;border-radius:10px;background:#0d1725}.s6-group-row+.s6-group-row{margin-top:7px}.s6-group-actions{display:flex;gap:6px}.s6-forgot{margin-top:6px;background:transparent;border:0;color:#70c8ff;font-size:12px;cursor:pointer;text-decoration:underline}.s6-forgot-box{margin-top:10px;padding:11px;border:1px solid #2a3d56;border-radius:12px;background:#0d1725}.s6-forgot-row{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:7px}.s6-forgot-msg{font-size:11px;color:#9fb0c5;min-height:16px;margin-top:6px}
      @media(max-width:760px){.s6-group-box{grid-template-columns:1fr}.s6-group-create,.s6-forgot-row{grid-template-columns:1fr}}
    `;doc.head.appendChild(s);
  }

  async function getLicenses(){
    const t=token();if(!t)return[];
    try{const r=await win.fetch('/api/core',{method:'POST',headers:{'Content-Type':'application/json','Authorization':'Bearer '+t},body:JSON.stringify({action:'list',include_archived:true})});const j=await r.json();return r.ok&&j.ok&&Array.isArray(j.licenses)?j.licenses:[]}catch{return[]}
  }
  async function getCustomers(){
    const t=token();if(!t)return[];
    try{const j=await nextApi('customers');customerCache=Array.isArray(j.customers)?j.customers:[];return customerCache}catch{return customerCache}
  }
  async function loadGroups(force=false){
    if(!token()){groups=[];memberships=[];groupsLoaded=false;return}
    if(groupsLoaded&&!force)return;
    try{const j=await post(GROUP_API,{action:'list'});groups=j.groups||[];memberships=j.memberships||[];groupsLoaded=true;renderGroupUI()}catch(e){if(e.message!=='unauthorized')console.warn(e)}
  }
  function memberSet(customerId){return new Set(memberships.filter(x=>String(x.customer_id)===String(customerId)).map(x=>String(x.group_id)))}
  function groupNamesFor(customerId){const ids=memberSet(customerId);return groups.filter(g=>ids.has(String(g.id))).map(g=>g.name)}

  async function paintLicenseStatus(){
    if(!doc)return;const licenses=await getLicenses();if(!licenses.length)return;const byId=new Map(licenses.map(x=>[String(x.id),x]));
    doc.querySelectorAll('#licenseRows tr').forEach(row=>{const edit=row.querySelector('[data-edit-license]');if(!edit)return;const x=byId.get(String(edit.dataset.editLicense)),cell=row.children[6];if(!x||!cell)return;const[conn,cc]=connState(x),[lic,lc]=licenseState(x),last=relative(x.last_check_at),stamp=conn+'|'+lic+'|'+last;if(cell.dataset.s6stamp===stamp)return;cell.dataset.s6stamp=stamp;cell.innerHTML='<div class="s6-live"><div class="s6-conn '+cc+'"><span class="s6-dot"></span>'+conn+'</div><div class="s6-license">License: <span class="'+lc+'">'+lic+'</span></div><div class="s6-last">ล่าสุด: '+last+'</div></div>'});
  }
  async function paintCustomers(){
    if(!doc)return;const customers=await getCustomers();await loadGroups();const byId=new Map(customers.map(c=>[String(c.id),c]));
    doc.querySelectorAll('#customerRows tr').forEach(row=>{
      const edit=row.querySelector('[data-edit-customer]');if(!edit)return;const id=String(edit.dataset.editCustomer),c=byId.get(id);if(!c)return;
      const emailCell=row.children[2];if(emailCell&&c.email){const line=emailCell.querySelector('.subline');if(line&&!line.querySelector('[data-s6-copy-email]')){line.classList.add('s6-email-line');const b=doc.createElement('button');b.type='button';b.className='btn soft small s6-copy-email';b.textContent='Copy Email';b.dataset.s6CopyEmail=c.email;line.appendChild(b)}}
      const nameCell=row.children[0];if(nameCell){let box=nameCell.querySelector('.s6-badges');if(!box){box=doc.createElement('div');box.className='s6-badges';nameCell.appendChild(box)}const names=groupNamesFor(id),stamp=names.join('|');if(box.dataset.stamp!==stamp){box.dataset.stamp=stamp;box.innerHTML=names.map(n=>'<span class="s6-badge"></span>').join('');[...box.children].forEach((el,i)=>el.textContent=names[i])}}
      const inGroup=activeGroup==='all'||memberSet(id).has(activeGroup);row.style.display=inGroup?'':'none';
    });
  }

  function ensureDeleteButton(){
    const footer=doc?.querySelector('#customerModal .modal-foot');if(!footer)return;let b=doc.getElementById('s6DeleteCustomerBtn');if(!b){b=doc.createElement('button');b.id='s6DeleteCustomerBtn';b.type='button';b.className='btn danger s6-delete-customer';b.textContent='ลบบัญชีลูกค้า';footer.insertBefore(b,footer.firstChild);b.addEventListener('click',deleteCurrentCustomer)}const title=doc.getElementById('customerModalTitle')?.textContent||'';b.style.display=(currentCustomerId&&title.includes('แก้ไข'))?'':'none';
  }
  async function deleteCurrentCustomer(){
    if(!currentCustomerId)return;const c=customerCache.find(x=>String(x.id)===String(currentCustomerId)),name=c?.name||'ลูกค้ารายนี้';if(!confirm('ยืนยันลบบัญชีลูกค้า "'+name+'" ?\n\nระบบจะลบเฉพาะ Customer Profile\nLicense และ License Key เดิมจะไม่ถูกลบ'))return;
    try{await post(DELETE_API,{id:currentCustomerId});currentCustomerId=null;doc.getElementById('customerModal')?.classList.remove('open');toast('ลบบัญชีลูกค้าแล้ว');if(typeof win.loadAll==='function')await win.loadAll();groupsLoaded=false;await loadGroups(true);await paintCustomers()}catch(e){toast('ลบบัญชีไม่สำเร็จ: '+e.message,true)}
  }
  async function copyText(v,msg){try{await navigator.clipboard.writeText(v)}catch{const ta=document.createElement('textarea');ta.value=v;document.body.appendChild(ta);ta.select();document.execCommand('copy');ta.remove()}toast(msg)}

  function ensureGroupFilter(){
    const view=doc?.getElementById('view-customers');if(!view)return;let bar=doc.getElementById('s6GroupFilter');if(!bar){bar=doc.createElement('div');bar.id='s6GroupFilter';bar.className='s6-group-filter';const table=view.querySelector('.table-wrap');view.insertBefore(bar,table)}renderGroupFilter();
  }
  function renderGroupFilter(){
    const bar=doc?.getElementById('s6GroupFilter');if(!bar)return;const items=[{id:'all',name:'ทั้งหมด'},...groups];bar.innerHTML='';for(const g of items){const b=doc.createElement('button');b.type='button';b.className='s6-group-chip'+(String(g.id)===String(activeGroup)?' active':'');b.textContent=g.name+(g.id==='all'?'':' ('+(g.customer_count||0)+')');b.dataset.s6GroupFilter=String(g.id);bar.appendChild(b)}
  }
  function ensureGroupSelector(){
    const grid=doc?.querySelector('#customerModal .form-grid');if(!grid)return;let field=doc.getElementById('s6CustomerGroupsField');if(!field){field=doc.createElement('div');field.id='s6CustomerGroupsField';field.className='field span2';field.innerHTML='<label>กลุ่มการใช้งาน EA</label><div id="s6CustomerGroupChoices" class="s6-group-box"></div><div class="field-help">ลูกค้า 1 คนเลือกได้หลายกลุ่ม</div>';const notes=doc.getElementById('cNotes')?.closest('.field');if(notes)grid.insertBefore(field,notes);else grid.appendChild(field)}renderGroupSelector();
  }
  function renderGroupSelector(){
    const box=doc?.getElementById('s6CustomerGroupChoices');if(!box)return;const selected=currentCustomerId?memberSet(currentCustomerId):new Set();box.innerHTML='';if(!groups.length){box.innerHTML='<div class="field-help">ยังไม่มีกลุ่ม EA — สร้างได้ที่หน้า Settings</div>';return}for(const g of groups){const label=doc.createElement('label');label.className='s6-group-choice';const input=doc.createElement('input');input.type='checkbox';input.value=g.id;input.dataset.s6GroupChoice='1';input.checked=selected.has(String(g.id));const span=doc.createElement('span');span.textContent=g.name;label.append(input,span);box.appendChild(label)}}
  function selectedGroupIds(){return [...doc.querySelectorAll('[data-s6-group-choice]:checked')].map(x=>x.value)}

  function ensureGroupSettings(){
    const grid=doc?.querySelector('#view-settings .settings-grid');if(!grid)return;let panel=doc.getElementById('s6GroupSettingsPanel');if(!panel){panel=doc.createElement('div');panel.id='s6GroupSettingsPanel';panel.className='panel s6-group-panel';panel.innerHTML='<div class="panel-head"><div><div class="panel-title">จัดการ EA Group</div><div class="panel-sub">ตั้งชื่อกลุ่มเองได้ และลูกค้าหนึ่งคนอยู่ได้หลายกลุ่ม</div></div></div><div class="s6-group-create"><input id="s6NewGroupName" class="input" placeholder="ชื่อกลุ่ม เช่น SemiAuto VIP"><button id="s6AddGroupBtn" class="btn primary" type="button">+ เพิ่มกลุ่ม</button></div><div id="s6GroupList" style="margin-top:12px"></div>';grid.appendChild(panel);panel.addEventListener('click',handleGroupSettingsClick)}renderGroupSettings();
  }
  function renderGroupSettings(){const list=doc?.getElementById('s6GroupList');if(!list)return;list.innerHTML='';if(!groups.length){list.innerHTML='<div class="empty">ยังไม่มีกลุ่ม EA</div>';return}for(const g of groups){const row=doc.createElement('div');row.className='s6-group-row';const left=doc.createElement('div');left.innerHTML='<b></b><div class="panel-sub"></div>';left.querySelector('b').textContent=g.name;left.querySelector('.panel-sub').textContent=(g.customer_count||0)+' ลูกค้า';const actions=doc.createElement('div');actions.className='s6-group-actions';const rn=doc.createElement('button');rn.type='button';rn.className='btn small';rn.textContent='เปลี่ยนชื่อ';rn.dataset.s6RenameGroup=g.id;const del=doc.createElement('button');del.type='button';del.className='btn small danger';del.textContent='ลบ';del.dataset.s6DeleteGroup=g.id;actions.append(rn,del);row.append(left,actions);list.appendChild(row)}}
  async function handleGroupSettingsClick(e){
    const add=e.target.closest('#s6AddGroupBtn');if(add){const input=doc.getElementById('s6NewGroupName'),name=input.value.trim();if(!name)return;try{const j=await post(GROUP_API,{action:'create',name});groups=j.groups||[];memberships=j.memberships||[];input.value='';groupsLoaded=true;renderGroupUI();toast('เพิ่มกลุ่ม EA แล้ว')}catch(x){toast(x.message==='duplicate_group'?'ชื่อกลุ่มนี้มีแล้ว':'เพิ่มกลุ่มไม่สำเร็จ',true)}return}
    const rn=e.target.closest('[data-s6-rename-group]');if(rn){const g=groups.find(x=>String(x.id)===String(rn.dataset.s6RenameGroup));if(!g)return;const name=prompt('ชื่อกลุ่มใหม่',g.name);if(!name||!name.trim())return;try{const j=await post(GROUP_API,{action:'rename',id:g.id,name:name.trim()});groups=j.groups||[];memberships=j.memberships||[];groupsLoaded=true;renderGroupUI();toast('เปลี่ยนชื่อกลุ่มแล้ว')}catch(x){toast(x.message==='duplicate_group'?'ชื่อกลุ่มนี้มีแล้ว':'เปลี่ยนชื่อไม่สำเร็จ',true)}return}
    const del=e.target.closest('[data-s6-delete-group]');if(del){const g=groups.find(x=>String(x.id)===String(del.dataset.s6DeleteGroup));if(!g||!confirm('ลบกลุ่ม "'+g.name+'" ?\nลูกค้าจะไม่ถูกลบ มีเพียงการจัดกลุ่มนี้ที่ถูกลบ'))return;try{const j=await post(GROUP_API,{action:'delete',id:g.id});groups=j.groups||[];memberships=j.memberships||[];if(activeGroup===String(g.id))activeGroup='all';groupsLoaded=true;renderGroupUI();toast('ลบกลุ่มแล้ว')}catch(x){toast('ลบกลุ่มไม่สำเร็จ',true)}}
  }
  function renderGroupUI(){ensureGroupFilter();ensureGroupSelector();ensureGroupSettings();renderGroupFilter();renderGroupSelector();renderGroupSettings();paintCustomers()}

  async function saveCustomerWithGroups(e){
    e.preventDefault();e.stopImmediatePropagation();const p={name:doc.getElementById('cName').value.trim(),phone:doc.getElementById('cPhone').value.trim(),email:doc.getElementById('cEmail').value.trim(),line_id:doc.getElementById('cLine').value.trim(),notes:doc.getElementById('cNotes').value.trim()};const msg=doc.getElementById('customerMsg');if(!p.name){msg.textContent='กรุณาใส่ชื่อ';return}msg.textContent='กำลังบันทึก...';
    try{let id=currentCustomerId;if(id)await nextApi('update_customer',{id,...p});else{const j=await nextApi('create_customer',p);id=j.customer.id}await post(GROUP_API,{action:'set_customer_groups',customer_id:id,group_ids:selectedGroupIds()});doc.getElementById('customerModal').classList.remove('open');currentCustomerId=null;toast('บันทึกข้อมูลลูกค้าแล้ว');if(typeof win.loadAll==='function')await win.loadAll();groupsLoaded=false;await loadGroups(true);await paintCustomers();msg.textContent=''}catch(x){msg.textContent=x.message==='duplicate_group'?'ข้อมูลซ้ำ':'บันทึกไม่สำเร็จ: '+x.message}
  }

  function ensureForgotPassword(){
    const login=doc?.getElementById('loginBtn');if(!login||doc.getElementById('s6ForgotBtn'))return;const b=doc.createElement('button');b.id='s6ForgotBtn';b.type='button';b.className='s6-forgot';b.textContent='ลืม Password?';login.insertAdjacentElement('afterend',b);const box=doc.createElement('div');box.id='s6ForgotBox';box.className='s6-forgot-box';box.style.display='none';box.innerHTML='<div class="s6-forgot-row"><input id="s6ForgotEmail" class="input" type="email" placeholder="Email ของ Admin"><button id="s6SendResetBtn" class="btn soft" type="button">ส่งลิงก์</button></div><div id="s6ForgotMsg" class="s6-forgot-msg"></div>';b.insertAdjacentElement('afterend',box);b.addEventListener('click',()=>{box.style.display=box.style.display==='none'?'block':'none'});doc.getElementById('s6SendResetBtn').addEventListener('click',sendResetEmail)
  }
  async function sendResetEmail(){
    const email=doc.getElementById('s6ForgotEmail').value.trim(),msg=doc.getElementById('s6ForgotMsg'),btn=doc.getElementById('s6SendResetBtn');if(!email){msg.textContent='กรุณาใส่ Email';return}btn.disabled=true;msg.textContent='กำลังส่ง...';try{await post(PASSWORD_API,{action:'request',email},false);msg.textContent='ถ้า Email ตรงกับ Admin ระบบส่งลิงก์เปลี่ยน Password ให้แล้ว กรุณาเช็ก Inbox / Spam'}catch(e){msg.textContent=e.message==='email_send_failed'?'ส่ง Email ไม่สำเร็จ กรุณาตรวจการตั้งค่า Email ของระบบ':'ส่งคำขอไม่สำเร็จ'}finally{btn.disabled=false}
  }

  function wire(){
    if(!doc||doc.documentElement.dataset.s6Wired==='1')return;doc.documentElement.dataset.s6Wired='1';injectStyle();ensureForgotPassword();ensureGroupFilter();ensureGroupSelector();ensureGroupSettings();ensureDeleteButton();
    doc.addEventListener('click',e=>{
      const edit=e.target.closest?.('[data-edit-customer]');if(edit){currentCustomerId=edit.dataset.editCustomer;setTimeout(()=>{ensureDeleteButton();renderGroupSelector()},0)}
      const add=e.target.closest?.('#addCustomerBtn,[data-quick="addCustomer"]');if(add){currentCustomerId=null;setTimeout(()=>{ensureDeleteButton();renderGroupSelector()},0)}
      const cp=e.target.closest?.('[data-s6-copy-email]');if(cp){e.preventDefault();copyText(cp.dataset.s6CopyEmail,'คัดลอก Email แล้ว')}
      const gf=e.target.closest?.('[data-s6-group-filter]');if(gf){activeGroup=gf.dataset.s6GroupFilter;renderGroupFilter();paintCustomers()}
      const settings=e.target.closest?.('.nav-btn[data-view="settings"]');if(settings)setTimeout(()=>{loadGroups(true);ensureGroupSettings()},50);
      const customers=e.target.closest?.('.nav-btn[data-view="customers"]');if(customers)setTimeout(()=>{loadGroups(true);paintCustomers()},50)
    },true);
    const form=doc.getElementById('customerForm');if(form)form.addEventListener('submit',saveCustomerWithGroups,true);
    const modal=doc.getElementById('customerModal');if(modal)new MutationObserver(()=>{ensureDeleteButton();ensureGroupSelector()}).observe(modal,{attributes:true,attributeFilter:['class']});
    const app=doc.getElementById('appView');if(app)new MutationObserver(()=>{if(!app.classList.contains('hidden')){groupsLoaded=false;loadGroups(true);paintCustomers();paintLicenseStatus()}}).observe(app,{attributes:true,attributeFilter:['class']});
  }
  function watchRows(){
    const lr=doc?.getElementById('licenseRows');if(lr&&lr!==lastLicenseRows){licenseObserver?.disconnect();lastLicenseRows=lr;licenseObserver=new MutationObserver(()=>setTimeout(paintLicenseStatus,25));licenseObserver.observe(lr,{childList:true});paintLicenseStatus()}
    const cr=doc?.getElementById('customerRows');if(cr&&cr!==lastCustomerRows){customerObserver?.disconnect();lastCustomerRows=cr;customerObserver=new MutationObserver(()=>setTimeout(paintCustomers,25));customerObserver.observe(cr,{childList:true});paintCustomers()}
  }
  function attach(){
    try{win=frame.contentWindow;doc=frame.contentDocument||win.document}catch{return}if(!doc)return;loading.style.display='none';wire();watchRows();loadGroups(true);paintLicenseStatus();paintCustomers();setInterval(()=>{watchRows();paintLicenseStatus();if(token())paintCustomers()},30000)
  }
  frame.addEventListener('load',()=>setTimeout(attach,500));
})();
