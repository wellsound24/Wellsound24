const localKey = "wellsound-site-content";
const githubKey = "wellsound-github-settings";
const editor = document.querySelector("#editor");
const statusNode = document.querySelector("#status");
const preview = document.querySelector("#preview");

let content = null;

const fields = {
  brand: [
    ["name", "ชื่อแบรนด์", "input"],
    ["tagline", "Tagline", "input"],
    ["description", "คำอธิบายแบรนด์", "textarea"],
    ["primaryColor", "สีหลัก", "color"],
    ["accentColor", "สีรอง", "color"]
  ],
  hero: [
    ["headline", "หัวข้อใหญ่", "textarea"],
    ["subheadline", "คำโปรย", "textarea"],
    ["ctaText", "ปุ่มหลัก", "input"],
    ["secondaryCtaText", "ปุ่มรอง", "input"]
  ],
  contact: [
    ["phone", "เบอร์โทร", "input"],
    ["line", "LINE", "input"],
    ["email", "อีเมล", "input"],
    ["address", "ที่อยู่", "textarea"],
    ["hours", "เวลาทำการ", "input"]
  ]
};

const listSchemas = {
  stats: [["label", "ชื่อสถิติ"], ["value", "ตัวเลข"]],
  services: [["title", "ชื่อบริการ"], ["description", "รายละเอียด"]],
  packages: [["name", "ชื่อแพ็กเกจ"], ["price", "ราคา"], ["detail", "รายละเอียด"]],
  testimonials: [["quote", "ข้อความรีวิว"], ["name", "ชื่อผู้รีวิว"]]
};

const labels = {
  brand: "แบรนด์",
  hero: "Hero",
  contact: "ติดต่อ",
  stats: "สถิติ",
  services: "บริการ",
  packages: "แพ็กเกจ",
  testimonials: "รีวิว"
};

const setStatus = (message) => {
  statusNode.textContent = message;
};

const fieldId = (path) => path.join(".");

const inputField = (path, label, type, value) => {
  const wrapper = document.createElement("label");
  wrapper.className = "field";
  wrapper.innerHTML = `<span></span>`;
  wrapper.querySelector("span").textContent = label;
  const control = type === "textarea" ? document.createElement("textarea") : document.createElement("input");
  control.name = fieldId(path);
  control.value = value || "";
  if (type === "color") control.type = "color";
  wrapper.append(control);
  return wrapper;
};

const renderObjectSection = (key) => {
  const section = document.createElement("fieldset");
  section.className = "fieldset";
  section.innerHTML = `<legend>${labels[key]}</legend>`;
  fields[key].forEach(([name, label, type]) => {
    section.append(inputField([key, name], label, type, content[key]?.[name]));
  });
  return section;
};

const renderListSection = (key) => {
  const section = document.createElement("fieldset");
  section.className = "fieldset";
  section.innerHTML = `<legend>${labels[key]}</legend>`;
  const list = content[key] || [];
  list.forEach((item, index) => {
    const box = document.createElement("div");
    box.className = "item-editor";
    listSchemas[key].forEach(([name, label]) => {
      const isLong = ["description", "detail", "quote"].some((word) => name.includes(word));
      box.append(inputField([key, index, name], label, isLong ? "textarea" : "input", item[name]));
    });
    const remove = document.createElement("button");
    remove.type = "button";
    remove.className = "danger-button";
    remove.textContent = "ลบรายการนี้";
    remove.addEventListener("click", () => {
      content[key].splice(index, 1);
      renderEditor();
      saveLocal();
    });
    box.append(remove);
    section.append(box);
  });
  const add = document.createElement("button");
  add.type = "button";
  add.className = "small-button";
  add.textContent = `เพิ่ม${labels[key]}`;
  add.addEventListener("click", () => {
    const next = {};
    listSchemas[key].forEach(([name]) => {
      next[name] = "";
    });
    content[key] = content[key] || [];
    content[key].push(next);
    renderEditor();
  });
  section.append(add);
  return section;
};

