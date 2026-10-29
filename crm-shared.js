export const STATUSES={new:'ลูกค้าใหม่',waiting:'รอตัดสินใจ',quoted:'ส่งใบเสนอราคาแล้ว',confirmed:'คอนเฟิร์มแล้ว',lost:'ไม่ได้งาน',needs_review:'รอตรวจสอบ'};
export const SOURCES={unknown:'ยังระบุไม่ได้',website:'เว็บไซต์',line:'LINE OA',phone:'โทรศัพท์',google_ads:'Google Ads',facebook:'Facebook',other:'อื่น ๆ'};
export const DEPOSITS={unverified:'ยังไม่ตรวจสอบ',pending:'รอมัดจำ',received:'รับมัดจำแล้ว'};
export const money=value=>value===null||value===undefined?'ยังไม่ระบุ':Number(value).toLocaleString('th-TH',{maximumFractionDigits:2})+' บาท';
export const dateLabel=value=>value?new Date(value.length===10?value+'T00:00:00+07:00':value).toLocaleDateString('th-TH',{timeZone:'Asia/Bangkok',day:'numeric',month:'short',year:'numeric'}):'ยังไม่ระบุ';
