(() => {
  "use strict";

  const DEFAULTS = window.WELLSOUND_DEFAULTS || {};
  const STORAGE_KEY = window.WELLSOUND_STORAGE_KEY || "wellsound24_site_content_v2";
  const OLD_STORAGE_KEY = "wellsound24_site_content_v1";
  const PIN_KEY = window.WELLSOUND_PIN_KEY || "wellsound24_admin_pin";
  const DEFAULT_PIN = "2468";

  const clone = (value) => value === undefined ? undefined : JSON.parse(JSON.stringify(value));
  const isObject = (value) => value && typeof value === "object" && !Array.isArray(value);

  function deepMerge(base, override) {
    if (Array.isArray(base)) return Array.isArray(override) ? clone(override) : clone(base);
    if (!isObject(base)) return override === undefined ? base : override;
    const result = { ...base };
    if (!isObject(override)) return result;
    Object.keys(override).forEach((key) => {
      result[key] = key in base ? deepMerge(base[key], override[key]) : clone(override[key]);
    });
    return result;
  }

  function loadSaved() {
    try {
      const current = JSON.parse(localStorage.getItem(STORAGE_KEY) || "null");
      if (current) return deepMerge(DEFAULTS, current);
      const old = JSON.parse(localStorage.getItem(OLD_STORAGE_KEY) || "null");
      if (old) return deepMerge(DEFAULTS, old);
    } catch (error) {
      console.warn("โหลดข้อมูลเดิมไม่สำเร็จ", error);
    }
    return clone(DEFAULTS);
  }

  function getByPath(object, path) {
    return String(path).split(".").reduce((value, key) => value?.[key], object);
  }

  function setByPath(object, path, value) {
    const parts = String(path).split(".");
    let target = object;
    parts.forEach((part, index) => {
      if (index === parts.length - 1) {
        target[part] = value;
        return;
      }
      const nextIsIndex = /^\d+$/.test(parts[index + 1]);
      if (target[part] === undefined || target[part] === null) target[part] = nextIsIndex ? [] : {};
      target = target[part];
    });
  }

  function normalizeLineLink(value) {
    let link = String(value || "").trim();
    if (!link || link === "#") return "";
    if (/^(lin\.ee|line\.me)\//i.test(link)) link = `https://${link}`;
    if (/^line:\/\//i.test(link)) return link;
    if (/^https?:\/\//i.test(link)) return link;
    return "";
  }

  function resolveLineUrl(site = {}) {
    const direct = normalizeLineLink(site.lineUrl);
    if (direct) return direct;

    let lineId = String(site.lineId || "").trim();
    const lineIdAsLink = normalizeLineLink(lineId);
    if (lineIdAsLink) return lineIdAsLink;

    lineId = lineId.replace(/^@+/, "").replace(/\s+/g, "");
    if (lineId) return `https://line.me/R/ti/p/@${encodeURIComponent(lineId)}`;

    const linePhone = String(site.linePhone || "").replace(/\D/g, "");
    if (linePhone) return `https://line.me/R/ti/p/~${encodeURIComponent(linePhone)}`;
    return "";
  }

  function resolveFacebookUrl(site = {}) {
    let link = String(site.facebookUrl || "").trim();
    if (!link || link === "#") return "";
    if (/^(facebook\.com|www\.facebook\.com|m\.facebook\.com|fb\.com)\//i.test(link)) {
      link = `https://${link}`;
    }
    if (/^https?:\/\//i.test(link)) return link;
    const pageName = link.replace(/^@+/, "").replace(/\s+/g, "");
    return pageName ? `https://www.facebook.com/${encodeURIComponent(pageName)}` : "";
  }

  const loginScreen = document.querySelector("#login-screen");
  const loginForm = document.querySelector("#login-form");
  const pinInput = document.querySelector("#pin-input");
  const loginMessage = document.querySelector("#login-message");
  const dashboard = document.querySelector("#dashboard");
  const form = document.querySelector("#content-form");
  const previewFrame = document.querySelector("#preview-frame");
  const panelTitle = document.querySelector("#panel-title");
  const statusBar = document.querySelector("#status-bar");
  const saveButton = document.querySelector("#save-button");
  const undoButton = document.querySelector("#undo-button");
  const redoButton = document.querySelector("#redo-button");
  const publishGithubTopButton = document.querySelector("#publish-github-top-button");
  const publishGithubButton = document.querySelector("#publish-github-button");

  const panelNames = {
    visual: "แก้จากตัวอย่าง",
    layout: "โครงสร้างหน้าเว็บ",
    general: "ข้อมูลทั่วไป",
    header: "หัวเว็บและเมนู",
    hero: "หน้าแรก",
    services: "บริการ",
    why: "จุดเด่น",
    portfolio: "ผลงาน",
    contact: "ติดต่อและฟอร์ม",
    footer: "ท้ายเว็บไซต์",
    appearance: "สีและรูปแบบ",
    backup: "บันทึกและสำรอง"
  };

  const sectionMeta = {
    hero: ["หน้าแรก", "ภาพปก หัวข้อ ปุ่ม และจุดเด่น"],
    services: ["บริการ", "รายการบริการทั้งหมด"],
    why: ["จุดเด่น", "เหตุผลที่ลูกค้าควรเลือก Wellsound24"],
    portfolio: ["ผลงาน", "รูปผลงานและประเภทงาน"],
    contact: ["ติดต่อ", "ข้อมูลติดต่อและแบบฟอร์มขอราคา"]
  };

  let draft = loadSaved();
  let selectedPath = "";
  let selectedEditType = "";
  let activeStyleDevice = "desktop";
  let previewTimer;
  let historyTimer;
  let history = [clone(draft)];
  let historyIndex = 0;

  function currentPin() {
    return localStorage.getItem(PIN_KEY) || DEFAULT_PIN;
  }

  function setStatus(message, type = "") {
    statusBar.textContent = message;
    statusBar.classList.toggle("is-success", type === "success");
    statusBar.classList.toggle("is-error", type === "error");
  }

  function unlockDashboard() {
    sessionStorage.setItem("wellsound24_admin_unlocked", "1");
    loginScreen.classList.add("is-hidden");
    dashboard.classList.remove("is-hidden");
    renderForm();
    setTimeout(sendPreview, 250);
  }

  loginForm.addEventListener("submit", (event) => {
    event.preventDefault();
    if (pinInput.value === currentPin()) {
      loginMessage.textContent = "";
      unlockDashboard();
    } else {
      loginMessage.textContent = "รหัสไม่ถูกต้อง กรุณาลองใหม่";
      pinInput.select();
    }
  });

  if (sessionStorage.getItem("wellsound24_admin_unlocked") === "1") unlockDashboard();

  document.querySelector("#logout-button").addEventListener("click", () => {
    sessionStorage.removeItem("wellsound24_admin_unlocked");
    location.reload();
  });

  function activatePanel(name, smooth = true) {
    document.querySelectorAll(".nav-item").forEach((item) => item.classList.toggle("is-active", item.dataset.panel === name));
    document.querySelectorAll(".panel").forEach((panel) => panel.classList.toggle("is-active", panel.dataset.panelContent === name));
    panelTitle.textContent = panelNames[name] || "จัดการเว็บไซต์";
    if (smooth) window.scrollTo({ top: 0, behavior: "smooth" });
  }

  document.querySelectorAll(".nav-item").forEach((button) => {
    button.addEventListener("click", () => activatePanel(button.dataset.panel));
  });

  function queuePreview() {
    clearTimeout(previewTimer);
    previewTimer = setTimeout(sendPreview, 90);
  }

  function sendPreview() {
    previewFrame.contentWindow?.postMessage({ type: "wellsound-preview", content: draft, device: activeStyleDevice }, "*");
  }

  previewFrame.addEventListener("load", () => setTimeout(sendPreview, 130));
  document.querySelector("#refresh-preview").addEventListener("click", () => {
    previewFrame.src = `index.html?editor=1&t=${Date.now()}`;
  });

  document.querySelectorAll("[data-device]").forEach((button) => {
    button.addEventListener("click", () => {
      document.querySelectorAll("[data-device]").forEach((item) => item.classList.remove("is-active"));
      button.classList.add("is-active");
      document.querySelector("[data-device-stage]").dataset.deviceStage = button.dataset.device;
      activeStyleDevice = button.dataset.device;
      document.querySelectorAll("[data-style-device]").forEach((item) => item.classList.toggle("is-active", item.dataset.styleDevice === activeStyleDevice));
      renderSelectedTextStyle();
      setTimeout(sendPreview, 80);
    });
  });

  function updateHistoryButtons() {
    undoButton.disabled = historyIndex <= 0;
    redoButton.disabled = historyIndex >= history.length - 1;
  }

  function pushHistory() {
    clearTimeout(historyTimer);
    const snapshot = clone(draft);
    const currentJson = JSON.stringify(history[historyIndex]);
    const nextJson = JSON.stringify(snapshot);
    if (currentJson === nextJson) return;
    history = history.slice(0, historyIndex + 1);
    history.push(snapshot);
    if (history.length > 60) history.shift();
    historyIndex = history.length - 1;
    updateHistoryButtons();
  }

  function queueHistory() {
    clearTimeout(historyTimer);
    historyTimer = setTimeout(pushHistory, 450);
  }

  function restoreHistory(index) {
    clearTimeout(historyTimer);
    if (index < 0 || index >= history.length) return;
    historyIndex = index;
    draft = clone(history[historyIndex]);
    renderForm();
    sendPreview();
    updateHistoryButtons();
    setStatus("ย้อนกลับ/ทำซ้ำการแก้ไขแล้ว");
  }

  undoButton.addEventListener("click", () => {
    pushHistory();
    restoreHistory(historyIndex - 1);
  });
  redoButton.addEventListener("click", () => restoreHistory(historyIndex + 1));

  function markChanged(message = "มีการแก้ไขที่ยังไม่ได้บันทึก") {
    setStatus(message);
    queuePreview();
    queueHistory();
  }

  function actionButtons(path, index, length) {
    return `<div class="list-card-actions">
      <button type="button" class="mini-button" data-list-action="up" data-list-path="${path}" data-index="${index}" ${index === 0 ? "disabled" : ""} title="เลื่อนขึ้น">↑</button>
      <button type="button" class="mini-button" data-list-action="down" data-list-path="${path}" data-index="${index}" ${index === length - 1 ? "disabled" : ""} title="เลื่อนลง">↓</button>
      <button type="button" class="mini-button danger" data-list-action="remove" data-list-path="${path}" data-index="${index}" title="ลบ">×</button>
    </div>`;
  }

  function renderSections() {
    const container = document.querySelector("#section-order-fields");
    const order = draft.layout.sectionOrder || [];
    container.innerHTML = order.map((id, index) => {
      const [title, description] = sectionMeta[id] || [id, ""];
      return `<article class="section-order-card">
        <div class="section-order-number">${index + 1}</div>
        <div><h3>${title}</h3><p>${description}</p></div>
        <div class="section-order-actions">
          <label class="toggle-card compact"><input type="checkbox" data-path="layout.visibility.${id}"><span><strong>แสดง</strong></span></label>
          <button type="button" class="mini-button" data-section-action="up" data-index="${index}" ${index === 0 ? "disabled" : ""}>↑</button>
          <button type="button" class="mini-button" data-section-action="down" data-index="${index}" ${index === order.length - 1 ? "disabled" : ""}>↓</button>
        </div>
      </article>`;
    }).join("");
  }

  function renderMenuFields() {
    const items = draft.header.menu || [];
    document.querySelector("#menu-fields").innerHTML = items.map((item, index) => `<article class="list-card">
      <div class="list-card-head"><strong>เมนูที่ ${index + 1}</strong>${actionButtons("header.menu", index, items.length)}</div>
      <div class="list-card-body"><div class="form-grid two-columns">
        <label class="field">ชื่อเมนู<input type="text" data-path="header.menu.${index}.label"></label>
        <label class="field">ลิงก์/ตำแหน่ง<input type="text" data-path="header.menu.${index}.target" placeholder="#contact"></label>
      </div></div>
    </article>`).join("");
  }

  function renderStatsFields() {
    const items = draft.stats || [];
    document.querySelector("#stats-fields").innerHTML = items.map((item, index) => `<article class="list-card">
      <div class="list-card-head"><strong>ช่องที่ ${index + 1}</strong>${actionButtons("stats", index, items.length)}</div>
      <div class="list-card-body"><div class="form-grid two-columns">
        <label class="field">หัวข้อ<input type="text" data-path="stats.${index}.title"></label>
        <label class="field">รายละเอียด<input type="text" data-path="stats.${index}.text"></label>
      </div></div>
    </article>`).join("");
  }

  function renderServiceFields() {
    const items = draft.services || [];
    document.querySelector("#service-fields").innerHTML = items.map((item, index) => `<article class="list-card">
      <div class="list-card-head"><strong>บริการที่ ${index + 1}</strong>${actionButtons("services", index, items.length)}</div>
      <div class="list-card-body"><div class="form-grid two-columns">
        <label class="field">ไอคอน<input type="text" maxlength="4" data-path="services.${index}.icon"></label>
        <label class="field">ชื่อบริการ<input type="text" data-path="services.${index}.title"></label>
        <label class="field full-width">รายละเอียด<textarea rows="3" data-path="services.${index}.description"></textarea></label>
      </div></div>
    </article>`).join("");
  }

  function renderWhyFields() {
    const items = draft.why.items || [];
    document.querySelector("#why-fields").innerHTML = items.map((item, index) => `<article class="list-card">
      <div class="list-card-head"><strong>รายการที่ ${index + 1}</strong>${actionButtons("why.items", index, items.length)}</div>
      <div class="list-card-body"><label class="field">ข้อความ<input type="text" data-path="why.items.${index}"></label></div>
    </article>`).join("");
  }

  function renderPortfolioFields() {
    const items = draft.portfolio || [];
    document.querySelector("#portfolio-fields").innerHTML = items.map((item, index) => `<article class="list-card">
      <div class="list-card-head"><strong>ผลงานที่ ${index + 1}</strong>${actionButtons("portfolio", index, items.length)}</div>
      <div class="list-card-body">
        <div class="form-grid two-columns">
          <label class="field">ข้อความภาษาอังกฤษ<input type="text" data-path="portfolio.${index}.label"></label>
          <label class="field">ชื่อผลงาน<input type="text" data-path="portfolio.${index}.title"></label>
          <label class="field full-width">คำอธิบายสั้น<input type="text" data-path="portfolio.${index}.description"></label>
          <label class="field">ลิงก์เมื่อคลิก<input type="text" data-path="portfolio.${index}.link"></label>
          <label class="field">ตำแหน่งรูป<select data-path="portfolio.${index}.imagePosition"><option value="left center">ซ้าย</option><option value="center center">กลาง</option><option value="right center">ขวา</option><option value="center top">ด้านบน</option><option value="center bottom">ด้านล่าง</option></select></label>
        </div>
        <div class="list-card-image" data-image-path="portfolio.${index}.image">
          <div class="image-preview"></div>
          <div><p>รูปผลงาน แนะนำขนาด 1200 × 900</p><div class="image-actions"><label class="upload-button">เลือกรูป<input type="file" accept="image/*" hidden></label><button type="button" class="danger-text remove-image">ลบรูป</button></div></div>
        </div>
      </div>
    </article>`).join("");
  }

  function renderEventTypeFields() {
    const items = draft.contact.form.eventTypes || [];
    document.querySelector("#event-type-fields").innerHTML = items.map((item, index) => `<article class="list-card">
      <div class="list-card-head"><strong>ประเภทที่ ${index + 1}</strong>${actionButtons("contact.form.eventTypes", index, items.length)}</div>
      <div class="list-card-body"><label class="field">ชื่อประเภทงาน<input type="text" data-path="contact.form.eventTypes.${index}"></label></div>
    </article>`).join("");
  }

  function renderDynamicFields() {
    renderSections();
    renderMenuFields();
    renderStatsFields();
    renderServiceFields();
    renderWhyFields();
    renderPortfolioFields();
    renderEventTypeFields();
  }

  function normalizeColor(value, fallback = "#000000") {
    return /^#[0-9a-f]{6}$/i.test(value || "") ? value : fallback;
  }

  function renderImagePreviews() {
    document.querySelectorAll("[data-image-path]").forEach((editor) => {
      const preview = editor.querySelector(".image-preview");
      if (!preview) return;
      const image = getByPath(draft, editor.dataset.imagePath);
      preview.style.backgroundImage = image ? `url(${JSON.stringify(image)})` : "";
      preview.classList.toggle("is-empty", !image);
    });
  }

  function fillFields() {
    form.querySelectorAll("[data-path]").forEach((field) => {
      const value = getByPath(draft, field.dataset.path);
      if (field.type === "checkbox") field.checked = Boolean(value);
      else if (field.type === "color") field.value = normalizeColor(value, "#000000");
      else field.value = value ?? "";
      const output = document.querySelector(`[data-output-for="${field.dataset.path}"]`);
      if (output) output.textContent = field.value;
    });
    document.querySelectorAll("[data-color-text]").forEach((field) => {
      field.value = getByPath(draft, field.dataset.colorText) || "";
    });
    renderImagePreviews();
  }

  function valueFromField(field) {
    if (field.type === "checkbox") return field.checked;
    const oldValue = getByPath(draft, field.dataset.path);
    if (field.type === "number" || field.type === "range" || typeof oldValue === "number") {
      const number = Number(field.value);
      return Number.isFinite(number) ? number : 0;
    }
    return field.value;
  }

  function bindFields() {
    form.querySelectorAll("[data-path]").forEach((field) => {
      const handler = () => {
        const path = field.dataset.path;
        const value = valueFromField(field);
        setByPath(draft, path, value);

        // ช่องข้อมูลเดียวกันอาจอยู่มากกว่าหนึ่งเมนู เช่น เบอร์โทร
        // จึงอัปเดตให้ทุกช่องที่ใช้ data-path เดียวกันแสดงค่าเดียวกันทันที
        form.querySelectorAll("[data-path]").forEach((otherField) => {
          if (otherField === field || otherField.dataset.path !== path) return;
          if (otherField.type === "checkbox") otherField.checked = Boolean(value);
          else otherField.value = value ?? "";
        });

        const colorText = document.querySelector(`[data-color-text="${path}"]`);
        if (field.type === "color" && colorText) colorText.value = field.value;
        const output = document.querySelector(`[data-output-for="${path}"]`);
        if (output) output.textContent = field.value;
        markChanged();
      };
      field.oninput = handler;
      field.onchange = handler;
    });

    document.querySelectorAll("[data-color-text]").forEach((field) => {
      field.onchange = () => {
        const path = field.dataset.colorText;
        const color = normalizeColor(field.value, getByPath(draft, path) || "#000000");
        field.value = color;
        setByPath(draft, path, color);
        const picker = [...document.querySelectorAll("[data-path]")].find((item) => item.dataset.path === path && item.type === "color");
        if (picker) picker.value = color;
        markChanged();
      };
    });
  }

  function compressImage(file) {
    return new Promise((resolve, reject) => {
      if (!file.type.startsWith("image/")) return reject(new Error("Not an image"));
      const reader = new FileReader();
      reader.onerror = reject;
      reader.onload = () => {
        const image = new Image();
        image.onerror = reject;
        image.onload = () => {
          const maxDimension = 1800;
          const scale = Math.min(1, maxDimension / Math.max(image.width, image.height));
          const width = Math.max(1, Math.round(image.width * scale));
          const height = Math.max(1, Math.round(image.height * scale));
          const canvas = document.createElement("canvas");
          canvas.width = width;
          canvas.height = height;
          const context = canvas.getContext("2d");
          context.drawImage(image, 0, 0, width, height);
          const outputType = file.type === "image/png" && file.size < 650000 ? "image/png" : "image/jpeg";
          resolve(canvas.toDataURL(outputType, outputType === "image/png" ? undefined : .8));
        };
        image.src = reader.result;
      };
      reader.readAsDataURL(file);
    });
  }

  function bindImageEditors() {
    document.querySelectorAll("[data-image-path] input[type='file']").forEach((input) => {
      input.onchange = async () => {
        const file = input.files?.[0];
        if (!file) return;
        const editor = input.closest("[data-image-path]");
        try {
          setStatus("กำลังปรับขนาดรูป...");
          const dataUrl = await compressImage(file);
          setByPath(draft, editor.dataset.imagePath, dataUrl);
          renderImagePreviews();
          queuePreview();
          pushHistory();
          setStatus("เพิ่มรูปแล้ว กรุณากดบันทึก", "success");
        } catch (error) {
          console.error(error);
          setStatus("เปิดรูปไม่ได้ กรุณาเลือกรูป JPG หรือ PNG", "error");
        } finally {
          input.value = "";
        }
      };
    });

    document.querySelectorAll("[data-image-path] .remove-image").forEach((button) => {
      button.onclick = () => {
        const editor = button.closest("[data-image-path]");
        const path = editor.dataset.imagePath;
        setByPath(draft, path, getByPath(DEFAULTS, path) || "");
        renderImagePreviews();
        sendPreview();
        pushHistory();
        setStatus("คืนค่ารูปแล้ว กรุณากดบันทึก");
      };
    });
  }

  const newItems = {
    "header.menu": () => ({ label: "เมนูใหม่", target: "#contact" }),
    stats: () => ({ title: "จุดเด่นใหม่", text: "รายละเอียดจุดเด่น" }),
    services: () => ({ icon: "✦", title: "บริการใหม่", description: "รายละเอียดบริการ" }),
    "why.items": () => "รายการจุดเด่นใหม่",
    portfolio: () => ({ label: "EVENT", title: "ผลงานใหม่", description: "", image: "", imagePosition: "center center", link: "#contact" }),
    "contact.form.eventTypes": () => "ประเภทงานใหม่"
  };

  function remapListTextStyles(path, action, index) {
    if (!draft.textStyles || typeof draft.textStyles !== "object") return;
    const escaped = path.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    const pattern = new RegExp(`^${escaped}\\.(\\d+)(?=\\.|$)`);
    const next = {};
    Object.entries(draft.textStyles).forEach(([key, value]) => {
      const match = key.match(pattern);
      if (!match) { next[key] = value; return; }
      const itemIndex = Number(match[1]);
      let newIndex = itemIndex;
      if (action === "up") {
        if (itemIndex === index) newIndex = index - 1;
        else if (itemIndex === index - 1) newIndex = index;
      } else if (action === "down") {
        if (itemIndex === index) newIndex = index + 1;
        else if (itemIndex === index + 1) newIndex = index;
      } else if (action === "remove") {
        if (itemIndex === index) return;
        if (itemIndex > index) newIndex = itemIndex - 1;
      }
      const newKey = key.replace(pattern, `${path}.${newIndex}`);
      next[newKey] = value;
      if (selectedPath === key) selectedPath = newKey;
    });
    draft.textStyles = next;
  }

  function mutateList(path, action, index) {
    const list = getByPath(draft, path);
    if (!Array.isArray(list)) return;
    if (action === "up" && index > 0) {
      remapListTextStyles(path, action, index);
      [list[index - 1], list[index]] = [list[index], list[index - 1]];
    }
    if (action === "down" && index < list.length - 1) {
      remapListTextStyles(path, action, index);
      [list[index + 1], list[index]] = [list[index], list[index + 1]];
    }
    if (action === "remove") {
      if (list.length <= 1 && !confirm("รายการนี้เป็นรายการสุดท้าย ต้องการลบหรือไม่?")) return;
      remapListTextStyles(path, action, index);
      list.splice(index, 1);
    }
    renderForm();
    sendPreview();
    pushHistory();
    setStatus("แก้ลำดับรายการแล้ว กรุณากดบันทึก");
  }

  function bindListActions() {
    document.querySelectorAll("[data-add-list]").forEach((button) => {
      button.onclick = () => {
        const path = button.dataset.addList;
        const list = getByPath(draft, path);
        const factory = newItems[path];
        if (!Array.isArray(list) || !factory) return;
        list.push(factory());
        renderForm();
        sendPreview();
        pushHistory();
        setStatus("เพิ่มรายการแล้ว กรุณากดบันทึก", "success");
      };
    });

    document.querySelectorAll("[data-list-action]").forEach((button) => {
      button.onclick = () => mutateList(button.dataset.listPath, button.dataset.listAction, Number(button.dataset.index));
    });

    document.querySelectorAll("[data-section-action]").forEach((button) => {
      button.onclick = () => {
        const index = Number(button.dataset.index);
        const order = draft.layout.sectionOrder;
        if (button.dataset.sectionAction === "up" && index > 0) [order[index - 1], order[index]] = [order[index], order[index - 1]];
        if (button.dataset.sectionAction === "down" && index < order.length - 1) [order[index + 1], order[index]] = [order[index], order[index + 1]];
        renderForm();
        sendPreview();
        pushHistory();
        setStatus("เปลี่ยนลำดับส่วนแล้ว กรุณากดบันทึก");
      };
    });
  }

  function renderForm() {
    renderDynamicFields();
    fillFields();
    bindFields();
    bindImageEditors();
    bindListActions();
    bindTextStyleEditor();
    renderSelectedTextStyle();
    updateHistoryButtons();
  }

  function ensureTextStyle(path, device = activeStyleDevice) {
    if (!draft.textStyles || typeof draft.textStyles !== "object") draft.textStyles = {};
    if (!draft.textStyles[path] || typeof draft.textStyles[path] !== "object") draft.textStyles[path] = {};
    if (!draft.textStyles[path][device] || typeof draft.textStyles[path][device] !== "object") draft.textStyles[path][device] = {};
    return draft.textStyles[path][device];
  }

  function currentTextStyle() {
    return draft.textStyles?.[selectedPath]?.[activeStyleDevice] || {};
  }

  function renderSelectedTextStyle() {
    const editor = document.querySelector("#text-style-editor");
    if (!editor) return;
    const show = Boolean(selectedPath) && selectedEditType === "text";
    editor.classList.toggle("is-hidden", !show);
    if (!show) return;
    document.querySelectorAll("[data-style-device]").forEach((button) => button.classList.toggle("is-active", button.dataset.styleDevice === activeStyleDevice));
    const style = currentTextStyle();
    editor.querySelectorAll("[data-text-style]").forEach((field) => {
      const key = field.dataset.textStyle;
      const zeroDefaults = new Set(["x", "y", "rotation", "letterSpacing"]);
      field.value = style[key] ?? (zeroDefaults.has(key) ? 0 : "");
    });
  }

  function bindTextStyleEditor() {
    document.querySelectorAll("[data-style-device]").forEach((button) => {
      button.onclick = () => {
        activeStyleDevice = button.dataset.styleDevice;
        document.querySelectorAll("[data-device]").forEach((item) => item.classList.toggle("is-active", item.dataset.device === activeStyleDevice));
        document.querySelector("[data-device-stage]").dataset.deviceStage = activeStyleDevice;
        renderSelectedTextStyle();
        setTimeout(sendPreview, 80);
      };
    });

    document.querySelectorAll("[data-text-style]").forEach((field) => {
      const handler = () => {
        if (!selectedPath || selectedEditType !== "text") return;
        const style = ensureTextStyle(selectedPath);
        const key = field.dataset.textStyle;
        const numericKeys = new Set(["fontSize", "width", "x", "y", "lineHeight", "letterSpacing", "rotation"]);
        if (field.value === "") delete style[key];
        else style[key] = numericKeys.has(key) ? Number(field.value) : field.value;
        markChanged("ปรับรูปแบบข้อความแล้ว กรุณากดบันทึก");
      };
      field.oninput = handler;
      field.onchange = handler;
    });

    document.querySelector("#reset-text-position").onclick = () => {
      if (!selectedPath) return;
      const style = ensureTextStyle(selectedPath);
      delete style.x; delete style.y; delete style.rotation;
      renderSelectedTextStyle();
      markChanged("คืนตำแหน่งข้อความแล้ว กรุณากดบันทึก");
    };

    document.querySelector("#reset-text-style").onclick = () => {
      if (!selectedPath || !draft.textStyles?.[selectedPath]) return;
      delete draft.textStyles[selectedPath][activeStyleDevice];
      if (!Object.keys(draft.textStyles[selectedPath]).length) delete draft.textStyles[selectedPath];
      renderSelectedTextStyle();
      markChanged("คืนรูปแบบข้อความจุดนี้แล้ว กรุณากดบันทึก");
    };
  }

  function findField(path) {
    const fields = [...document.querySelectorAll("[data-path], [data-image-path]")];
    return fields.find((element) => element.dataset.path === path || element.dataset.imagePath === path) || null;
  }

  function jumpToPath(path, smooth = true) {
    const target = findField(path);
    if (!target) {
      activatePanel("visual");
      setStatus(`ยังไม่มีช่องแก้ไขเฉพาะสำหรับ ${path}`, "error");
      return;
    }
    const panel = target.closest(".panel");
    if (panel) activatePanel(panel.dataset.panelContent, false);
    target.classList.remove("field-highlight");
    void target.offsetWidth;
    target.classList.add("field-highlight");
    target.scrollIntoView({ behavior: smooth ? "smooth" : "auto", block: "center" });
    const input = target.matches("input,textarea,select") ? target : target.querySelector("input,textarea,select");
    setTimeout(() => input?.focus({ preventScroll: true }), smooth ? 450 : 20);
  }

  window.addEventListener("message", (event) => {
    if (event.data?.type === "wellsound-text-style-change") {
      selectedPath = event.data.path;
      selectedEditType = "text";
      activeStyleDevice = event.data.device || activeStyleDevice;
      const style = ensureTextStyle(selectedPath, activeStyleDevice);
      Object.assign(style, event.data.values || {});
      renderSelectedTextStyle();
      queueHistory();
      setStatus("ย้ายข้อความแล้ว กรุณากดบันทึก");
      return;
    }
    if (event.data?.type !== "wellsound-select-field") return;
    selectedPath = event.data.path;
    selectedEditType = event.data.editType || "text";
    activeStyleDevice = event.data.device || activeStyleDevice;
    document.querySelector("#selected-title").textContent = event.data.label || selectedPath;
    document.querySelector("#selected-path").textContent = `จุดที่เลือก: ${selectedPath}`;
    document.querySelector("#selected-card .selected-badge").textContent = selectedEditType === "image" ? "รูปภาพ" : "ข้อความ — ลากย้ายได้";
    document.querySelector("#selected-jump-button").classList.remove("is-hidden");
    renderSelectedTextStyle();
    if (selectedEditType === "image") jumpToPath(selectedPath);
    else activatePanel("visual");
  });

  document.querySelector("#selected-jump-button").addEventListener("click", () => {
    if (selectedPath) jumpToPath(selectedPath);
  });

  function saveChanges() {
    try {
      pushHistory();
      localStorage.setItem(STORAGE_KEY, JSON.stringify(draft));
      setStatus(`บันทึกเรียบร้อย เมื่อ ${new Date().toLocaleTimeString("th-TH", { hour: "2-digit", minute: "2-digit" })}`, "success");
      sendPreview();
    } catch (error) {
      console.error(error);
      setStatus("บันทึกไม่สำเร็จ พื้นที่เก็บข้อมูลอาจเต็ม กรุณาลดจำนวนหรือขนาดรูป", "error");
    }
  }

  saveButton.addEventListener("click", saveChanges);
  document.querySelector("#save-copy-button").addEventListener("click", saveChanges);

  function siteContentSource(content) {
    return [
      'window.WELLSOUND_STORAGE_KEY = "wellsound24_site_content_v2";',
      'window.WELLSOUND_PIN_KEY = "wellsound24_admin_pin";',
      `window.WELLSOUND_DEFAULTS = ${JSON.stringify(content, null, 2)};`,
      ""
    ].join("\n");
  }

  async function publishToGithub() {
    saveChanges();
    const publishButtons = [publishGithubTopButton, publishGithubButton].filter(Boolean);
    publishButtons.forEach((button) => { button.disabled = true; });
    setStatus("กำลังบันทึกขึ้นเว็บไซต์จริง...");

    try {
      const response = await fetch("/api/publish", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          pin: currentPin(),
          content: draft
        })
      });
      const result = await response.json().catch(() => ({}));
      if (!response.ok) {
        throw new Error(result.error || `บันทึกไม่สำเร็จ (${response.status})`);
      }

      setStatus("บันทึกขึ้นเว็บไซต์จริงแล้ว รอ Vercel deploy ประมาณ 1-2 นาที", "success");
    } catch (error) {
      console.error(error);
      setStatus(`บันทึกขึ้นเว็บไซต์จริงไม่สำเร็จ: ${error.message}`, "error");
    } finally {
      publishButtons.forEach((button) => { button.disabled = false; });
    }
  }

  publishGithubTopButton?.addEventListener("click", publishToGithub);
  publishGithubButton?.addEventListener("click", publishToGithub);

  document.querySelector("#test-line-link-button")?.addEventListener("click", () => {
    const lineUrl = resolveLineUrl(draft.site || {});
    if (!lineUrl) {
      setStatus("กรุณากรอก LINE ID หรือลิงก์เพิ่มเพื่อน LINE ก่อนทดสอบ", "error");
      return;
    }
    window.open(lineUrl, "_blank", "noopener");
    setStatus("กำลังเปิดลิงก์ LINE สำหรับทดสอบ", "success");
  });


  document.querySelector("#test-facebook-link-button")?.addEventListener("click", () => {
    const facebookUrl = resolveFacebookUrl(draft.site || {});
    if (!facebookUrl) {
      setStatus("กรุณากรอกลิงก์หรือชื่อเพจ Facebook ก่อนทดสอบ", "error");
      return;
    }
    window.open(facebookUrl, "_blank", "noopener");
    setStatus("กำลังเปิดเพจ Facebook สำหรับทดสอบ", "success");
  });

  document.querySelector("#change-pin-button").addEventListener("click", () => {
    const newPin = document.querySelector("#new-pin").value.trim();
    if (!/^\d{4,12}$/.test(newPin)) {
      setStatus("รหัสใหม่ต้องเป็นตัวเลข 4–12 หลัก", "error");
      return;
    }
    localStorage.setItem(PIN_KEY, newPin);
    document.querySelector("#new-pin").value = "";
    setStatus("เปลี่ยนรหัสเข้าแดชบอร์ดเรียบร้อย", "success");
  });

  document.querySelector("#export-button").addEventListener("click", () => {
    const blob = new Blob([JSON.stringify(draft, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `wellsound24-full-backup-${new Date().toISOString().slice(0,10)}.json`;
    link.click();
    URL.revokeObjectURL(url);
    setStatus("ดาวน์โหลดไฟล์สำรองแล้ว", "success");
  });

  document.querySelector("#publish-file-button").addEventListener("click", () => {
    const source = siteContentSource(draft);
    const blob = new Blob([source], { type: "text/javascript;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "site-content.js";
    link.click();
    URL.revokeObjectURL(url);
    setStatus("สร้างไฟล์ site-content.js แล้ว นำไปแทนไฟล์เดิมก่อนอัปโหลดขึ้น Vercel", "success");
  });

  document.querySelector("#import-input").addEventListener("change", async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    try {
      const imported = JSON.parse(await file.text());
      if (!imported.site || !imported.hero) throw new Error("Invalid structure");
      draft = deepMerge(DEFAULTS, imported);
      history = [clone(draft)];
      historyIndex = 0;
      renderForm();
      sendPreview();
      setStatus("นำเข้าข้อมูลแล้ว กรุณากดบันทึกเพื่อยืนยัน", "success");
    } catch (error) {
      console.error(error);
      setStatus("ไฟล์สำรองไม่ถูกต้องหรือเปิดไม่ได้", "error");
    } finally {
      event.target.value = "";
    }
  });

  document.querySelector("#reset-button").addEventListener("click", () => {
    if (!confirm("ต้องการคืนค่าเว็บไซต์เป็นค่าเริ่มต้นทั้งหมดหรือไม่?")) return;
    localStorage.removeItem(STORAGE_KEY);
    localStorage.removeItem(OLD_STORAGE_KEY);
    draft = clone(DEFAULTS);
    history = [clone(draft)];
    historyIndex = 0;
    renderForm();
    sendPreview();
    setStatus("คืนค่าเริ่มต้นแล้ว", "success");
  });

  document.addEventListener("keydown", (event) => {
    if (!(event.ctrlKey || event.metaKey)) return;
    if (event.key.toLowerCase() === "s") {
      event.preventDefault();
      saveChanges();
    }
    if (event.key.toLowerCase() === "z" && !event.shiftKey) {
      event.preventDefault();
      undoButton.click();
    }
    if (event.key.toLowerCase() === "y" || (event.key.toLowerCase() === "z" && event.shiftKey)) {
      event.preventDefault();
      redoButton.click();
    }
  });
})();
