// A contact click is an intent signal, not a confirmed enquiry or sale.
export function contactChannel(link) {
 if (!link) return '';
 const href = link.getAttribute('href') || '';
 if (/^tel:\+?\d/.test(href)) return 'tel';
 let url;
 try { url = new URL(href); } catch { return ''; }
 if (url.protocol !== 'https:') return '';
 if (url.hostname === 'line.me' || url.hostname === 'lin.ee') return 'line';
 if (['facebook.com','www.facebook.com','m.me'].includes(url.hostname) &&
     (link.hasAttribute('data-contact') || link.closest('.contact-options,.hero-actions,.mobile-contact') ||
      (link.closest('#main-nav') && link.classList.contains('button'))) &&
     !/\/photos(?:\/|$)/.test(url.pathname)) return 'facebook';
 return '';
}

export function recordContactClick(link, gtag) {
 const channel = contactChannel(link);
 if (!channel || typeof gtag !== 'function') return false;
 gtag('event','contact_click',{contact_channel:channel,transport_type:'beacon'});
 gtag('event','conversion',{send_to:'AW-11531689060/6l_kCN_O2oAdEOS43voq',transport_type:'beacon'});
 return true;
}
