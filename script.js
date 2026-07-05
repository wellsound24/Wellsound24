(() => {
  "use strict";

  const DEFAULTS = window.WELLSOUND_DEFAULTS || {};
  const STORAGE_KEY = window.WELLSOUND_STORAGE_KEY || "wellsound24_site_content_v2";
  const editorMode = new URLSearchParams(location.search).get("editor") === "1";

  const clone = (value) => JSON.parse(JSON.stringify(value));
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

  function loadContent() {
    try {
      const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || "null");
      return deepMerge(DEFAULTS, saved || {});
    } catch (error) {
      console.warn("อ่านข้อมูลเว็บไซต์ไม่สำเร็จ ใช้ค่าเริ่มต้นแทน", error);
      return clone(DEFAULTS);
    }
  }

  function getByPath(object, path) {
    return String(path).split(".").reduce((value, key) => value?.[key], object);
  }

  function setText(selector, value) {
    const element = document.querySelector(selector);
    if (element && value !== undefined && value !== null) element.textContent = value;
  }

  function setBackground(element, image, position = "center center") {
    if (!element) return;
    if (image) {
      element.style.backgroundImage = `url(${JSON.stringify(image)})`;
      element.style.backgroundPosition = position || "center center";
      element.classList.add("has-custom-image");
    } else {
      element.style.backgroundImage = "";
      element.style.backgroundPosition = "";
      element.classList.remove("has-custom-image");
    }
  }

  function fontStack(name) {
    const stacks = {
      Kanit: '"Kanit", system-ui, sans-serif',
      Prompt: '"Prompt", system-ui, sans-serif',
      Sarabun: '"Sarabun", system-ui, sans-serif',
      System: 'system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif'
    };
    return stacks[name] || stacks.Kanit;
  }

  function activeTextDevice() {
    if (window.innerWidth <= 700) return "mobile";
    if (window.innerWidth <= 1024) return "tablet";
    return "desktop";
  }

  function textStyleFor(content, path, device = activeTextDevice()) {
    const styles = content.textStyles?.[path] || {};
    return { ...(styles.desktop || {}), ...(device === "desktop" ? {} : (styles[device] || {})) };
  }

  function applyTextStyles(content) {
    document.querySelectorAll("[data-edit-path]").forEach((element) => {
      const style = textStyleFor(content, element.dataset.editPath);
      const x = Number(style.x) || 0;
      const y = Number(style.y) || 0;
      const rotation = Number(style.rotation) || 0;
      element.style.display = "";
      const needsTransform = x !== 0 || y !== 0 || rotation !== 0;
      const needsBox = needsTransform || Number(style.width) > 0;
      if (needsBox && getComputedStyle(element).display === "inline") element.style.display = "inline-block";
      element.style.fontSize = Number(style.fontSize) > 0 ? `${style.fontSize}px` : "";
      element.style.width = Number(style.width) > 0 ? `${style.width}px` : "";
      element.style.maxWidth = Number(style.width) > 0 ? "none" : "";
      element.style.textAlign = style.textAlign || "";
      element.style.fontWeight = style.fontWeight || "";
      element.style.lineHeight = Number(style.lineHeight) > 0 ? String(style.lineHeight) : "";
      element.style.letterSpacing = style.letterSpacing !== undefined && style.letterSpacing !== "" ? `${Number(style.letterSpacing) || 0}px` : "";
      element.style.color = style.color || "";
      element.style.transform = needsTransform ? `translate3d(${x}px, ${y}px, 0) rotate(${rotation}deg)` : "";
      element.dataset.textX = String(x);
      element.dataset.textY = String(y);
    });
  }

  function applyDesign(content) {
    const design = content.design || DEFAULTS.design;
    const root = document.documentElement;
    root.style.setProperty("--gold", design.primaryColor);
    root.style.setProperty("--gold-light", design.primaryLight);
    root.style.setProperty("--bg", design.backgroundColor);
    root.style.setProperty("--card", design.surfaceColor);
    root.style.setProperty("--text", design.textColor);
    root.style.setProperty("--muted", design.mutedColor);
    root.style.setProperty("--line", design.lineColor || "rgba(255,255,255,.11)");
    root.style.setProperty("--font-family", fontStack(design.fontFamily));
    root.style.setProperty("--container", `${Math.max(880, Math.min(1600, Number(design.containerWidth) || 1180))}px`);
    root.style.setProperty("--radius", `${Math.max(0, Math.min(50, Number(design.borderRadius) || 0))}px`);
    root.style.setProperty("--button-radius", `${Math.max(0, Math.min(50, Number(design.buttonRadius) || 0))}px`);
    root.style.setProperty("--section-padding", `${Math.max(40, Math.min(180, Number(design.sectionPadding) || 110))}px`);
    document.body.classList.toggle("no-card-shadow", design.cardShadow === false);
  }

  function renderHeader(content) {
    const header = document.querySelector("[data-site-header]");
    if (!header) return;
    header.hidden = content.header.visible === false;
    header.classList.toggle("is-static", content.header.sticky === false);
    document.documentElement.style.setProperty("--header-opacity", String(Math.max(0, Math.min(100, Number(content.header.backgroundOpacity) || 0)) / 100));
    document.documentElement.style.setProperty("--header-logo-size", `${Math.max(48, Math.min(180, Number(content.header.logoSize) || 102))}px`);

    const nav = document.querySelector("#main-nav");
    nav.innerHTML = "";
    (content.header.menu || []).forEach((item, index) => {
      const link = document.createElement("a");
      link.href = item.target || "#";
      link.textContent = item.label || `เมนู ${index + 1}`;
      link.dataset.editPath = `header.menu.${index}.label`;
      nav.appendChild(link);
    });

    const cta = document.querySelector("[data-header-cta]");
    cta.hidden = content.header.showCta !== true;
    cta.textContent = content.header.ctaText || "";
    cta.href = content.header.ctaLink || "#contact";
  }

  function renderHero(content) {
    const hero = document.querySelector("#hero");
    setText('[data-edit-path="hero.eyebrow"]', content.hero.eyebrow);
    setText('[data-edit-path="hero.titleLine1"]', content.hero.titleLine1);
    setText('[data-edit-path="hero.titleLine2"]', content.hero.titleLine2);
    setText('[data-edit-path="hero.description"]', content.hero.description);
    setText('[data-edit-path="hero.primaryButton"]', content.hero.primaryButton);
    setText('[data-edit-path="hero.secondaryButton"]', content.hero.secondaryButton);
    setBackground(hero, content.hero.image, content.hero.imagePosition);

    hero.classList.remove("align-left", "align-center", "align-right");
    hero.classList.add(`align-${content.hero.alignment || "center"}`);
    hero.classList.toggle("no-lights", content.hero.showLights === false);
    document.documentElement.style.setProperty("--hero-overlay", String(Math.max(0, Math.min(100, Number(content.hero.overlayOpacity) || 0)) / 100));
    document.documentElement.style.setProperty("--hero-min-height", `${Math.max(60, Math.min(130, Number(content.hero.minHeight) || 100))}vh`);
    document.documentElement.style.setProperty("--hero-content-width", `${Math.max(600, Math.min(1400, Number(content.hero.contentWidth) || 940))}px`);
    document.documentElement.style.setProperty("--hero-title-scale", String(Math.max(60, Math.min(150, Number(content.hero.titleSize) || 100)) / 100));

    const primary = document.querySelector(".hero-primary");
    const secondary = document.querySelector(".hero-secondary");
    primary.hidden = content.hero.showPrimaryButton === false;
    secondary.hidden = content.hero.showSecondaryButton === false;
    primary.href = content.hero.primaryLink || "#contact";
    secondary.href = content.hero.secondaryLink || "#services";

    const stats = document.querySelector("#hero-stats");
    stats.hidden = content.hero.showStats === false || !(content.stats || []).length;
    stats.style.setProperty("--stats-count", String(Math.max(1, (content.stats || []).length)));
    stats.innerHTML = "";
    (content.stats || []).forEach((item, index) => {
      const box = document.createElement("div");
      const strong = document.createElement("strong");
      const span = document.createElement("span");
      strong.textContent = item.title || "";
      span.textContent = item.text || "";
      strong.dataset.editPath = `stats.${index}.title`;
      span.dataset.editPath = `stats.${index}.text`;
      box.append(strong, span);
      stats.appendChild(box);
    });
  }

  function renderServices(content) {
    setText('[data-edit-path="servicesSection.eyebrow"]', content.servicesSection.eyebrow);
    setText('[data-edit-path="servicesSection.title"]', content.servicesSection.title);
    setText('[data-edit-path="servicesSection.description"]', content.servicesSection.description);
    const section = document.querySelector("#services");
    section.classList.remove("bg-soft", "bg-card");
    if (content.servicesSection.background === "soft") section.classList.add("bg-soft");
    if (content.servicesSection.background === "card") section.classList.add("bg-card");

    const grid = document.querySelector("#service-grid");
    grid.style.setProperty("--service-columns", String(Math.max(1, Math.min(4, Number(content.servicesSection.columns) || 4))));
    grid.innerHTML = "";
    (content.services || []).forEach((item, index) => {
      const card = document.createElement("article");
      card.className = "service-card";
      card.innerHTML = `<div aria-hidden="true" class="service-icon"></div><h3></h3><p></p>`;
      card.querySelector(".service-icon").textContent = item.icon || "✦";
      card.querySelector("h3").textContent = item.title || "";
      card.querySelector("p").textContent = item.description || "";
      card.querySelector(".service-icon").dataset.editPath = `services.${index}.icon`;
      card.querySelector("h3").dataset.editPath = `services.${index}.title`;
      card.querySelector("p").dataset.editPath = `services.${index}.description`;
      grid.appendChild(card);
    });
  }

  function renderWhy(content) {
    setText('[data-edit-path="why.eyebrow"]', content.why.eyebrow);
    setText('[data-edit-path="why.titleLine1"]', content.why.titleLine1);
    setText('[data-edit-path="why.titleLine2"]', content.why.titleLine2);
    setText('[data-edit-path="why.description"]', content.why.description);
    setText('[data-edit-path="why.screenLine1"]', content.why.screenLine1);
    setText('[data-edit-path="why.screenLine2"]', content.why.screenLine2);

    const list = document.querySelector("#why-list");
    list.innerHTML = "";
    (content.why.items || []).forEach((text, index) => {
      const item = document.createElement("li");
      item.textContent = text;
      item.dataset.editPath = `why.items.${index}`;
      list.appendChild(item);
    });

    const wrap = document.querySelector(".why-visual-wrap");
    const image = document.querySelector("#why-image");
    const useImage = content.why.visualType === "image" && Boolean(content.why.image);
    wrap.classList.toggle("is-image", useImage);
    setBackground(image, content.why.image, content.why.imagePosition);
  }

  function renderPortfolio(content) {
    setText('[data-edit-path="portfolioSection.eyebrow"]', content.portfolioSection.eyebrow);
    setText('[data-edit-path="portfolioSection.title"]', content.portfolioSection.title);
    setText('[data-edit-path="portfolioSection.description"]', content.portfolioSection.description);
    const grid = document.querySelector("#portfolio-grid");
    grid.style.setProperty("--portfolio-columns", String(Math.max(1, Math.min(4, Number(content.portfolioSection.columns) || 2))));
    grid.innerHTML = "";
    (content.portfolio || []).forEach((item, index) => {
      const card = document.createElement("a");
      card.className = "portfolio-item";
      card.href = item.link || "#contact";
      card.dataset.editImage = `portfolio.${index}.image`;
      setBackground(card, item.image, item.imagePosition);
      const inner = document.createElement("div");
      const label = document.createElement("span");
      const title = document.createElement("h3");
      const description = document.createElement("p");
      label.textContent = item.label || "";
      title.textContent = item.title || "";
      description.textContent = item.description || "";
      label.dataset.editPath = `portfolio.${index}.label`;
      title.dataset.editPath = `portfolio.${index}.title`;
      description.dataset.editPath = `portfolio.${index}.description`;
      description.hidden = !item.description;
      inner.append(label, title, description);
      card.appendChild(inner);
      grid.appendChild(card);
    });
  }

  function renderContact(content) {
    setText('[data-edit-path="contact.eyebrow"]', content.contact.eyebrow);
    setText('[data-edit-path="contact.title"]', content.contact.title);
    setText('[data-edit-path="contact.description"]', content.contact.description);
    setText('[data-edit-path="contact.phoneButton"]', content.contact.phoneButton);
    setText('[data-edit-path="contact.lineButton"]', content.contact.lineButton);
    setText('[data-edit-path="contact.facebookButton"]', content.contact.facebookButton);
    setText('[data-edit-path="contact.note"]', content.contact.note);
    setText('[data-edit-path="contact.form.nameLabel"]', content.contact.form.nameLabel);
    setText('[data-edit-path="contact.form.phoneLabel"]', content.contact.form.phoneLabel);
    setText('[data-edit-path="contact.form.eventLabel"]', content.contact.form.eventLabel);
    setText('[data-edit-path="contact.form.detailsLabel"]', content.contact.form.detailsLabel);
    setText('[data-edit-path="contact.form.submitButton"]', content.contact.form.submitButton);

    const phoneButton = document.querySelector(".contact-actions .contact-phone");
    const lineButton = document.querySelector(".contact-actions .contact-line");
    const facebookButton = document.querySelector(".contact-actions .contact-facebook");
    phoneButton.hidden = content.contact.showPhoneButton === false;
    lineButton.hidden = content.contact.showLineButton === false;
    facebookButton.hidden = content.contact.showFacebookButton === false;

    const form = document.querySelector("#quote-form");
    form.hidden = content.contact.showForm === false;
    form.querySelector('[name="name"]').placeholder = content.contact.form.namePlaceholder || "";
    form.querySelector('[name="phone"]').placeholder = content.contact.form.phonePlaceholder || "";
    form.querySelector('[name="details"]').placeholder = content.contact.form.detailsPlaceholder || "";

    const select = document.querySelector("#event-type-select");
    select.innerHTML = "";
    const placeholder = document.createElement("option");
    placeholder.value = "";
    placeholder.textContent = content.contact.form.eventPlaceholder || "เลือกประเภทงาน";
    select.appendChild(placeholder);
    (content.contact.form.eventTypes || []).forEach((type) => {
      const option = document.createElement("option");
      option.textContent = type;
      option.value = type;
      select.appendChild(option);
    });

    const details = document.querySelector("#contact-details");
    details.innerHTML = "";
    if (content.site.phone) {
      const a = document.createElement("a");
      a.href = `tel:${String(content.site.phone).replace(/[^0-9+]/g, "")}`;
      a.textContent = `โทร: ${content.site.phone}`;
      a.dataset.editPath = "site.phone";
      details.appendChild(a);
    }
    if (content.site.email) {
      const a = document.createElement("a");
      a.href = `mailto:${content.site.email}`;
      a.textContent = `อีเมล: ${content.site.email}`;
      a.dataset.editPath = "site.email";
      details.appendChild(a);
    }
    if (content.site.address) {
      const span = document.createElement("span");
      span.textContent = `พื้นที่ให้บริการ: ${content.site.address}`;
      span.dataset.editPath = "site.address";
      details.appendChild(span);
    }
  }

  function renderFooter(content) {
    const footer = document.querySelector("[data-site-footer]");
    footer.style.backgroundColor = content.footer.backgroundColor || "#050505";
    setText('[data-edit-path="footer.text"]', content.footer.text);
    setText('[data-edit-path="footer.contactText"]', content.footer.contactText);
    setText('[data-edit-path="footer.adminLinkText"]', content.footer.adminLinkText);
    document.querySelector(".footer-logo").hidden = content.footer.showLogo === false;
    document.querySelector(".admin-link").hidden = content.footer.showAdminLink === false;
    document.querySelector(".footer-contact").hidden = content.footer.showContact === false;
    document.querySelector(".floating-line").hidden = content.floating.showLine === false;
    setText('[data-edit-path="floating.label"]', content.floating.label);
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
    if (lineId) {
      return `https://line.me/R/ti/p/@${encodeURIComponent(lineId)}`;
    }

    const linePhone = String(site.linePhone || "").replace(/\D/g, "");
    if (linePhone) {
      return `https://line.me/R/ti/p/~${encodeURIComponent(linePhone)}`;
    }

    return "#";
  }

  function resolveFacebookUrl(site = {}) {
    let link = String(site.facebookUrl || "").trim();
    if (!link || link === "#") return "#";
    if (/^(facebook\.com|www\.facebook\.com|m\.facebook\.com|fb\.com)\//i.test(link)) {
      link = `https://${link}`;
    }
    if (/^https?:\/\//i.test(link)) return link;
    const pageName = link.replace(/^@+/, "").replace(/\s+/g, "");
    return pageName ? `https://www.facebook.com/${encodeURIComponent(pageName)}` : "#";
  }

  function applyLinksAndMeta(content) {
    document.title = content.site.title || DEFAULTS.site.title;
    const meta = document.querySelector('meta[name="description"]');
    if (meta) meta.content = content.site.description || "";
    const favicon = document.querySelector("#site-favicon");
    if (favicon) favicon.href = content.site.favicon || DEFAULTS.site.favicon;
    document.querySelectorAll(".site-logo").forEach((image) => { image.src = content.site.logo || DEFAULTS.site.logo; });
    document.querySelectorAll(".contact-phone").forEach((button) => { button.href = `tel:${String(content.site.phone || "").replace(/[^0-9+]/g, "")}`; });
    const lineUrl = resolveLineUrl(content.site);
    document.querySelectorAll(".contact-line").forEach((button) => {
      button.href = lineUrl;
      if (lineUrl === "#") {
        button.setAttribute("aria-disabled", "true");
        button.title = "กรุณาตั้งค่า LINE ID หรือลิงก์ LINE ในแดชบอร์ด";
      } else {
        button.removeAttribute("aria-disabled");
        button.removeAttribute("title");
      }
    });

    const facebookUrl = resolveFacebookUrl(content.site);
    document.querySelectorAll(".contact-facebook").forEach((button) => {
      button.href = facebookUrl;
      if (facebookUrl === "#") {
        button.setAttribute("aria-disabled", "true");
        button.title = "กรุณาตั้งค่าลิงก์เพจ Facebook ในแดชบอร์ด";
      } else {
        button.removeAttribute("aria-disabled");
        button.removeAttribute("title");
      }
    });
  }

  function applyLayout(content) {
    const main = document.querySelector("#page-sections");
    const known = ["hero", "services", "why", "portfolio", "contact"];
    const order = [...new Set([...(content.layout.sectionOrder || []), ...known])].filter((id) => known.includes(id));
    order.forEach((id) => {
      const section = document.querySelector(`[data-section-id="${id}"]`);
      if (section) main.appendChild(section);
    });
    known.forEach((id) => {
      const section = document.querySelector(`[data-section-id="${id}"]`);
      if (section) section.hidden = content.layout.visibility?.[id] === false;
    });
  }

  function applyContent(content) {
    if (!content?.site) return;
    applyDesign(content);
    renderHeader(content);
    renderHero(content);
    renderServices(content);
    renderWhy(content);
    renderPortfolio(content);
    renderContact(content);
    renderFooter(content);
    applyLinksAndMeta(content);
    applyLayout(content);
    applyTextStyles(content);
    window.WELLSOUND_ACTIVE_CONTENT = content;
  }

  let currentContent = loadContent();
  applyContent(currentContent);
  let textResizeTimer;
  window.addEventListener("resize", () => {
    clearTimeout(textResizeTimer);
    textResizeTimer = setTimeout(() => applyTextStyles(currentContent), 80);
  });

  const year = document.querySelector("#current-year");
  if (year) year.textContent = new Date().getFullYear();

  const menuToggle = document.querySelector(".menu-toggle");
  const mainNav = document.querySelector(".main-nav");
  if (menuToggle && mainNav) {
    menuToggle.addEventListener("click", () => {
      const open = mainNav.classList.toggle("is-open");
      menuToggle.setAttribute("aria-expanded", String(open));
    });
    mainNav.addEventListener("click", (event) => {
      if (event.target.closest("a")) {
        mainNav.classList.remove("is-open");
        menuToggle.setAttribute("aria-expanded", "false");
      }
    });
  }

  const quoteForm = document.querySelector("#quote-form");
  const formMessage = document.querySelector("#form-message");
  quoteForm?.addEventListener("submit", (event) => {
    event.preventDefault();
    const content = window.WELLSOUND_ACTIVE_CONTENT || currentContent;
    const lineUrl = resolveLineUrl(content.site);
    if (!lineUrl || lineUrl === "#") {
      formMessage.textContent = "กรุณาใส่ LINE ID เบอร์โทร หรือลิงก์ LINE ในหน้าแดชบอร์ดก่อนใช้งาน";
      return;
    }
    const data = new FormData(quoteForm);
    const message = [
      "สวัสดีครับ สนใจขอใบเสนอราคา Wellsound24",
      `ชื่อ: ${data.get("name")}`,
      `เบอร์โทร: ${data.get("phone")}`,
      `ประเภทงาน: ${data.get("eventType")}`,
      `รายละเอียด: ${data.get("details") || "-"}`
    ].join("\n");
    navigator.clipboard?.writeText(message).catch(() => {});
    formMessage.textContent = "คัดลอกข้อความแล้ว กำลังเปิด LINE...";
    window.open(lineUrl, "_blank", "noopener");
  });

  window.addEventListener("message", (event) => {
    if (event.data?.type === "wellsound-preview" && event.data.content) {
      currentContent = deepMerge(DEFAULTS, event.data.content);
      applyContent(currentContent);
    }
  });

  if (editorMode) {
    document.body.classList.add("is-editor-preview");
    let drag = null;
    let suppressClick = false;

    function selectTextTarget(target) {
      document.querySelectorAll(".editor-selected").forEach((item) => item.classList.remove("editor-selected"));
      target.classList.add("editor-selected");
      const path = target.dataset.editPath || target.dataset.editImage;
      window.parent.postMessage({
        type: "wellsound-select-field",
        path,
        editType: target.dataset.editImage ? "image" : "text",
        value: getByPath(currentContent, path),
        label: target.textContent?.trim().slice(0, 80) || path,
        device: activeTextDevice()
      }, "*");
    }

    document.addEventListener("pointerdown", (event) => {
      const target = event.target.closest("[data-edit-path]");
      if (!target || event.button !== 0) return;
      event.preventDefault();
      event.stopPropagation();
      selectTextTarget(target);
      const style = textStyleFor(currentContent, target.dataset.editPath);
      drag = {
        target,
        path: target.dataset.editPath,
        pointerId: event.pointerId,
        startX: event.clientX,
        startY: event.clientY,
        originX: Number(style.x) || 0,
        originY: Number(style.y) || 0,
        x: Number(style.x) || 0,
        y: Number(style.y) || 0,
        moved: false
      };
      target.setPointerCapture?.(event.pointerId);
      target.classList.add("editor-dragging");
    }, true);

    document.addEventListener("pointermove", (event) => {
      if (!drag || event.pointerId !== drag.pointerId) return;
      const dx = event.clientX - drag.startX;
      const dy = event.clientY - drag.startY;
      if (Math.abs(dx) + Math.abs(dy) > 3) drag.moved = true;
      drag.x = Math.round(drag.originX + dx);
      drag.y = Math.round(drag.originY + dy);
      const style = textStyleFor(currentContent, drag.path);
      const rotation = Number(style.rotation) || 0;
      drag.target.style.transform = `translate3d(${drag.x}px, ${drag.y}px, 0) rotate(${rotation}deg)`;
      drag.target.dataset.textX = String(drag.x);
      drag.target.dataset.textY = String(drag.y);
    }, true);

    function finishDrag(event) {
      if (!drag || (event.pointerId !== undefined && event.pointerId !== drag.pointerId)) return;
      drag.target.classList.remove("editor-dragging");
      if (drag.moved) {
        suppressClick = true;
        const device = activeTextDevice();
        if (!currentContent.textStyles || typeof currentContent.textStyles !== "object") currentContent.textStyles = {};
        if (!currentContent.textStyles[drag.path] || typeof currentContent.textStyles[drag.path] !== "object") currentContent.textStyles[drag.path] = {};
        currentContent.textStyles[drag.path][device] = {
          ...(currentContent.textStyles[drag.path][device] || {}),
          x: drag.x,
          y: drag.y
        };
        window.parent.postMessage({
          type: "wellsound-text-style-change",
          path: drag.path,
          device,
          values: { x: drag.x, y: drag.y }
        }, "*");
        setTimeout(() => { suppressClick = false; }, 80);
      }
      drag = null;
    }
    document.addEventListener("pointerup", finishDrag, true);
    document.addEventListener("pointercancel", finishDrag, true);

    document.addEventListener("click", (event) => {
      const target = event.target.closest("[data-edit-path], [data-edit-image]");
      if (!target) return;
      event.preventDefault();
      event.stopPropagation();
      if (suppressClick) return;
      selectTextTarget(target);
    }, true);
  }

  window.WELLSOUND_APPLY_CONTENT = applyContent;
})();
