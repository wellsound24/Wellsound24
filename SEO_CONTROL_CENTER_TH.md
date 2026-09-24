# SEO / การตลาด — เมนู 08

เมนูเดียวรวม AI keyword, title/description, H1/H2/เนื้อหา/FAQ/Local SEO, review/approve, technical SEO, verification, Search Console, ภาพแชร์, GA/Pixel, audit, rank tracking, dashboard, AI วิเคราะห์ผล และ ALT/internal links

## การใช้งาน

1. เลือกหน้า ข้อมูล SEO แยกตาม slug เดิม
2. แก้ฟอร์มเอง หรือขอข้อเสนอ AI จากเนื้อหา/Audit/รายงานจริง
3. กดตรวจข้อเสนอ ดูค่าเดิมและค่าใหม่ แล้วอนุมัติเป็นร่าง
4. บันทึกร่าง ดูตัวอย่าง แล้วเจ้าของกดเผยแพร่เว็บตามขั้นตอนเดิม

AI ไม่บันทึกหรือเผยแพร่เอง ข้อมูลและสิทธิ์ใช้ `w24_state` / `w24_save` ของระบบเดิม ไม่มีการย้ายฐานข้อมูลหรือแก้ตาราง License

หน้า 06 เพิ่มปุ่มลบสำหรับเจ้าของ พร้อมยืนยันและใช้ `delete_media` เดิม ซึ่งป้องกันไฟล์ที่ใช้ในร่าง เว็บไซต์ หรือประวัติ

## สิ่งที่แสดงบนเว็บจริง

`api/site.js` อ่านเฉพาะ published และใช้ตัวแสดงผลเดียวกับ browser/preview: head, H1, เนื้อหา, FAQ, ALT และลิงก์ถูกส่งใน HTML จากเซิร์ฟเวอร์โดยตรง canonical/OG tag ไม่ซ้ำ sitemap อ่านหน้าที่เผยแพร่และตัด noindex ออก

คง verification `i_DWSty-hy7Q3bgE97dqsr2QhN_tIA0xvSWGhPIl6HQ` และ Google Ads tag `AW-11531689060` เดิม ไม่สร้าง GA/Pixel ID หากไม่มีค่า GA/Pixel จะไม่โหลดเครื่องมือนั้น หากตั้ง ID ถูกต้องจะโหลดตาม consent เดิมของผู้ชม

## การเชื่อมต่อที่ใช้ credential จริง

ตั้งใน Supabase โครงการเดิม `sjcxywxixgrpdgeqaepk` → Edge Function Secrets:

- `OPENAI_API_KEY` สำหรับสร้างข้อเสนอ AI; `OPENAI_SEO_MODEL` เป็นตัวเลือก (ค่าเริ่มต้น `gpt-4.1-mini`)
- `GOOGLE_SERVICE_ACCOUNT_JSON` และให้ service account มีสิทธิ์อ่าน property `https://wellsound24.vercel.app/` ใน Search Console หรือใช้ `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REFRESH_TOKEN` ที่อนุญาต scope `webmasters.readonly`
- `PAGESPEED_API_KEY` เป็นตัวเลือกเมื่อ anonymous PageSpeed API ถูกจำกัดโควตา

ห้ามใส่ secret ในหน้า SEO, public settings หรือ commit Git ใช้ Edge Function `wellsound24-seo` ซึ่งตรวจ session/สมาชิกผ่าน `wellsound24-control` ทุกคำขอและใช้ rate limit เดิม ไม่เปลี่ยน auth ของระบบเดิม

Search Console query/inspection ใช้ read-only API verification meta ไม่ใช่สิทธิ์อ่าน API ระบบไม่แสดงตัวเลขจำลองหากขาด credential หรือ Google ยังไม่มีข้อมูล

## ขอบเขตตัวชี้วัด

- Audit ทำงานเมื่อเปิดเมนูหรือกดตรวจใหม่ ตรวจหน้าที่เผยแพร่ทั้งหมด, Title ซ้ำ และลิงก์ในเว็บสูงสุด 40 รายการต่อรอบ ไม่ตรวจลิงก์ภายนอก
- Rank tracking ใช้อันดับเฉลี่ยจาก Search Console ของ keyword ที่บันทึกแล้ว สูงสุด 20 คำ เปรียบเทียบ 28 วันกับ 28 วันก่อน โดยเว้นข้อมูลล่าสุด 3 วัน ไม่ใช่ SERP rank สด
- Dashboard แสดง click/impression/CTR/position, queries สูงสุด 1,000 แถวและแนวโน้มรายวัน Google อาจไม่คืนข้อมูลคำค้นทั้งหมด
- PageSpeed แยกคะแนน lab และ CrUX field metrics ไม่ตีความเวลาตอบกลับหน้าเว็บเป็น Core Web Vitals
- ชื่อไฟล์ภาพเป็นข้อเสนอสำหรับ upload ครั้งใหม่ ไม่เปลี่ยนชื่อไฟล์เดิมจนลิงก์เสีย

## ตรวจสอบก่อนเผยแพร่

`pnpm install --frozen-lockfile`, `pnpm build`, `pnpm test`

การทดสอบครอบคลุม SSR head/body, draft isolation, 404, schema/FAQ, sitemap/noindex, XSS escaping, audit และการคงเนื้อหาเดิม ต้องตรวจ authenticated admin และ provider APIs เพิ่มด้วย credential จริง
