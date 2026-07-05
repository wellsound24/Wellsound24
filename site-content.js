window.WELLSOUND_STORAGE_KEY = "wellsound24_site_content_v2";
window.WELLSOUND_PIN_KEY = "wellsound24_admin_pin";

window.WELLSOUND_DEFAULTS = {
  version: 4,
  site: {
    title: "Wellsound24 | เช่าเครื่องเสียง เวที ไฟ และจอ LED",
    description: "Wellsound24 ให้เช่าเครื่องเสียง เวที ไฟ และจอ LED พร้อมทีมงานมืออาชีพในกรุงเทพฯ",
    phone: "0932614296",
    email: "",
    lineId: "",
    linePhone: "",
    lineUrl: "",
    facebookUrl: "",
    address: "กรุงเทพฯ และพื้นที่ใกล้เคียง",
    logo: "assets/logo.png",
    favicon: "assets/favicon.png"
  },
  design: {
    primaryColor: "#f6b800",
    primaryLight: "#ffd65a",
    backgroundColor: "#070707",
    surfaceColor: "#151515",
    textColor: "#ffffff",
    mutedColor: "#b5b5b5",
    lineColor: "rgba(255,255,255,.11)",
    fontFamily: "Kanit",
    containerWidth: 1180,
    borderRadius: 22,
    buttonRadius: 13,
    sectionPadding: 110,
    cardShadow: true
  },
  layout: {
    sectionOrder: ["hero", "services", "why", "portfolio", "contact"],
    visibility: {
      hero: true,
      services: true,
      why: true,
      portfolio: true,
      contact: true
    }
  },
  textStyles: {},
  header: {
    visible: true,
    sticky: true,
    backgroundOpacity: 94,
    logoSize: 102,
    showCta: false,
    ctaText: "ขอใบเสนอราคา",
    ctaLink: "#contact",
    menu: [
      { label: "บริการ", target: "#services" },
      { label: "จุดเด่น", target: "#why-us" },
      { label: "ผลงาน", target: "#portfolio" },
      { label: "ติดต่อ", target: "#contact" }
    ]
  },
  hero: {
    eyebrow: "EVENT PRODUCTION • BANGKOK",
    titleLine1: "ยกระดับทุกงาน",
    titleLine2: "ด้วยแสง สี เสียงมืออาชีพ",
    description: "ให้เช่าเครื่องเสียง เวที ระบบไฟ และจอ LED สำหรับงานแต่ง งานบริษัท งานเลี้ยง มินิคอนเสิร์ต และอีเวนต์ทุกประเภท",
    primaryButton: "ขอใบเสนอราคา",
    primaryLink: "#contact",
    secondaryButton: "ดูบริการของเรา",
    secondaryLink: "#services",
    showPrimaryButton: true,
    showSecondaryButton: true,
    image: "",
    imagePosition: "center center",
    overlayOpacity: 68,
    minHeight: 100,
    alignment: "center",
    contentWidth: 940,
    titleSize: 100,
    showLights: true,
    showStats: true
  },
  stats: [
    { title: "ครบวงจร", text: "เสียง • ไฟ • เวที • LED" },
    { title: "ทีมงานมืออาชีพ", text: "ดูแลตั้งแต่ติดตั้งถึงจบงาน" },
    { title: "รองรับทุกขนาด", text: "งานเล็กจนถึงงานใหญ่" }
  ],
  servicesSection: {
    eyebrow: "OUR SERVICES",
    title: "บริการของ Wellsound24",
    description: "เลือกใช้บริการแยกส่วน หรือให้เราดูแลระบบภายในงานแบบครบวงจร",
    columns: 4,
    background: "default"
  },
  services: [
    { icon: "♫", title: "ระบบเครื่องเสียง", description: "ลำโพง มิกเซอร์ ไมโครโฟน และอุปกรณ์เสียง พร้อมทีมควบคุมระบบ" },
    { icon: "▱", title: "เวทีและโครงสร้าง", description: "เวทีสำหรับงานแสดง งานแต่ง งานบริษัท และมินิคอนเสิร์ต" },
    { icon: "✦", title: "ระบบแสงและเอฟเฟกต์", description: "ไฟเวที Moving Head ไฟบรรยากาศ และเอฟเฟกต์เพิ่มความโดดเด่น" },
    { icon: "▦", title: "จอ LED", description: "จอ LED สำหรับพรีเซนเทชัน ถ่ายทอดสด และแสดงภาพภายในงาน" }
  ],
  why: {
    eyebrow: "WHY WELLSOUND24",
    titleLine1: "ดูแลทุกขั้นตอน",
    titleLine2: "ให้คุณจัดงานได้อย่างมั่นใจ",
    description: "เราช่วยวางแผนอุปกรณ์ให้เหมาะกับพื้นที่ จำนวนแขก และรูปแบบงาน พร้อมติดตั้ง ทดสอบ และควบคุมระบบตลอดงาน",
    items: [
      "ประเมินหน้างานและจัดชุดอุปกรณ์ให้เหมาะสม",
      "ติดตั้งและทดสอบระบบก่อนเริ่มงาน",
      "มีทีมงานดูแลระบบตลอดกิจกรรม",
      "ให้บริการในกรุงเทพฯ และพื้นที่ใกล้เคียง"
    ],
    visualType: "stage",
    image: "",
    imagePosition: "center center",
    screenLine1: "WELL",
    screenLine2: "SOUND24"
  },
  portfolioSection: {
    eyebrow: "EVENT TYPES",
    title: "รองรับงานหลากหลายรูปแบบ",
    description: "ผลงานและรูปแบบงานที่เรารับดูแล",
    columns: 2
  },
  portfolio: [
    { label: "MINI CONCERT", title: "มินิคอนเสิร์ต", description: "", image: "", imagePosition: "center center", link: "#contact" },
    { label: "WEDDING", title: "งานแต่งงาน", description: "", image: "", imagePosition: "center center", link: "#contact" },
    { label: "CORPORATE", title: "งานบริษัท", description: "", image: "", imagePosition: "center center", link: "#contact" },
    { label: "PRIVATE PARTY", title: "งานเลี้ยงและปาร์ตี้", description: "", image: "", imagePosition: "center center", link: "#contact" }
  ],
  contact: {
    eyebrow: "CONTACT US",
    title: "กำลังวางแผนจัดงาน?",
    description: "ส่งรายละเอียดวันจัดงาน สถานที่ จำนวนแขก และอุปกรณ์ที่ต้องการ เพื่อให้เราช่วยประเมินราคาได้รวดเร็วขึ้น",
    phoneButton: "โทรสอบถาม",
    lineButton: "คุยผ่าน LINE",
    facebookButton: "ติดต่อทาง Facebook",
    note: "พร้อมให้คำปรึกษาและประเมินราคาตามรายละเอียดของงาน",
    showPhoneButton: true,
    showLineButton: true,
    showFacebookButton: true,
    showForm: true,
    form: {
      nameLabel: "ชื่อผู้ติดต่อ",
      namePlaceholder: "ชื่อของคุณ",
      phoneLabel: "เบอร์โทรศัพท์",
      phonePlaceholder: "08x-xxx-xxxx",
      eventLabel: "ประเภทงาน",
      eventPlaceholder: "เลือกประเภทงาน",
      detailsLabel: "รายละเอียดเพิ่มเติม",
      detailsPlaceholder: "วันที่ สถานที่ จำนวนแขก และอุปกรณ์ที่ต้องการ",
      submitButton: "เตรียมข้อความส่ง LINE",
      eventTypes: ["งานแต่งงาน", "งานบริษัท", "มินิคอนเสิร์ต", "งานเลี้ยง / ปาร์ตี้", "งานอื่น ๆ"]
    }
  },
  footer: {
    text: "Wellsound24. All rights reserved.",
    showLogo: true,
    showAdminLink: true,
    adminLinkText: "จัดการเว็บไซต์",
    showContact: true,
    contactText: "โทร 093-261-4296",
    backgroundColor: "#050505"
  },
  floating: {
    showLine: true,
    label: "LINE"
  }
};