const renderGithubSection = () => {
  const settings = JSON.parse(localStorage.getItem(githubKey) || "{}");
  const section = document.createElement("fieldset");
  section.className = "fieldset";
  section.innerHTML = "<legend>GitHub สำหรับเผยแพร่</legend>";
  [
    ["owner", "GitHub owner หรือ organization", "wellsound24"],
    ["repo", "ชื่อ repository", "Wellsound24"],
    ["branch", "branch", "main"],
    ["path", "ตำแหน่งไฟล์ข้อมูล", "data.json"],
    ["token", "GitHub fine-grained token"]
  ].forEach(([name, label, fallback]) => {
    section.append(inputField(["github", name], label, "input", settings[name] || fallback || ""));
  });
  return section;
};

const renderEditor = () => {
  editor.replaceChildren(
    renderObjectSection("brand"),
    renderObjectSection("hero"),
    renderListSection("stats"),
    renderListSection("services"),
    renderListSection("packages"),
    renderListSection("testimonials"),
    renderObjectSection("contact"),
    renderGithubSection()
  );
};

const collect = () => {
  const form = new FormData(editor);
  const next = structuredClone(content);
  const github = {};
  for (const [name, value] of form.entries()) {
    const path = name.split(".");
    if (path[0] === "github") {
      github[path[1]] = value.trim();
      continue;
    }
    if (path.length === 2) next[path[0]][path[1]] = value;
    if (path.length === 3) next[path[0]][Number(path[1])][path[2]] = value;
  }
  localStorage.setItem(githubKey, JSON.stringify(github));
  content = next;
  return { next, github };
};

const saveLocal = () => {
  const { next } = collect();
  localStorage.setItem(localKey, JSON.stringify(next));
  preview.contentWindow?.location.reload();
  setStatus("บันทึกตัวอย่างแล้ว");
};

const downloadJson = () => {
  const { next } = collect();
  const blob = new Blob([JSON.stringify(next, null, 2)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = "data.json";
  link.click();
  URL.revokeObjectURL(url);
  setStatus("ดาวน์โหลดไฟล์ data.json แล้ว");
};

const publishGithub = async () => {
  const { next, github } = collect();
  const { owner, repo, branch = "main", path = "data.json", token } = github;
  if (!owner || !repo || !token) {
    setStatus("กรุณาใส่ owner, repo และ GitHub token ก่อนบันทึกลง GitHub");
    return;
  }
  setStatus("กำลังส่งข้อมูลไป GitHub...");
  const apiUrl = `https://api.github.com/repos/${owner}/${repo}/contents/${path}`;
  const headers = {
    Authorization: `Bearer ${token}`,
    Accept: "application/vnd.github+json",
    "Content-Type": "application/json"
  };
  const current = await fetch(`${apiUrl}?ref=${encodeURIComponent(branch)}`, { headers });
  const currentJson = current.ok ? await current.json() : {};
  const body = {
    message: "Update Wellsound website content",
    branch,
    content: btoa(unescape(encodeURIComponent(JSON.stringify(next, null, 2)))),
    sha: currentJson.sha
  };
  const response = await fetch(apiUrl, { method: "PUT", headers, body: JSON.stringify(body) });
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    setStatus(`บันทึก GitHub ไม่สำเร็จ: ${error.message || response.status}`);
    return;
  }
  localStorage.setItem(localKey, JSON.stringify(next));
  setStatus("บันทึกลง GitHub แล้ว รอ Vercel deploy จาก repository");
};

const load = async () => {
  try {
    const response = await fetch("../data.json", { cache: "no-store" });
    content = await response.json();
    const local = localStorage.getItem(localKey);
    if (local) content = JSON.parse(local);
    renderEditor();
    setStatus("พร้อมแก้ไข");
  } catch (error) {
    setStatus("โหลดข้อมูลไม่สำเร็จ");
    console.error(error);
  }
};

document.querySelector("#saveLocal").addEventListener("click", saveLocal);
document.querySelector("#downloadJson").addEventListener("click", downloadJson);
document.querySelector("#publishGithub").addEventListener("click", publishGithub);
load();
