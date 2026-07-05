const OWNER = process.env.GITHUB_OWNER || "wellsound24";
const REPO = process.env.GITHUB_REPO || "Wellsound24";
const BRANCH = process.env.GITHUB_BRANCH || "main";
const FILE_PATH = process.env.GITHUB_CONTENT_PATH || "site-content.js";
const PUBLISH_PIN = process.env.PUBLISH_PIN || process.env.ADMIN_PIN || "2468";

function send(res, status, payload) {
  res.statusCode = status;
  res.setHeader("Content-Type", "application/json; charset=utf-8");
  res.end(JSON.stringify(payload));
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let body = "";
    req.on("data", (chunk) => {
      body += chunk;
      if (body.length > 12 * 1024 * 1024) {
        reject(new Error("ข้อมูลใหญ่เกินไป"));
        req.destroy();
      }
    });
    req.on("end", () => resolve(body));
    req.on("error", reject);
  });
}

function siteContentSource(content) {
  return [
    'window.WELLSOUND_STORAGE_KEY = "wellsound24_site_content_v2";',
    'window.WELLSOUND_PIN_KEY = "wellsound24_admin_pin";',
    `window.WELLSOUND_DEFAULTS = ${JSON.stringify(content, null, 2)};`,
    ""
  ].join("\n");
}

async function githubJson(url, options) {
  const response = await fetch(url, options);
  const json = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(json.message || `GitHub API error ${response.status}`);
  }
  return json;
}

module.exports = async function handler(req, res) {
  if (req.method !== "POST") {
    send(res, 405, { ok: false, error: "Method not allowed" });
    return;
  }

  const token = process.env.GITHUB_TOKEN;
  if (!token) {
    send(res, 500, { ok: false, error: "ยังไม่ได้ตั้งค่า GITHUB_TOKEN ใน Vercel Environment Variables" });
    return;
  }

  try {
    const payload = JSON.parse(await readBody(req));
    if (String(payload.pin || "") !== String(PUBLISH_PIN)) {
      send(res, 401, { ok: false, error: "รหัสเผยแพร่ไม่ถูกต้อง" });
      return;
    }
    if (!payload.content || typeof payload.content !== "object") {
      send(res, 400, { ok: false, error: "ข้อมูลเว็บไซต์ไม่ถูกต้อง" });
      return;
    }

    const apiUrl = `https://api.github.com/repos/${OWNER}/${REPO}/contents/${FILE_PATH}`;
    const headers = {
      Authorization: `Bearer ${token}`,
      Accept: "application/vnd.github+json",
      "Content-Type": "application/json",
      "X-GitHub-Api-Version": "2022-11-28"
    };

    const current = await githubJson(`${apiUrl}?ref=${encodeURIComponent(BRANCH)}`, { headers });
    const source = siteContentSource(payload.content);
    const update = await githubJson(apiUrl, {
      method: "PUT",
      headers,
      body: JSON.stringify({
        message: `Update Wellsound24 content ${new Date().toISOString()}`,
        branch: BRANCH,
        content: Buffer.from(source, "utf8").toString("base64"),
        sha: current.sha
      })
    });

    send(res, 200, { ok: true, commit: update.commit?.sha || null });
  } catch (error) {
    send(res, 500, { ok: false, error: error.message || "บันทึกไม่สำเร็จ" });
  }
};
