# วิธีนำ Wellsound ขึ้น GitHub และ Vercel

## 1. GitHub

ใช้ repository `wellsound24/Wellsound24` แล้วอัปโหลดไฟล์ทั้งหมดในโฟลเดอร์นี้ขึ้นไป:

- `admin/`
- `assets/`
- `data.json`
- `index.html`
- `README.md`
- `vercel.json`

## 2. Vercel

1. เปิด Vercel แล้วเลือก `Add New Project`
2. Import repository `Wellsound24`
3. ใช้ค่าเริ่มต้นของ Vercel ได้เลย เพราะเว็บนี้เป็น static website
4. Deploy

## 3. Dashboard

หลัง deploy แล้ว เปิด:

`https://your-domain.vercel.app/admin`

ในหน้า Dashboard สามารถแก้เนื้อหาได้ และถ้าต้องการให้กดบันทึกกลับ GitHub ได้ ให้ใส่:

- GitHub owner หรือ organization
- Repository: `Wellsound24`
- Branch: `main`
- Data path: `data.json`
- Fine-grained GitHub token ที่มีสิทธิ์ Contents: Read and write

เมื่อกด `บันทึกลง GitHub` ระบบจะอัปเดต `data.json` และ Vercel จะ deploy ใหม่ผ่าน GitHub integration
