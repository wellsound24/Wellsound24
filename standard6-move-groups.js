(()=>{
  const frame=document.getElementById('dash');
  const GROUP_API='https://sjcxywxixgrpdgeqaepk.supabase.co/functions/v1/admin-ea-groups';
  let doc=null,win=null,groups=[],memberships=[],licenses=[],activeGroup='all',busy=false,rowsObserver=null,lastRows=null;

  function token(){try{return win?.sessionStorage.getItem('well_admin_session')||''}catch{return''}}
  async function post(url,body){
    const t=token();if(!t)throw new Error('unauthorized');
    const r=await fetch(url,{method:'POST',headers:{'Content-Type':'application/json','Authorization':'Bearer '+t},body:JSON.stringify(body)});
    const j=await r.json().catch(()=>({ok:false,error:'server_error'}));
    if(!r.ok||!j.ok)throw new Error(j.error||'server_error');
    return j;
  }
  async function loadLicenses(){
    const t=token();if(!t)return[];
    try{
      const r=await win.fetch('/api/core',{method:'POST',headers:{'Content-Type':'application/json','Authorization':'Bearer '+t},body:JSON.stringify({action:'list',include_archived:true})});
      const j=await r.json();
      licenses=r.ok&&j.ok&&Array.isArray(j.licenses)?j.licenses:[];
    }catch{}
    return licenses;
  }
  async function loadGroups(){
    if(busy||!token())return;
    busy=true;
    try{
      const j=await post(GROUP_API,{action:'list'});
      groups=Array.isArray(j.groups)?j.groups:[];
      memberships=Array.isArray(j.memberships)?j.memberships:[];
      renderFilter();
      renderLicenseGroupChoices();
      paintLicenseRows();
    }catch(e){if(e.message!=='unauthorized')console.warn(e)}
    finally{busy=false}
  }
  function memberSet(customerId){
    return new Set(memberships.filter(x=>String(x.customer_id)===String(customerId)).map(x=>String(x.group_id)));
  }
  function groupNamesFor(customerId){
    const ids=memberSet(customerId);
    return groups.filter(g=>ids.has(String(g.id))).map(g=>g.name);
  }
  function injectStyle(){
    if(!doc||doc.getElementById('s6MoveGroupsStyle'))return;
    const s=doc.createElement('style');s.id='s6MoveGroupsStyle';s.textContent=`
      #view-customers #s6GroupFilter{display:none!important}
      #view-customers .s6-badges{display:none!important}
      #customerModal #s6CustomerGroupsField{display:none!important}
      #s6LicenseGroupFilter{display:flex;gap:7px;flex-wrap:wrap;margin:0 0 12px}
      .s6-license-group-chip{border:1px solid #2a3d56;background:#101a29;color:#aebcd0;border-radius:999px;padding:6px 11px;font-size:11px;cursor:pointer}
      .s6-license-group-chip.active{background:#183d33;border-color:#2f8066;color:#63e1b8}
      .s6-license-badges{display:flex;gap:4px;flex-wrap:wrap;margin-top:4px}
      .s6-license-badge{font-size:9px;padding:2px 6px;border-radius:999px;border:1px solid #28445a;background:#102033;color:#a9c8df}
      #s6LicenseGroupsField .s6-license-group-box{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:7px;margin-top:4px}
      #s6LicenseGroupsField .s6-license-group-choice{display:flex;align-items:center;gap:7px;padding:8px 9px;border:1px solid #26384f;border-radius:10px;background:#0d1725;font-size:11px}
      #s6LicenseGroupsField .s6-license-group-choice input{width:15px;height:15px}
      @media(max-width:760px){#s6LicenseGroupsField .s6-license-group-box{grid-template-columns:1fr}}
    `;doc.head.appendChild(s);
  }
  function ensureLicenseFilter(){
    if(!doc)return;
    const view=doc.getElementById('view-licenses');if(!view)return;
    let bar=doc.getElementById('s6LicenseGroupFilter');
    if(!bar){
      bar=doc.createElement('div');bar.id='s6LicenseGroupFilter';
      const table=view.querySelector('.table-wrap');
      if(table)view.insertBefore(bar,table);else view.appendChild(bar);
      bar.addEventListener('click',e=>{
        const b=e.target.closest('[data-s6-license-group-filter]');if(!b)return;
        activeGroup=b.dataset.s6LicenseGroupFilter||'all';
        renderFilter();paintLicenseRows();
      });
    }
    renderFilter();
  }
  function renderFilter(){
    const bar=doc?.getElementById('s6LicenseGroupFilter');if(!bar)return;
    bar.innerHTML='';
    const items=[{id:'all',name:'ทั้งหมด'},...groups];
    for(const g of items){
      const b=doc.createElement('button');b.type='button';
      b.className='s6-license-group-chip'+(String(g.id)===String(activeGroup)?' active':'');
      b.dataset.s6LicenseGroupFilter=String(g.id);
      b.textContent=g.name+(g.id==='all'?'':' ('+(g.customer_count||0)+')');
      bar.appendChild(b);
    }
  }
  function ensureLicenseGroupSelector(){
    if(!doc)return;
    const grid=doc.querySelector('#licenseModal .form-grid');if(!grid)return;
    let field=doc.getElementById('s6LicenseGroupsField');
    if(!field){
      field=doc.createElement('div');field.id='s6LicenseGroupsField';field.className='field span2';
      field.innerHTML='<label>กลุ่มการใช้งาน EA</label><div id="s6LicenseGroupChoices" class="s6-license-group-box"></div><div class="field-help">กลุ่มผูกกับลูกค้าที่เลือกใน License นี้</div>';
      const notes=doc.getElementById('fNotes')?.closest('.field');
      if(notes)grid.insertBefore(field,notes);else grid.appendChild(field);
    }
    renderLicenseGroupChoices();
  }
  function renderLicenseGroupChoices(){
    const box=doc?.getElementById('s6LicenseGroupChoices');if(!box)return;
    const customerId=doc.getElementById('fCustomer')?.value||'';
    const selected=customerId?memberSet(customerId):new Set();
    box.innerHTML='';
    if(!groups.length){box.innerHTML='<div class="field-help">ยังไม่มีกลุ่ม EA — สร้างได้ที่หน้า Settings</div>';return}
    for(const g of groups){
      const label=doc.createElement('label');label.className='s6-license-group-choice';
      const input=doc.createElement('input');input.type='checkbox';input.value=g.id;input.dataset.s6LicenseGroupChoice='1';input.checked=selected.has(String(g.id));
      const span=doc.createElement('span');span.textContent=g.name;
      label.append(input,span);box.appendChild(label);
    }
  }
  function selectedLicenseGroupIds(){
    return [...doc.querySelectorAll('[data-s6-license-group-choice]:checked')].map(x=>x.value);
  }
  async function saveGroupsFromLicense(){
    const customerId=doc?.getElementById('fCustomer')?.value||'';
    if(!customerId)return;
    const ids=selectedLicenseGroupIds();
    try{
      await post(GROUP_API,{action:'set_customer_groups',customer_id:customerId,group_ids:ids});
      await loadGroups();
    }catch(e){console.warn('save license groups failed',e)}
  }
  function paintLicenseRows(){
    if(!doc)return;
    const byId=new Map(licenses.map(x=>[String(x.id),x]));
    doc.querySelectorAll('#licenseRows tr').forEach(row=>{
      const edit=row.querySelector('[data-edit-license]');if(!edit)return;
      const lic=byId.get(String(edit.dataset.editLicense));if(!lic)return;
      const customerId=lic.customer_id||'';
      const inGroup=activeGroup==='all'||(customerId&&memberSet(customerId).has(String(activeGroup)));
      row.style.display=inGroup?'':'none';
      const cell=row.children[0];if(!cell)return;
      let box=cell.querySelector('.s6-license-badges');
      if(!box){box=doc.createElement('div');box.className='s6-license-badges';cell.appendChild(box)}
      const names=customerId?groupNamesFor(customerId):[];
      const stamp=names.join('|');if(box.dataset.stamp===stamp)return;
      box.dataset.stamp=stamp;box.innerHTML='';
      for(const n of names){const s=doc.createElement('span');s.className='s6-license-badge';s.textContent=n;box.appendChild(s)}
    });
    doc.querySelectorAll('#customerRows tr').forEach(row=>{row.style.display=''});
  }
  function watchLicenseRows(){
    const rows=doc?.getElementById('licenseRows');if(!rows||rows===lastRows)return;
    if(rowsObserver)rowsObserver.disconnect();lastRows=rows;
    rowsObserver=new MutationObserver(()=>setTimeout(()=>{loadLicenses().then(paintLicenseRows)},30));
    rowsObserver.observe(rows,{childList:true});
  }
  function wire(){
    if(!doc||doc.documentElement.dataset.s6GroupsMovedToLicense==='1')return;
    doc.documentElement.dataset.s6GroupsMovedToLicense='1';
    injectStyle();ensureLicenseFilter();ensureLicenseGroupSelector();watchLicenseRows();
    doc.addEventListener('click',e=>{
      const licensesNav=e.target.closest?.('.nav-btn[data-view="licenses"]');
      if(licensesNav)setTimeout(()=>{ensureLicenseFilter();loadLicenses().then(()=>loadGroups())},80);
      const edit=e.target.closest?.('[data-edit-license]');
      if(edit)setTimeout(()=>{ensureLicenseGroupSelector();renderLicenseGroupChoices()},30);
      const add=e.target.closest?.('#addLicenseBtn,[data-quick="addLicense"]');
      if(add)setTimeout(()=>{ensureLicenseGroupSelector();renderLicenseGroupChoices()},30);
    },true);
    const customerSelect=doc.getElementById('fCustomer');
    if(customerSelect)customerSelect.addEventListener('change',()=>renderLicenseGroupChoices());
    const licenseForm=doc.getElementById('licenseForm');
    if(licenseForm)licenseForm.addEventListener('submit',()=>{saveGroupsFromLicense()},true);
    const licenseModal=doc.getElementById('licenseModal');
    if(licenseModal)new MutationObserver(()=>{ensureLicenseGroupSelector();renderLicenseGroupChoices()}).observe(licenseModal,{attributes:true,attributeFilter:['class']});
    new MutationObserver(()=>{ensureLicenseFilter();watchLicenseRows();doc.querySelectorAll('#customerRows tr').forEach(row=>{row.style.display=''})}).observe(doc.body,{childList:true,subtree:true});
    Promise.all([loadLicenses(),loadGroups()]).then(()=>paintLicenseRows());
  }
  function attach(){
    try{win=frame.contentWindow;doc=frame.contentDocument||win.document}catch{return}
    if(!doc)return;wire();
  }
  frame.addEventListener('load',()=>setTimeout(attach,1100));
  if(frame.contentDocument?.readyState==='complete')setTimeout(attach,1100);
})();
