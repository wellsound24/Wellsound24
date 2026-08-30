(()=>{
  const frame=document.getElementById('dash');
  const GROUP_API='https://sjcxywxixgrpdgeqaepk.supabase.co/functions/v1/admin-ea-groups';
  let doc=null,win=null,groups=[],memberships=[],licenses=[],active='all',busy=false,rowsObserver=null;

  function token(){try{return win?.sessionStorage.getItem('well_admin_session')||''}catch{return''}}
  function toast(msg,bad=false){if(win&&typeof win.toast==='function')win.toast(msg,bad?'bad':undefined);else alert(msg)}
  async function groupPost(body){
    const t=token(); if(!t) throw new Error('unauthorized');
    const r=await fetch(GROUP_API,{method:'POST',headers:{'Content-Type':'application/json','Authorization':'Bearer '+t},body:JSON.stringify(body)});
    const j=await r.json().catch(()=>({ok:false,error:'server_error'}));
    if(!r.ok||!j.ok)throw new Error(j.error||'server_error');
    return j;
  }
  async function loadLicenses(){
    const t=token(); if(!t){licenses=[];return}
    try{
      const r=await win.fetch('/api/core',{method:'POST',headers:{'Content-Type':'application/json','Authorization':'Bearer '+t},body:JSON.stringify({action:'list',include_archived:true})});
      const j=await r.json();
      if(r.ok&&j.ok&&Array.isArray(j.licenses))licenses=j.licenses;
    }catch{}
  }
  async function refresh(){
    if(busy||!token())return;
    busy=true;
    try{
      const [g]=await Promise.all([groupPost({action:'list'}),loadLicenses()]);
      groups=Array.isArray(g.groups)?g.groups:[];
      memberships=Array.isArray(g.memberships)?g.memberships:[];
      renderAll();
    }catch(e){if(e.message!=='unauthorized')console.warn('groups-on-license',e)}
    finally{busy=false}
  }
  function memberSet(customerId){
    return new Set(memberships.filter(m=>String(m.customer_id)===String(customerId)).map(m=>String(m.group_id)));
  }
  function groupNames(customerId){
    const ids=memberSet(customerId);
    return groups.filter(g=>ids.has(String(g.id))).map(g=>g.name);
  }
  function injectStyle(){
    if(!doc||doc.getElementById('s6GroupsOnLicenseStyle'))return;
    const s=doc.createElement('style');s.id='s6GroupsOnLicenseStyle';s.textContent=`
      #view-customers #s6GroupFilter,#view-customers .s6-badges,#customerModal #s6CustomerGroupsField{display:none!important}
      #s6LicenseGroupBar{display:flex;gap:7px;flex-wrap:wrap;margin:0 0 12px}
      .s6-lg-chip{border:1px solid #2a3d56;background:#101a29;color:#aebcd0;border-radius:999px;padding:6px 11px;font-size:11px;cursor:pointer}
      .s6-lg-chip.active{background:#183d33;border-color:#2f8066;color:#63e1b8}
      .s6-lg-badges{display:flex;gap:4px;flex-wrap:wrap;margin-top:4px}
      .s6-lg-badge{font-size:9px;padding:2px 6px;border-radius:999px;border:1px solid #28445a;background:#102033;color:#a9c8df}
      #s6LicenseGroupField .s6-lg-box{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:7px;margin-top:5px}
      #s6LicenseGroupField .s6-lg-choice{display:flex;align-items:center;gap:7px;padding:8px 9px;border:1px solid #26384f;border-radius:10px;background:#0d1725;font-size:11px}
      #s6LicenseGroupField .s6-lg-choice input{width:15px;height:15px}
      #s6LicenseGroupField .s6-lg-actions{display:flex;align-items:center;justify-content:flex-end;gap:8px;margin-top:8px}
      #s6LicenseGroupField .s6-lg-msg{font-size:10px;color:#91a0b6;margin-right:auto}
      @media(max-width:760px){#s6LicenseGroupField .s6-lg-box{grid-template-columns:1fr}}
    `;doc.head.appendChild(s);
  }
  function ensureBar(){
    const view=doc?.getElementById('view-licenses'); if(!view)return;
    let bar=doc.getElementById('s6LicenseGroupBar');
    if(!bar){
      bar=doc.createElement('div');bar.id='s6LicenseGroupBar';
      const table=view.querySelector('.table-wrap');
      if(table)view.insertBefore(bar,table);else view.appendChild(bar);
      bar.addEventListener('click',e=>{
        const b=e.target.closest('[data-s6-lg-filter]'); if(!b)return;
        active=b.dataset.s6LgFilter||'all'; renderBar(); paintRows();
      });
    }
  }
  function renderBar(){
    const bar=doc?.getElementById('s6LicenseGroupBar'); if(!bar)return;
    bar.innerHTML='';
    const items=[{id:'all',name:'ทั้งหมด'},...groups];
    items.forEach(g=>{
      const b=doc.createElement('button');b.type='button';b.className='s6-lg-chip'+(String(g.id)===String(active)?' active':'');
      b.dataset.s6LgFilter=String(g.id);
      b.textContent=g.name+(g.id==='all'?'':' ('+(g.customer_count||0)+')');bar.appendChild(b);
    });
  }
  function paintRows(){
    const byId=new Map(licenses.map(l=>[String(l.id),l]));
    doc?.querySelectorAll('#licenseRows tr').forEach(row=>{
      const edit=row.querySelector('[data-edit-license]'); if(!edit)return;
      const lic=byId.get(String(edit.dataset.editLicense)); if(!lic)return;
      const cid=lic.customer_id||'';
      row.style.display=(active==='all'||(cid&&memberSet(cid).has(String(active))))?'':'none';
      const cell=row.children[0]; if(!cell)return;
      let box=cell.querySelector('.s6-lg-badges');
      if(!box){box=doc.createElement('div');box.className='s6-lg-badges';cell.appendChild(box)}
      const names=cid?groupNames(cid):[];box.innerHTML='';
      names.forEach(n=>{const s=doc.createElement('span');s.className='s6-lg-badge';s.textContent=n;box.appendChild(s)});
    });
    doc?.querySelectorAll('#customerRows tr').forEach(r=>{r.style.display=''});
  }
  function ensureField(){
    const grid=doc?.querySelector('#licenseModal .form-grid'); if(!grid)return;
    let field=doc.getElementById('s6LicenseGroupField');
    if(!field){
      field=doc.createElement('div');field.id='s6LicenseGroupField';field.className='field span2';
      field.innerHTML='<label>กลุ่มการใช้งาน EA</label><div id="s6LicenseGroupChoices" class="s6-lg-box"></div><div class="s6-lg-actions"><span id="s6LicenseGroupMsg" class="s6-lg-msg"></span><button id="s6SaveLicenseGroups" type="button" class="btn soft small">บันทึกกลุ่ม</button></div>';
      const notes=doc.getElementById('fNotes')?.closest('.field');if(notes)grid.insertBefore(field,notes);else grid.appendChild(field);
      doc.getElementById('s6SaveLicenseGroups')?.addEventListener('click',saveGroups);
    }
    renderChoices();
  }
  function renderChoices(){
    const box=doc?.getElementById('s6LicenseGroupChoices'); if(!box)return;
    const cid=doc.getElementById('fCustomer')?.value||''; const selected=cid?memberSet(cid):new Set();
    box.innerHTML='';
    if(!cid){box.innerHTML='<div class="field-help">เลือกลูกค้าก่อน แล้วจึงเลือกกลุ่ม</div>';return}
    if(!groups.length){box.innerHTML='<div class="field-help">ยังไม่มีกลุ่ม EA — สร้างได้ที่หน้า Settings</div>';return}
    groups.forEach(g=>{
      const label=doc.createElement('label');label.className='s6-lg-choice';
      const input=doc.createElement('input');input.type='checkbox';input.value=g.id;input.checked=selected.has(String(g.id));input.dataset.s6LgChoice='1';
      const span=doc.createElement('span');span.textContent=g.name;label.append(input,span);box.appendChild(label);
    });
  }
  async function saveGroups(){
    const cid=doc?.getElementById('fCustomer')?.value||'';const msg=doc?.getElementById('s6LicenseGroupMsg');
    if(!cid){if(msg)msg.textContent='กรุณาเลือกลูกค้าก่อน';return}
    const ids=[...doc.querySelectorAll('[data-s6-lg-choice]:checked')].map(x=>x.value);
    if(msg)msg.textContent='กำลังบันทึก...';
    try{await groupPost({action:'set_customer_groups',customer_id:cid,group_ids:ids});if(msg)msg.textContent='บันทึกกลุ่มแล้ว';await refresh();renderChoices();}
    catch(e){if(msg)msg.textContent='บันทึกไม่สำเร็จ';toast('บันทึกกลุ่มไม่สำเร็จ',true)}
  }
  function renderAll(){injectStyle();ensureBar();renderBar();ensureField();paintRows();}
  function wire(){
    if(!doc||doc.documentElement.dataset.s6GroupsOnLicense==='1')return;
    doc.documentElement.dataset.s6GroupsOnLicense='1';injectStyle();ensureBar();ensureField();
    doc.addEventListener('click',e=>{
      if(e.target.closest?.('.nav-btn[data-view="licenses"]'))setTimeout(refresh,100);
      if(e.target.closest?.('[data-edit-license],#addLicenseBtn,[data-quick="addLicense"]'))setTimeout(()=>{ensureField();renderChoices()},80);
    },true);
    doc.getElementById('fCustomer')?.addEventListener('change',renderChoices);
    const rows=doc.getElementById('licenseRows');
    if(rows){rowsObserver=new MutationObserver(()=>setTimeout(()=>{loadLicenses().then(paintRows)},40));rowsObserver.observe(rows,{childList:true})}
    refresh();setInterval(refresh,15000);
  }
  function attach(){try{win=frame.contentWindow;doc=frame.contentDocument||win.document}catch{return}if(!doc)return;wire()}
  frame.addEventListener('load',()=>setTimeout(attach,1200));
  if(frame.contentDocument?.readyState==='complete')setTimeout(attach,1200);
})();
