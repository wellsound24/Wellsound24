(() => {
  const links = [...document.querySelectorAll('#main-nav a[href^="#"]')];
  const sections = links.map(a => document.querySelector(a.getAttribute('href'))).filter(Boolean);
  const updateSection = () => {
    const offset = document.querySelector('header').getBoundingClientRect().height + 30;
    let current = null;
    sections.slice().sort((a,b) => a.offsetTop-b.offsetTop).forEach(section => {
      if (section.getBoundingClientRect().top <= offset) current = section.id;
    });
    links.forEach(a => {
      if (a.hash === '#' + current) a.setAttribute('aria-current','location');
      else a.removeAttribute('aria-current');
    });
  };
  let pending = false;
  window.addEventListener('scroll', () => {
    if (!pending) { pending = true; requestAnimationFrame(() => {updateSection();pending=false;}); }
  }, {passive:true});
  window.addEventListener('resize',updateSection);
  window.addEventListener('load',updateSection);
  updateSection();

  const menu = document.querySelector('#main-nav');
  document.addEventListener('click', e => {
    if (!e.target.closest('header')) closeMenu();
  });
  window.matchMedia('(min-width:761px)').addEventListener('change', () => closeMenu());

  const modal = document.querySelector('#photo-dialog');
  const controls = document.createElement('div');
  controls.className = 'photo-controls';
  controls.innerHTML = '<button type="button" aria-label="ภาพก่อนหน้า">←</button><span aria-live="polite"></span><button type="button" aria-label="ภาพถัดไป">→</button>';
  modal.append(controls);
  let selected = 0, visiblePhotos = [];
  const renderPhoto = () => {
    const photo = visiblePhotos[selected].querySelector('img');
    modal.querySelector('img').src = photo.src;
    modal.querySelector('img').alt = photo.alt;
    modal.querySelector('.modal-caption').textContent = photo.alt;
    controls.querySelector('span').textContent = `${selected + 1} / ${visiblePhotos.length}`;
  };
  document.querySelectorAll('.photo-open').forEach(button => button.addEventListener('click', () => {
    visiblePhotos = [...document.querySelectorAll('.work-card:not([hidden]) .photo-open')];
    selected = visiblePhotos.indexOf(button);
    renderPhoto();
  }));
  const move = step => { if (!visiblePhotos.length) return; selected = (selected + step + visiblePhotos.length) % visiblePhotos.length; renderPhoto(); };
  controls.querySelector('button:first-child').addEventListener('click', () => move(-1));
  controls.querySelector('button:last-child').addEventListener('click', () => move(1));
  modal.addEventListener('keydown', e => {
    if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') { e.preventDefault();move(e.key === 'ArrowLeft' ? -1 : 1); }
  });

  const form = document.createElement('form');
  form.className = 'enquiry';
  form.innerHTML = `
    <h3>สรุปรายละเอียดงานของคุณ</h3>
    <p class="hint">กรอกแล้วคัดลอกข้อความไปส่งทาง LINE หรือ Facebook ได้เลย แบบฟอร์มนี้ยังไม่ส่งข้อมูลถึงทีมงาน</p>
    <label for="event-type">ประเภทงาน *</label>
    <select id="event-type" name="type" required><option value="">เลือกประเภทงาน</option><option>งานอีเวนต์</option><option>คอนเสิร์ต / มินิคอนเสิร์ต</option><option>งานเลี้ยง / งานแต่งงาน</option><option>อื่น ๆ</option></select>
    <label for="event-date">วันที่จัดงาน (ถ้าทราบ)</label><input id="event-date" name="date" type="date">
    <label for="event-place">สถานที่จัดงาน *</label><input id="event-place" name="place" required maxlength="200" placeholder="ชื่อสถานที่ / อำเภอ / จังหวัด">
    <label for="event-people">จำนวนผู้ร่วมงานโดยประมาณ</label><input id="event-people" name="people" type="number" min="1" max="1000000" inputmode="numeric" placeholder="เช่น 200">
    <label for="event-detail">อุปกรณ์ที่ต้องการและงบประมาณ</label><textarea id="event-detail" name="details" maxlength="2000" placeholder="เช่น เครื่องเสียง แสงไฟ จอ LED และงบประมาณที่วางไว้"></textarea>
    <button class="button" type="submit">คัดลอกรายละเอียดงาน</button>
    <p role="status" aria-live="polite"></p>
    <div class="request-preview" hidden><label for="request-text">ข้อความสำหรับส่งถึงทีมงาน</label><textarea id="request-text" readonly rows="8"></textarea></div>`;
  document.querySelector('.brief').append(form);
  form.addEventListener('submit', async e => {
    e.preventDefault();
    const data = new FormData(form);
    const place = data.get('place').trim();
    if (!place) {form.querySelector('[name=place]').focus();return;}
    const message = ['สวัสดีครับ/ค่ะ สนใจบริการ Wellsound24',`ประเภทงาน: ${data.get('type')}`,`วันที่: ${data.get('date') || 'ยังไม่กำหนด'}`,`สถานที่: ${place}`,`ผู้ร่วมงาน: ${data.get('people') || 'ยังไม่ทราบ'}`,`รายละเอียด: ${data.get('details').trim() || 'ขอปรึกษาทีมงาน'}`].join('\n');
    form.querySelector('.request-preview').hidden = false;
    form.querySelector('#request-text').value = message;
    try {
      await navigator.clipboard.writeText(message);
      form.querySelector('[role=status]').textContent = 'คัดลอกแล้ว กรุณาเปิด LINE หรือ Facebook แล้ววางข้อความส่งถึงทีมงาน';
    } catch {
      form.querySelector('[role=status]').textContent = 'เลือกข้อความด้านล่างแล้วคัดลอก จากนั้นนำไปส่งทาง LINE หรือ Facebook';
      form.querySelector('#request-text').focus();
      form.querySelector('#request-text').select();
    }
  });
})();