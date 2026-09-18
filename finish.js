(() => {
 const updateSection=()=>{const links=[...document.querySelectorAll('#main-nav a[href^="#"]')];let current='';for(const a of links){const section=document.getElementById(a.hash.slice(1));if(section&&section.getBoundingClientRect().top<=130)current=a.hash;}for(const a of links){if(a.hash===current)a.setAttribute('aria-current','location');else a.removeAttribute('aria-current');}};
 window.addEventListener('scroll',updateSection,{passive:true});window.addEventListener('load',updateSection);
 document.addEventListener('click',e=>{if(e.target.closest('#main-nav a')||!e.target.closest('header'))closeMenu();});
 window.matchMedia('(min-width:761px)').addEventListener('change',()=>closeMenu());
 const modal=document.querySelector('#photo-dialog'),controls=document.createElement('div');controls.className='photo-controls';controls.innerHTML='<button type="button" aria-label="ภาพก่อนหน้า">←</button><span aria-live="polite"></span><button type="button" aria-label="ภาพถัดไป">→</button>';modal.append(controls);
 let selected=0,photos=[];
 const render=()=>{const img=photos[selected];if(!img)return;modal.querySelector('img').src=img.src;modal.querySelector('img').alt=img.alt;modal.querySelector('.modal-caption').textContent=img.alt;controls.querySelector('span').textContent=`${selected+1} / ${photos.length}`;};
 document.addEventListener('click',e=>{const b=e.target.closest('.photo-open');if(!b)return;photos=[...document.querySelectorAll('.work-card:not([hidden]) .photo-open img')];selected=photos.indexOf(b.querySelector('img'));render();});
 const move=n=>{if(!photos.length)return;selected=(selected+n+photos.length)%photos.length;render();};controls.firstElementChild.onclick=()=>move(-1);controls.lastElementChild.onclick=()=>move(1);modal.addEventListener('keydown',e=>{if(e.key==='ArrowLeft'||e.key==='ArrowRight'){e.preventDefault();move(e.key==='ArrowLeft'?-1:1);}});
})();
