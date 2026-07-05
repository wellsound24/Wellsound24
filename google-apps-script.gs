const STORE_KEY = "WELLSOUND24_CONTENT";
const UPDATED_KEY = "WELLSOUND24_UPDATED_AT";
const PIN_KEY = "WELLSOUND24_PIN";

function jsonOutput(payload) {
  return ContentService
    .createTextOutput(JSON.stringify(payload))
    .setMimeType(ContentService.MimeType.JSON);
}

function doGet() {
  return doPost({ postData: { contents: JSON.stringify({ action: "get" }) } });
}

function doPost(e) {
  try {
    const payload = JSON.parse(e.postData.contents || "{}");
    const properties = PropertiesService.getScriptProperties();
    const pin = properties.getProperty(PIN_KEY) || "2468";

    if (payload.action === "get") {
      const raw = properties.getProperty(STORE_KEY);
      return jsonOutput({
        ok: true,
        content: raw ? JSON.parse(raw) : null,
        updatedAt: properties.getProperty(UPDATED_KEY) || null
      });
    }

    if (payload.action === "save") {
      if (String(payload.pin || "") !== String(pin)) {
        return jsonOutput({ ok: false, error: "รหัสเผยแพร่ไม่ถูกต้อง" });
      }
      if (!payload.content || typeof payload.content !== "object") {
        return jsonOutput({ ok: false, error: "ข้อมูลเว็บไซต์ไม่ถูกต้อง" });
      }
      const updatedAt = new Date().toISOString();
      properties.setProperty(STORE_KEY, JSON.stringify(payload.content));
      properties.setProperty(UPDATED_KEY, updatedAt);
      return jsonOutput({ ok: true, updatedAt });
    }

    return jsonOutput({ ok: false, error: "Unknown action" });
  } catch (error) {
    return jsonOutput({ ok: false, error: error.message || "Apps Script error" });
  }
}
