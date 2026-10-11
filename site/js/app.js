(() => {
  const WHATSAPP = '972548803868';
  const S = window.SOL_STRINGS;
  const $ = (id) => document.getElementById(id);
  const grid = $('grid');
  const canHover = matchMedia('(hover: hover) and (pointer: fine)').matches;

  let lang = 'he';
  try { lang = localStorage.getItem('sol-lang') || 'he'; } catch {}
  let posts = [];
  let categories = [];
  let activeCat = 'all';
  let visible = [];

  // ---------- language ----------
  function applyLang() {
    const t = S[lang];
    document.documentElement.lang = lang;
    document.documentElement.dir = t.dir;
    $('langToggle').textContent = t.toggle;
    document.querySelectorAll('[data-i18n]').forEach((el) => { el.textContent = t[el.dataset.i18n]; });
    const wa = `https://wa.me/${WHATSAPP}?text=${encodeURIComponent(t.waMessage)}`;
    $('waButton').href = wa;
    $('waFloat').href = wa;
    renderFilters();
  }
  $('langToggle').addEventListener('click', () => {
    lang = lang === 'he' ? 'en' : 'he';
    try { localStorage.setItem('sol-lang', lang); } catch {}
    applyLang();
  });

  // ---------- data ----------
  const shortcodeOf = (s) => (String(s).match(/\/(?:p|reel|tv)\/([^/?#]+)/) || [])[1] || String(s).trim();

  async function load() {
    const [p, c, h] = await Promise.all(
      ['content/posts.json', 'content/categories.json', 'content/hidden.json']
        .map((u) => fetch(u, { cache: 'no-cache' }).then((r) => (r.ok ? r.json() : null)).catch(() => null)),
    );
    const hidden = new Set((h || []).map(shortcodeOf));
    categories = c || [];
    posts = ((p && p.posts) || [])
      .filter((x) => !hidden.has(x.id) && !hidden.has(shortcodeOf(x.permalink || '')))
      .sort((a, b) => (b.timestamp || '').localeCompare(a.timestamp || ''));
    $('sampleNote').hidden = !posts.some((x) => x.sample);
    applyLang();
    render();
  }

  // ---------- filters ----------
  function renderFilters() {
    const el = $('filters');
    const used = categories.filter((c) => posts.some((p) => (p.categories || []).includes(c.id)));
    if (!used.some((c) => c.id === activeCat)) activeCat = 'all';
    const chips = [{ id: 'all', label: S[lang].all }, ...used.map((c) => ({ id: c.id, label: c[lang] }))];
    el.hidden = used.length === 0;
    el.innerHTML = '';
    chips.forEach((c) => {
      const b = document.createElement('button');
      b.className = 'chip';
      b.type = 'button';
      b.setAttribute('role', 'tab');
      b.setAttribute('aria-selected', String(c.id === activeCat));
      b.textContent = c.label;
      b.onclick = () => { activeCat = c.id; renderFilters(); render(); };
      el.appendChild(b);
    });
  }

  // ---------- grid ----------
  const ICONS = {
    video: '<svg viewBox="0 0 24 24"><path d="M8 5.5v13l10-6.5z" class="fill"/></svg>',
    carousel: '<svg viewBox="0 0 24 24"><rect x="7" y="7" width="13" height="13" rx="2" class="fill"/><path d="M4 16V6a2 2 0 0 1 2-2h10" stroke-width="2"/></svg>',
  };

  function tileFor(post, index) {
    const first = post.media[0];
    const t = document.createElement('button');
    t.type = 'button';
    t.className = 'tile';
    t.setAttribute('aria-label', (post.caption || '').slice(0, 80) || 'SOL');
    t.onclick = () => openLightbox(index);

    if (post.sample) {
      t.innerHTML = `<div class="swatch" style="background:${post.color}"><span>${post.label || ''}</span></div>`;
    } else {
      const img = document.createElement('img');
      img.loading = index < 8 ? 'eager' : 'lazy';
      img.decoding = 'async';
      img.alt = '';
      img.src = first.type === 'video' ? first.poster : first.thumb || first.src;
      t.appendChild(img);
      if (first.type === 'video' && !first.external) {
        const v = document.createElement('video');
        v.muted = true;
        v.loop = true;
        v.playsInline = true;
        v.preload = 'none';
        v.dataset.src = first.preview || first.src;
        t.appendChild(v);
        t.classList.add('has-video');
        if (canHover) {
          t.addEventListener('mouseenter', () => playTile(t));
          t.addEventListener('mouseleave', () => stopTile(t));
        }
      }
    }
    const kind = post.media.length > 1 ? 'carousel' : first.type === 'video' ? 'video' : null;
    if (kind) t.insertAdjacentHTML('beforeend', `<span class="badge">${ICONS[kind]}</span>`);
    return t;
  }

  function render() {
    stopAll();
    visible = activeCat === 'all' ? posts : posts.filter((p) => (p.categories || []).includes(activeCat));
    grid.innerHTML = '';
    const frag = document.createDocumentFragment();
    visible.forEach((p, i) => frag.appendChild(tileFor(p, i)));
    grid.appendChild(frag);
    $('empty').hidden = visible.length > 0;
    if (!canHover) requestAnimationFrame(updateTopRow);
  }

  // ---------- video previews ----------
  function playTile(t) {
    const v = t.querySelector('video');
    if (!v) return;
    if (!v.src) v.src = v.dataset.src;
    const p = v.play();
    if (p) p.then(() => t.classList.add('playing')).catch(() => {});
  }
  function stopTile(t) {
    const v = t.querySelector('video');
    if (!v) return;
    v.pause();
    t.classList.remove('playing');
  }
  function stopAll() { grid.querySelectorAll('.tile.playing').forEach(stopTile); }

  // Touch devices: autoplay (muted) the videos in the topmost row that is mostly on screen.
  function updateTopRow() {
    if (!$('lightbox').hidden) return;
    const vh = window.innerHeight;
    let rowTop = null;
    for (const t of grid.children) {
      const r = t.getBoundingClientRect();
      if (r.bottom < 0) continue;
      if (r.top > vh) break;
      const shown = Math.min(r.bottom, vh) - Math.max(r.top, 0);
      if (shown >= r.height * 0.6) { rowTop = r.top; break; }
    }
    grid.querySelectorAll('.tile.has-video').forEach((t) => {
      const inRow = rowTop !== null && Math.abs(t.getBoundingClientRect().top - rowTop) < 2;
      if (inRow) { if (!t.classList.contains('playing')) playTile(t); }
      else if (t.classList.contains('playing')) stopTile(t);
    });
  }
  if (!canHover) {
    let ticking = false;
    const onScroll = () => {
      if (ticking) return;
      ticking = true;
      requestAnimationFrame(() => { ticking = false; updateTopRow(); });
    };
    addEventListener('scroll', onScroll, { passive: true });
    addEventListener('resize', onScroll);
  }

  // ---------- floating WhatsApp: hide when the footer button is on screen ----------
  new IntersectionObserver(([e]) => $('waFloat').classList.toggle('hide', e.isIntersecting))
    .observe($('waButton'));

  // ---------- lightbox ----------
  let lbPost = 0;
  let lbSlide = 0;

  function openLightbox(i) {
    stopAll();
    lbPost = i;
    lbSlide = 0;
    $('lightbox').hidden = false;
    document.body.style.overflow = 'hidden';
    showSlide();
  }
  function closeLightbox() {
    const v = $('lbStage').querySelector('video');
    if (v) v.pause();
    $('lbStage').innerHTML = '';
    $('lightbox').hidden = true;
    document.body.style.overflow = '';
    if (!canHover) updateTopRow();
  }
  function showSlide() {
    const post = visible[lbPost];
    const m = post.media[lbSlide];
    const stage = $('lbStage');
    stage.innerHTML = '';
    if (post.sample) {
      stage.innerHTML = `<div class="swatch-lg" style="background:${post.color}"></div>`;
    } else if (m.type === 'video' && m.external) {
      // Instagram doesn't allow downloading Reels with licensed music, so play them there.
      stage.innerHTML = `<a class="lb-external" href="${post.permalink}" target="_blank" rel="noopener">
        <img src="${m.poster}" alt=""><span>${ICONS.video}${S[lang].watchOnInstagram}</span></a>`;
    } else if (m.type === 'video') {
      const v = document.createElement('video');
      v.src = m.src;
      v.poster = m.poster || '';
      v.controls = true;
      v.playsInline = true;
      v.autoplay = true; // opened by a tap, so sound is allowed
      stage.appendChild(v);
    } else {
      const img = document.createElement('img');
      img.src = m.src;
      img.alt = post.caption || '';
      stage.appendChild(img);
    }
    $('lbCaption').textContent = post.caption || '';
    $('lbLink').hidden = !post.permalink;
    $('lbLink').href = post.permalink || '#';
    $('lbDots').innerHTML = post.media.length > 1
      ? post.media.map((_, i) => `<i class="${i === lbSlide ? 'on' : ''}"></i>`).join('')
      : '';
  }
  function step(dir) {
    const post = visible[lbPost];
    const next = lbSlide + dir;
    if (next >= 0 && next < post.media.length) { lbSlide = next; }
    else {
      const np = lbPost + dir;
      if (np < 0 || np >= visible.length) return;
      lbPost = np;
      lbSlide = dir > 0 ? 0 : visible[np].media.length - 1;
    }
    showSlide();
  }
  // In RTL the "next" arrow sits on the left, so step direction follows the visual side.
  $('lbNext').onclick = () => step(1);
  $('lbPrev').onclick = () => step(-1);
  $('lbClose').onclick = closeLightbox;
  $('lightbox').addEventListener('click', (e) => {
    if (e.target.id === 'lightbox' || e.target.id === 'lbStage') closeLightbox();
  });
  addEventListener('keydown', (e) => {
    if ($('lightbox').hidden) return;
    const rtl = document.documentElement.dir === 'rtl';
    if (e.key === 'Escape') closeLightbox();
    if (e.key === 'ArrowRight') step(rtl ? -1 : 1);
    if (e.key === 'ArrowLeft') step(rtl ? 1 : -1);
  });
  let touchX = null;
  $('lbStage').addEventListener('touchstart', (e) => { touchX = e.touches[0].clientX; }, { passive: true });
  $('lbStage').addEventListener('touchend', (e) => {
    if (touchX === null) return;
    const dx = e.changedTouches[0].clientX - touchX;
    touchX = null;
    if (Math.abs(dx) < 40) return;
    const rtl = document.documentElement.dir === 'rtl';
    step((dx < 0) !== rtl ? 1 : -1);
  });

  $('year').textContent = new Date().getFullYear();
  load();
})();
