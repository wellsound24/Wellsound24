(()=>{
  const frame=document.getElementById('dash');
  const GROUP_API='https://sjcxywxixgrpdgeqaepk.supabase.co/functions/v1/admin-ea-groups';
  let doc=null,win=null,lastStamp='',busy=false;

  function token(){
    try{return win?.sessionStorage.getItem('well_admin_session')||''}catch{return''}
  }

  async function post(url,body){
    const t=token();
    if(!t)throw new Error('unauthorized');
    const r=await fetch(url,{method:'POST',headers:{'Content-Type':'application/json','Authorization':'Bearer '+t},body:JSON.stringify(body)});
    const j=await r.json().catch(()=>({ok:false,error:'server_error'}));
    if(!r.ok||!j.ok)throw new Error(j.error||'server_error');
    return j;
  }

  async function nextApi(action,payload={}){
    const t=token();
    if(!t)throw new Error('unauthorized');
    const r=await win.fetch('/api/next',{method:'POST',headers:{'Content-Type':'application/json','Authorization':'Bearer '+t},body:JSON.stringify({action,...payload})});
    const j=await r.json().catch(()=>({ok:false,error:'server_error'}));
    if(!r.ok||!j.ok)throw new Error(j.error||'server_error');
    return j;
  }

  function injectStyle(){
    if(!doc||doc.getElementById('s6DashboardGroupsStyle'))return;
    const s=doc.createElement('style');
    s.id='s6DashboardGroupsStyle';
    s.textContent=`
      .s6-dashboard-groups{display:grid;gap:8px;padding:0 14px 14px;max-height:260px;overflow:auto}
      .s6-dashboard-group{border:1px solid #26384f;border-radius:11px;background:#0d1725;padding:10px 11px}
      .s6-dashboard-group-head{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:7px}
      .s6-dashboard-group-name{font-size:12px;font-weight:800;color:#e5eef9;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
      .s6-dashboard-group-count{font-size:10px;color:#8fa0b7;white-space:nowrap}
      .s6-dashboard-members{display:flex;gap:5px;flex-wrap:wrap}
      .s6-dashboard-member{font-size:10px;line-height:1.2;padding:4px 7px;border-radius:999px;border:1px solid #28445a;background:#102033;color:#b8d2e7;max-width:220px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
      .s6-dashboard-empty-member{font-size:10px;color:#78889d}
      .s6-dashboard-loading{padding:2px 14px 14px;color:#8393a7;font-size:11px}
      @media(max-width:760px){.s6-dashboard-groups{max-height:none}.s6-dashboard-group-head{align-items:flex-start}.s6-dashboard-member{max-width:100%}}
    `;
    doc.head.appendChild(s);
  }

  function findGroupPanel(){
    if(!doc)return null;
    const titles=[...doc.querySelectorAll('.panel-title,h2,h3,strong,b')];
    const title=titles.find(x=>(x.textContent||'').trim()==='รายการกลุ่ม');
    if(!title)return null;
    return title.closest('.panel')||title.parentElement?.parentElement||null;
  }

  function ensureHost(){
    const panel=findGroupPanel();
    if(!panel)return null;
    injectStyle();
    let host=doc.getElementById('s6DashboardGroupList');
    if(!host){
      host=doc.createElement('div');
      host.id='s6DashboardGroupList';
      host.className='s6-dashboard-groups';
      const head=panel.querySelector('.panel-head');
      if(head)head.insertAdjacentElement('afterend',host);else panel.appendChild(host);
    }
    [...panel.querySelectorAll('.empty')].forEach(el=>{if(el!==host&&!host.contains(el))el.style.display='none'});
    return host;
  }

  function customerName(customerId,customers){
    const c=customers.find(x=>String(x.id)===String(customerId));
    return (c?.name||c?.email||c?.phone||'ลูกค้าไม่ทราบชื่อ').trim();
  }

  function render(groups,memberships,customers){
    const host=ensureHost();
    if(!host)return;
    if(!groups.length){
      host.innerHTML='<div class="s6-dashboard-loading">ยังไม่มีกลุ่ม EA</div>';
      return;
    }
    host.innerHTML='';
    for(const g of groups){
      const memberIds=memberships.filter(m=>String(m.group_id)===String(g.id)).map(m=>String(m.customer_id));
      const names=memberIds.map(id=>customerName(id,customers));
      const card=doc.createElement('div');
      card.className='s6-dashboard-group';
      const head=doc.createElement('div');
      head.className='s6-dashboard-group-head';
      const name=doc.createElement('div');
      name.className='s6-dashboard-group-name';
      name.textContent=g.name||'ไม่ระบุชื่อกลุ่ม';
      const count=doc.createElement('div');
      count.className='s6-dashboard-group-count';
      count.textContent=names.length+' ลูกค้า';
      head.append(name,count);
      const members=doc.createElement('div');
      members.className='s6-dashboard-members';
      if(!names.length){
        const empty=doc.createElement('span');
        empty.className='s6-dashboard-empty-member';
        empty.textContent='ยังไม่มีลูกค้าในกลุ่ม';
        members.appendChild(empty);
      }else{
        for(const n of names){
          const chip=doc.createElement('span');
          chip.className='s6-dashboard-member';
          chip.textContent=n;
          members.appendChild(chip);
        }
      }
      card.append(head,members);
      host.appendChild(card);
    }
  }

  async function refresh(force=false){
    if(busy||!doc||!win||!token())return;
    const host=ensureHost();
    if(host&&!host.childElementCount)host.innerHTML='<div class="s6-dashboard-loading">กำลังโหลดกลุ่ม...</div>';
    busy=true;
    try{
      const [gj,cj]=await Promise.all([post(GROUP_API,{action:'list'}),nextApi('customers')]);
      const groups=Array.isArray(gj.groups)?gj.groups:[];
      const memberships=Array.isArray(gj.memberships)?gj.memberships:[];
      const customers=Array.isArray(cj.customers)?cj.customers:[];
      const stamp=JSON.stringify([groups.map(g=>[g.id,g.name,g.customer_count]),memberships.map(m=>[m.group_id,m.customer_id]),customers.map(c=>[c.id,c.name,c.email,c.phone])]);
      if(force||stamp!==lastStamp){lastStamp=stamp;render(groups,memberships,customers)}
    }catch(e){
      const host=ensureHost();
      if(host&&e.message!=='unauthorized')host.innerHTML='<div class="s6-dashboard-loading">โหลดรายการกลุ่มไม่สำเร็จ</div>';
    }finally{busy=false}
  }

  function wire(){
    if(!doc||doc.documentElement.dataset.s6DashboardGroups==='1')return;
    doc.documentElement.dataset.s6DashboardGroups='1';
    injectStyle();
    ensureHost();
    doc.addEventListener('click',e=>{
      const target=e.target.closest?.('#s6AddGroupBtn,[data-s6-rename-group],[data-s6-delete-group],.nav-btn[data-view="overview"],.nav-btn[data-view="customers"],.nav-btn[data-view="settings"]');
      if(target)setTimeout(()=>refresh(true),700);
    },true);
    const customerForm=doc.getElementById('customerForm');
    if(customerForm)customerForm.addEventListener('submit',()=>setTimeout(()=>refresh(true),900),true);
    new MutationObserver(()=>{ensureHost()}).observe(doc.body,{childList:true,subtree:true});
    refresh(true);
    setInterval(()=>refresh(false),10000);
  }

  function attach(){
    try{win=frame.contentWindow;doc=frame.contentDocument||win.document}catch{return}
    if(!doc)return;
    wire();
  }

  frame.addEventListener('load',()=>setTimeout(attach,900));
  if(frame.contentDocument?.readyState==='complete')setTimeout(attach,900);
})();
