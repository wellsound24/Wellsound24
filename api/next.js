const TARGET = 'https://sjcxywxixgrpdgeqaepk.supabase.co/functions/v1/admin-license-next';

export default async function handler(req, res) {
  if (req.method === 'OPTIONS') {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Headers', 'content-type, authorization');
    res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
    return res.status(204).end();
  }
  if (req.method !== 'POST') return res.status(405).json({ ok: false, error: 'method_not_allowed' });
  try {
    const headers = { 'content-type': 'application/json' };
    if (req.headers.authorization) headers.authorization = req.headers.authorization;
    const r = await fetch(TARGET, {
      method: 'POST',
      headers,
      body: JSON.stringify(req.body || {})
    });
    const text = await r.text();
    res.setHeader('Cache-Control', 'no-store');
    res.setHeader('Content-Type', r.headers.get('content-type') || 'application/json; charset=utf-8');
    return res.status(r.status).send(text);
  } catch (e) {
    return res.status(502).json({ ok: false, error: 'proxy_error' });
  }
}
