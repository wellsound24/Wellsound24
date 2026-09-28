import {recordContactClick} from './contact-shared.js';
const params = new URLSearchParams(location.search);
if (!params.has('editor') && !params.has('preview')) {
 document.addEventListener('click', event => {
  // Leave navigation, modifier keys and open-in-new-tab behavior to the browser.
  if (event.button !== 0) return;
  recordContactClick(event.target.closest?.('a[href]'), window.gtag);
 });
}
