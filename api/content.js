const GOOGLE_DRIVE_FILE_ID = process.env.GOOGLE_DRIVE_FILE_ID || "";
const GOOGLE_API_KEY = process.env.GOOGLE_API_KEY || "";
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
        reject(new Error("Payload is too large"));
        req.destroy();
      }
    });
    req.on("end", () => resolve(body));
    req.on("error", reject);
  });
}

function requireDriveFileId() {
  if (!GOOGLE_DRIVE_FILE_ID) {
    throw new Error("Missing GOOGLE_DRIVE_FILE_ID in Vercel Environment Variables");
  }
}

async function readDriveContent() {
  requireDriveFileId();
  const url = new URL(`https://www.googleapis.com/drive/v3/files/${GOOGLE_DRIVE_FILE_ID}`);
  url.searchParams.set("alt", "media");
  if (GOOGLE_API_KEY) url.searchParams.set("key", GOOGLE_API_KEY);

  const response = await fetch(url);
  const text = await response.text();
  if (!response.ok) {
    throw new Error(text || `Google Drive read failed (${response.status})`);
  }
  return text ? JSON.parse(text) : null;
}

async function writeDriveContent(accessToken, content) {
  requireDriveFileId();
  if (!accessToken) {
    throw new Error("Missing Google OAuth access token");
  }

  const response = await fetch(
    `https://www.googleapis.com/upload/drive/v3/files/${GOOGLE_DRIVE_FILE_ID}?uploadType=media`,
    {
      method: "PATCH",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json; charset=utf-8"
      },
      body: JSON.stringify(content)
    }
  );
  const text = await response.text();
  if (!response.ok) {
    throw new Error(text || `Google Drive save failed (${response.status})`);
  }
  return text ? JSON.parse(text) : {};
}

async function makeDriveFileReadable(accessToken) {
  const response = await fetch(
    `https://www.googleapis.com/drive/v3/files/${GOOGLE_DRIVE_FILE_ID}/permissions`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json; charset=utf-8"
      },
      body: JSON.stringify({ role: "reader", type: "anyone" })
    }
  );
  if (!response.ok && response.status !== 400 && response.status !== 409) {
    const text = await response.text();
    throw new Error(text || `Google Drive permission update failed (${response.status})`);
  }
}

module.exports = async function handler(req, res) {
  try {
    if (req.method === "GET") {
      const content = await readDriveContent();
      send(res, 200, { ok: true, content, updatedAt: null });
      return;
    }

    if (req.method === "POST") {
      const payload = JSON.parse(await readBody(req));
      if (String(payload.pin || "") !== String(PUBLISH_PIN)) {
        send(res, 401, { ok: false, error: "Publish PIN is incorrect" });
        return;
      }
      if (!payload.content || typeof payload.content !== "object") {
        send(res, 400, { ok: false, error: "Website content is invalid" });
        return;
      }

      const authorization = String(req.headers.authorization || "");
      const accessToken = authorization.replace(/^Bearer\s+/i, "").trim();
      const data = await writeDriveContent(accessToken, payload.content);
      await makeDriveFileReadable(accessToken);
      send(res, 200, { ok: true, updatedAt: data.modifiedTime || null });
      return;
    }

    send(res, 405, { ok: false, error: "Method not allowed" });
  } catch (error) {
    send(res, 500, { ok: false, error: error.message || "Save failed" });
  }
};
