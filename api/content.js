const GOOGLE_SCRIPT_URL = process.env.GOOGLE_SCRIPT_URL || "";
const PUBLISH_PIN = process.env.PUBLISH_PIN || process.env.ADMIN_PIN || "2468";

function send(res, status, payload) {
  res.statusCode = status;
  res.setHeader("Content-Type", "application/json; charset=utf-8");
  res.setHeader("Cache-Control", "no-store");
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

async function callGoogle(payload) {
  if (!GOOGLE_SCRIPT_URL) {
    throw new Error("ยังไม่ได้ตั้งค่า GOOGLE_SCRIPT_URL ใน Vercel Environment Variables");
  }

  const response = await fetch(GOOGLE_SCRIPT_URL, {
    method: "POST",
    headers: { "Content-Type": "text/plain;charset=utf-8" },
    body: JSON.stringify(payload)
  });
  const text = await response.text();
  let data;
  try {
    data = JSON.parse(text);
  } catch (error) {
    throw new Error("Google Apps Script ตอบกลับไม่ใช่ JSON");
  }
  if (!response.ok || data.ok === false) {
    throw new Error(data.error || `Google Apps Script error ${response.status}`);
  }
  return data;
}

module.exports = async function handler(req, res) {
  try {
    if (req.method === "GET") {
      const data = await callGoogle({ action: "get" });
      send(res, 200, { ok: true, content: data.content || null, updatedAt: data.updatedAt || null });
      return;
    }

    if (req.method === "POST") {
      const payload = JSON.parse(await readBody(req));
      if (String(payload.pin || "") !== String(PUBLISH_PIN)) {
        send(res, 401, { ok: false, error: "รหัสเผยแพร่ไม่ถูกต้อง" });
        return;
      }
      if (!payload.content || typeof payload.content !== "object") {
        send(res, 400, { ok: false, error: "ข้อมูลเว็บไซต์ไม่ถูกต้อง" });
        return;
      }
      const data = await callGoogle({ action: "save", pin: PUBLISH_PIN, content: payload.content });
      send(res, 200, { ok: true, updatedAt: data.updatedAt || null });
      return;
    }

    send(res, 405, { ok: false, error: "Method not allowed" });
  } catch (error) {
    send(res, 500, { ok: false, error: error.message || "บันทึกไม่สำเร็จ" });
  }
};
