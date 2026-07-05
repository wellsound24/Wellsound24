# Wellsound Website

Static website and editable dashboard for Wellsound.

## Files

- `index.html` is the public website.
- `admin/index.html` is the dashboard.
- `data.json` stores website content.
- `assets/site.css`, `assets/app.js`, and `assets/admin.js` control the design and editing behavior.

## Dashboard

Open `admin/` to edit content. Use:

1. `บันทึกและดูตัวอย่าง` to save changes in the current browser.
2. `ดาวน์โหลด data.json` to export the updated content file.
3. `บันทึกลง GitHub` to commit changes to `data.json` through the GitHub API.

GitHub defaults are configured for `wellsound24/Wellsound24`. Add a fine-grained GitHub token with Contents read/write permission in the dashboard before publishing content changes.

When the repository is connected to Vercel, a GitHub commit will trigger a new deployment.