import {imageManifest} from './image-manifest.js';
export function optimizeImages(document) {
 for(const img of document.querySelectorAll('main img[src]')) {
  const src=img.getAttribute('src');
  const item=imageManifest[src]||Object.values(imageManifest).find(x=>x.src===src);
  if(!item)continue;
  if(img.closest('.photo-open')&&!img.dataset.fullSrc)img.dataset.fullSrc=src;
  img.setAttribute('src',item.src);
  img.setAttribute('srcset',item.srcset);
  img.setAttribute('sizes',img.closest('.hero-photo')?'(max-width: 760px) 100vw, 56vw':img.closest('.feature')?'(max-width: 760px) 46vw, 24vw':'(max-width: 760px) 92vw, (max-width: 1100px) 44vw, 30vw');
  img.setAttribute('width',String(item.width));img.setAttribute('height',String(item.height));
  img.setAttribute('decoding','async');
  if(img.closest('.hero-photo')){img.setAttribute('fetchpriority','high');img.setAttribute('loading','eager');}
 }
}
