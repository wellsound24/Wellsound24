(()=>{
  const frame=document.getElementById('dash');
  function attach(){
    let doc=null;
    try{doc=frame.contentDocument||frame.contentWindow?.document}catch{return}
    if(!doc||doc.getElementById('s6CustomerGroupSelectorRestore'))return;
    const s=doc.createElement('style');
    s.id='s6CustomerGroupSelectorRestore';
    s.textContent='html body #customerModal #s6CustomerGroupsField{display:block!important}';
    doc.head.appendChild(s);
  }
  frame.addEventListener('load',()=>setTimeout(attach,1400));
  if(frame.contentDocument?.readyState==='complete')setTimeout(attach,1400);
})();
