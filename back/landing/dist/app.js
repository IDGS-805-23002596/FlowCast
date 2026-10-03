const checkout = document.querySelector('#checkout');
const form = document.querySelector('#payment-form');
const success = document.querySelector('#payment-success');
const error = document.querySelector('#payment-error');
const pay = form.querySelector('button[type=submit]');
let timer;
function openDialog(dialog) { dialog.showModal(); document.body.classList.add('modal-open'); }
function resetPayment() { clearTimeout(timer); form.hidden = false; success.hidden = true; error.hidden = true; pay.disabled = false; pay.innerHTML = 'Pagar $200 MXN <span>→</span>'; }
document.querySelectorAll('[data-buy]').forEach(button => button.addEventListener('click', () => { resetPayment(); openDialog(checkout); }));
document.querySelectorAll('dialog').forEach(dialog => {
 dialog.querySelectorAll('[data-close]').forEach(button => button.addEventListener('click', () => dialog.close()));
 dialog.addEventListener('click', event => { if (event.target === dialog) { const r = dialog.getBoundingClientRect(); if (event.clientX < r.left || event.clientX > r.right || event.clientY < r.top || event.clientY > r.bottom) dialog.close(); } });
 dialog.addEventListener('close', () => { document.body.classList.remove('modal-open'); if(dialog === checkout) clearTimeout(timer); });
});
form.addEventListener('submit', event => {
 event.preventDefault(); if(pay.disabled) return; error.hidden = true; pay.disabled = true; pay.textContent = 'Procesando pago…';
 timer = setTimeout(() => {
  if (document.querySelector('#payment-result').value === 'decline') { error.textContent = 'No se pudo procesar el pago. Inténtalo de nuevo.'; error.hidden = false; pay.disabled = false; pay.textContent = 'Volver a intentar · $200 MXN'; }
  else { form.hidden = true; success.hidden = false; success.focus(); }
 }, 3000);
});
document.querySelectorAll('[data-screen]').forEach(button => button.addEventListener('click', () => { const img = document.querySelector('#lightbox-image'); img.src = button.dataset.screen; img.alt = button.querySelector('img').alt; openDialog(document.querySelector('#lightbox')); }));
document.querySelector('#year').textContent = new Date().getFullYear();

const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)').matches;
const videoObserver = new IntersectionObserver(entries => entries.forEach(({target, isIntersecting}) => {
 const video = target.querySelector('video');
 if(isIntersecting && !target.dataset.paused && !reducedMotion && !document.hidden) video.play().catch(() => updateVideoButton(target));
 else video.pause();
}), {threshold: 0.2});
function updateVideoButton(card) {
 const video=card.querySelector('video'), button=card.querySelector('.video-toggle');
 button.textContent=video.paused?'▶':'Ⅱ';
 button.setAttribute('aria-label', (video.paused?'Reproducir':'Pausar')+' clip');
}
document.querySelectorAll('.video-card').forEach(card => {
 const video=card.querySelector('video'); video.muted=true;
 if(reducedMotion) {video.autoplay=false;video.pause();}
 video.addEventListener('play',()=>updateVideoButton(card));
 video.addEventListener('pause',()=>updateVideoButton(card));
 card.querySelector('button').addEventListener('click',()=>{
  if(video.paused){delete card.dataset.paused;video.play().catch(()=>updateVideoButton(card));}
  else {card.dataset.paused='true';video.pause();}
 });
 updateVideoButton(card);videoObserver.observe(card);
});
document.addEventListener('visibilitychange',()=>document.querySelectorAll('.video-card').forEach(card=>{
 const video=card.querySelector('video');
 if(document.hidden) video.pause();
 else if(!card.dataset.paused&&!reducedMotion){const r=card.getBoundingClientRect();if(r.top<innerHeight&&r.bottom>0)video.play().catch(()=>{});}
}));
