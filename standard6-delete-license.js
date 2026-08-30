(()=>{
  const frame=document.getElementById('dash');
  const DELETE_API='https://sjcxywxixgrpdgeqaepk.supabase.co/functions/v1/admin-customer-delete';
  let doc=null,win=null,currentLicenseId=null,licenses=[],customers=[];

  function token(){return win?.sessionStorage.getItem('well_admin_session')||''}
  function toast(msg,bad=false){
    if(win&&typeof win.toast==='function')win.toast(msg,bad?'bad':undefined);
    else alert(msg);
  }
  async function loadData(){
    const t=token();if(!t)return;
    try{
      const [lr,cr]=await Promise.all([
        win.fetch('/api/core',{method:'POST',headers:{'Content-Type':'application/json','Authorization':'Bearer '+t},body:JSON.stringify({action:'list',include_archived:true})}),
        win.fetch('/api/next',{method:'POST',headers:{'Content-Type':'application/json','Authorization':'Bearer '+t},body:JSON.stringify({action:'customers'})})
      ]);
      const [lj,cj]=await Promise.all([lr.json(),cr.json()]);
      if(lr.ok&&lj.ok&&Array.isArray(lj.licenses))licenses=lj.licenses;
      if(cr.ok&&cj.ok&&Array.isArray(cj.customers))customers=cj.customers;
    }catch{}
  }
  function hideOldCustomerDelete(){
    if(!doc)return;
    let style=doc.getElementById('s6MoveDeleteStyle');
    if(!style){
      style=doc.createElement('style');
      style.id='s6MoveDeleteStyle';
      style.textContent='#customerModal #s6DeleteCustomerBtn{display:none!important}#s6DeleteCustomerFromLicenseBtn{margin-right:auto!important;background:#4b1f27!important;border-color:#8b3644!important;color:#ffd9df!important}';
      doc.head.appendChild(style);
    }
  }
  function ensureLicenseDelete(){
    if(!doc)return;
    hideOldCustomerDelete();
    const footer=doc.querySelector('#licenseModal .modal-foot');
    if(!footer)return;
    let btn=doc.getElementById('s6DeleteCustomerFromLicenseBtn');
    if(!btn){
      btn=doc.createElement('button');
      btn.id='s6DeleteCustomerFromLicenseBtn';
      btn.type='button';
      btn.className='btn danger';
      btn.textContent='ลบบัญชีลูกค้า';
      footer.insertBefore(btn,footer.firstChild);
      btn.addEventListener('click',deleteLinkedCustomer);
    }
    const lic=licenses.find(x=>String(x.id)===String(currentLicenseId));
    const title=doc.getElementById('licenseModalTitle')?.textContent||'';
    btn.style.display=(currentLicenseId&&title.includes('แก้ไข')&&lic?.customer_id)?'':'none';
  }
  async function deleteLinkedCustomer(){
    if(!currentLicenseId)return;
    await loadData();
    const lic=licenses.find(x=>String(x.id)===String(currentLicenseId));
    const customerId=lic?.customer_id;
    if(!customerId){toast('License นี้ไม่ได้ผูกกับบัญชีลูกค้า',true);return}
    const c=customers.find(x=>String(x.id)===String(customerId));
    const name=c?.name||lic?.customer_name||'ลูกค้ารายนี้';
    if(!confirm('ยืนยันลบบัญชีลูกค้า "'+name+'" ?\n\nระบบจะลบเฉพาะ Customer Profile\nLicense และ License Key เดิมจะไม่ถูกลบ\nถ้าลูกค้านี้มีหลาย License ระบบจะถอดการผูก Customer Profile ออกจากทุก License'))return;
    const t=token();if(!t){toast('Session หมดอายุ กรุณาเข้าสู่ระบบใหม่',true);return}
    try{
      const r=await fetch(DELETE_API,{method:'POST',headers:{'Content-Type':'application/json','Authorization':'Bearer '+t},body:JSON.stringify({id:customerId})});
      const j=await r.json().catch(()=>({ok:false,error:'server_error'}));
      if(!r.ok||!j.ok)throw new Error(j.error||'server_error');
      doc.getElementById('licenseModal')?.classList.remove('open');
      toast('ลบบัญชีลูกค้าแล้ว');
      setTimeout(()=>win.location.reload(),350);
    }catch(e){toast('ลบบัญชีไม่สำเร็จ: '+(e.message||'server_error'),true)}
  }
  function wire(){
    if(!doc||doc.documentElement.dataset.s6MoveDelete==='1')return;
    doc.documentElement.dataset.s6MoveDelete='1';
    hideOldCustomerDelete();
    doc.addEventListener('click',e=>{
      const edit=e.target.closest?.('[data-edit-license]');
      if(edit){currentLicenseId=edit.dataset.editLicense;loadData().then(ensureLicenseDelete);setTimeout(ensureLicenseDelete,0)}
      const add=e.target.closest?.('#addLicenseBtn,[data-quick="addLicense"]');
      if(add){currentLicenseId=null;setTimeout(ensureLicenseDelete,0)}
    },true);
    const modal=doc.getElementById('licenseModal');
    if(modal)new MutationObserver(()=>ensureLicenseDelete()).observe(modal,{attributes:true,attributeFilter:['class']});
    new MutationObserver(()=>{hideOldCustomerDelete();ensureLicenseDelete()}).observe(doc.documentElement,{subtree:true,childList:true});
    loadData().then(ensureLicenseDelete);
  }
  function attach(){
    try{win=frame.contentWindow;doc=frame.contentDocument||win.document}catch{return}
    if(!doc)return;
    wire();
  }
  frame.addEventListener('load',()=>setTimeout(attach,700));
  if(frame.contentDocument?.readyState==='complete')setTimeout(attach,700);
})();
