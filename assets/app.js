const fallbackContent = {};

const localKey = "wellsound-site-content";

const getPath = (source, path) =>
  path.split(".").reduce((value, key) => (value ? value[key] : undefined), source);

const setText = (selector, value, root = document) => {
  root.querySelectorAll(selector).forEach((node) => {
    node.textContent = value || "";
  });
};

const createCard = (item) => {
  const article = document.createElement("article");
  article.className = "card";
  article.innerHTML = `<h3></h3><p></p>`;
  article.querySelector("h3").textContent = item.title || "";
  article.querySelector("p").textContent = item.description || "";
  return article;
};

const createPackage = (item) => {
  const article = document.createElement("article");
  article.className = "package";
  article.innerHTML = `<h3></h3><strong></strong><p></p>`;
  article.querySelector("h3").textContent = item.name || "";
  article.querySelector("strong").textContent = item.price || "";
  article.querySelector("p").textContent = item.detail || "";
  return article;
};

const createQuote = (item) => {
  const article = document.createElement("article");
  article.className = "quote";
  article.innerHTML = `<blockquote></blockquote><cite></cite>`;
  article.querySelector("blockquote").textContent = item.quote || "";
  article.querySelector("cite").textContent = item.name || "";
  return article;
};

const createStat = (item) => {
  const article = document.createElement("article");
  article.className = "stat";
  article.innerHTML = `<strong></strong><span></span>`;
  article.querySelector("strong").textContent = item.value || "";
  article.querySelector("span").textContent = item.label || "";
  return article;
};

const renderList = (selector, items, factory) => {
  document.querySelectorAll(selector).forEach((container) => {
    container.replaceChildren(...(items || []).map(factory));
  });
};

const applyContent = (content) => {
  document.documentElement.style.setProperty("--primary", content.brand?.primaryColor || "#0f766e");
  document.documentElement.style.setProperty("--accent", content.brand?.accentColor || "#f59e0b");
  document.title = content.brand?.name || "Wellsound";

  document.querySelectorAll("[data-text]").forEach((node) => {
    node.textContent = getPath(content, node.dataset.text) || "";
  });

  renderList('[data-list="stats"]', content.stats, createStat);
  renderList('[data-list="services"]', content.services, createCard);
  renderList('[data-list="packages"]', content.packages, createPackage);
  renderList('[data-list="testimonials"]', content.testimonials, createQuote);

  const contact = content.contact || {};
  setText('[data-contact="phone"]', contact.phone);
  setText('[data-contact="line"]', contact.line);
  setText('[data-contact="email"]', contact.email);
  setText('[data-contact="address"]', contact.address);
  setText('[data-contact="hours"]', contact.hours);

  const phone = document.querySelector('[data-contact="phone"]');
  const line = document.querySelector('[data-contact="line"]');
  const email = document.querySelector('[data-contact="email"]');
  if (phone) phone.href = `tel:${(contact.phone || "").replace(/\D/g, "")}`;
  if (line) line.href = contact.line?.startsWith("@")
    ? `https://line.me/R/ti/p/${encodeURIComponent(contact.line)}`
    : "#";
  if (email) email.href = `mailto:${contact.email || ""}`;
};

const loadContent = async () => {
  try {
    const response = await fetch("data.json", { cache: "no-store" });
    Object.assign(fallbackContent, await response.json());
  } catch (error) {
    console.warn("Cannot load data.json", error);
  }

  const local = localStorage.getItem(localKey);
  const content = local ? JSON.parse(local) : fallbackContent;
  applyContent(content);
};

loadContent();
