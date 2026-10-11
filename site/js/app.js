(() => {
  const WHATSAPP = '972548803868';
  const PAGE = 24; // tiles shown before "More work"
  const S = window.SOL_STRINGS;
  const $ = (id) => document.getElementById(id);
  const grid = $('grid');
  const canHover = matchMedia('(hover: hover) and (pointer: fine)').matches;
  const reduceMotion = matchMedia('(prefers-reduced-motion: reduce)').matches;

  let lang = 'he';
  try { lang = localStorage.getItem('sol-lang') || 'he'; } catch {}
  let posts = [];
  let categories = [];
  let activeCat = new URLSearchParams(location.search).get('cat') || 'all';
  let visible = [];
  let shown = 0;

  const t = () => S[lang];
  const waLink = (text) => `https://wa.me/${WHATSAPP}?text=${encodeURIComponent(text)}`;
  const fmtDate = (ts) => (ts
    ? new Intl.DateTimeFormat(t().locale, { month: 'long', year: 'numeric' }).format(new Date(ts))
    : '');
  const catNames = (post) => categories
    .filter((c) => (post.categories || []).includes(c.id))
    .map((c) => c[lang]);
  const shortcodeOf = (s) => (String(s).match(/\/(?:p|reel|tv)\/([^/?#]+)/) || [])[1] || String(s).trim();

  // Captions come straight from Instagram: drop hashtags, "comment below" asks and booking chatter meant for the feed.
  // A feed ask is cut from where it starts, so the descriptive half of a mixed line survives.
  const FEED_ASK = /👇|תכתבו|תגיבו|ספרו לי|תייגו|שתפו|מה דעתכם|מה אומרות|לינק בביו|link in bio|comment below|דפדפו/i;
  const BOOKING_LINE = /תורים|תור פנוי|בקליניקה|נשארו מקומות|המקומות נתפסים|דברו איתי|\d{3}-?\d{7}/;
  function cleanCaption(text) {
    return String(text || '')
      .replace(/[⁦-⁩‎‏\t]/g, '')
      .split('\n')
      .map((l) => {
        l = l.replace(/(^|\s)#[\p{L}\p{N}_]+/gu, '').trim();
        const ask = l.search(FEED_ASK);
        if (ask !== -1) l = l.slice(0, ask).replace(/[\s,–—-]+$/u, '').trim();
        return l;
      })
      .filter((l) => l && !BOOKING_LINE.test(l))
      .join('\n');
  }
  // The caption to show: Hebrew text in Hebrew mode; style + date in English (captions are written in Hebrew).
  const captionFor = (post) => (lang === 'he' ? cleanCaption(post.caption) : '');

  // ---------- language ----------
  function applyLang() {
    const s = t();
    document.documentElement.lang = lang;
    document.title = s.title;
    document.querySelectorAll('.lang-toggle').forEach((b) => {
      b.textContent = s.toggle;
      b.setAttribute('aria-label', s.switchLang);
    });
    document.querySelectorAll('[data-i18n]').forEach((el) => { el.textContent = s[el.dataset.i18n]; });
    document.querySelectorAll('[data-i18n-label]').forEach((el) => el.setAttribute('aria-label', s[el.dataset.i18nLabel]));
    $('waButton').href = waLink(s.waMessage);
    $('waFloat').href = waLink(s.waMessage);
    $('heroCount').textContent = s.works(posts.length);
    if (typeof redrawSparkles === 'function') redrawSparkles(); // the info line width just changed
    applyTheme();
    renderFilters();
    grid.querySelectorAll('.tile').forEach((tile) => labelTile(tile, visible[+tile.dataset.i]));
    if (!$('lightbox').hidden) showSlide();
  }
  // ---------- dark / bright theme (dark by default; the first paint is set by the inline script in <head>) ----------
  const theme = () => document.documentElement.dataset.theme || 'dark';
  function applyTheme() {
    const dark = theme() === 'dark';
    document.querySelector('meta[name="theme-color"]').content = dark ? '#141210' : '#F3F0EB';
    document.querySelectorAll('.theme-toggle').forEach((b) => {
      b.setAttribute('aria-label', dark ? t().toLight : t().toDark);
      b.title = b.getAttribute('aria-label');
    });
  }
  document.querySelectorAll('.theme-toggle').forEach((b) => b.addEventListener('click', () => {
    document.documentElement.dataset.theme = theme() === 'dark' ? 'light' : 'dark';
    try { localStorage.setItem('sol-theme', theme()); } catch {}
    applyTheme();
  }));

  document.querySelectorAll('.lang-toggle').forEach((b) => b.addEventListener('click', () => {
    lang = lang === 'he' ? 'en' : 'he';
    try { localStorage.setItem('sol-lang', lang); } catch {}
    applyLang();
  }));

  // ---------- data ----------
  async function load() {
    $('loadError').hidden = true;
    // placeholder tiles in the real layout (large first tile) so nothing jumps when photos arrive
    grid.innerHTML = Array.from({ length: 9 }, (_, i) => `<div class="tile skeleton${i === 0 ? ' feature' : ''}" aria-hidden="true"></div>`).join('');
    const get = (u) => fetch(u, { cache: 'no-cache' }).then((r) => (r.ok ? r.json() : null)).catch(() => null);
    const [p, c, h, pin, cur] = await Promise.all(
      ['content/posts.json', 'content/categories.json', 'content/hidden.json', 'content/pinned.json', 'content/curation.json'].map(get),
    );
    if (!p) {
      grid.innerHTML = '';
      $('loadError').hidden = false;
      return;
    }
    const hidden = new Set((h || []).map(shortcodeOf));
    const pinned = (pin || []).map(shortcodeOf);
    categories = c || [];
    const rank = (x) => {
      const i = pinned.indexOf(shortcodeOf(x.permalink || ''));
      return i === -1 ? Infinity : i;
    };
    // Promos, tutorials and announcements stay on Instagram unless pinned or approved (content/curation.json).
    const keywords = ((cur && cur.autoHideKeywords) || []).map((k) => k.toLowerCase());
    const approved = new Set(((cur && cur.approved) || []).map(shortcodeOf));
    const isWork = (x) => {
      const code = shortcodeOf(x.permalink || '');
      if (approved.has(code) || pinned.includes(code)) return true;
      const cap = String(x.caption || '').toLowerCase();
      return !keywords.some((k) => cap.includes(k));
    };
    posts = (p.posts || [])
      .filter((x) => !hidden.has(x.id) && !hidden.has(shortcodeOf(x.permalink || '')) && isWork(x))
      .sort((a, b) => rank(a) - rank(b) || (b.timestamp || '').localeCompare(a.timestamp || ''));
    $('sampleNote').hidden = !posts.some((x) => x.sample);
    applyLang();
    render();
  }
  $('retryBtn').addEventListener('click', load);

  // ---------- filters (a few style-led choices; techniques are shown in the viewer instead) ----------
  const filterDefs = () => categories.filter((c) => c.filter);
  const matches = (post, def) => [def.id, ...(def.includes || [])].some((id) => (post.categories || []).includes(id));
  const usedFilters = () => filterDefs().filter((d) => posts.some((p) => matches(p, d)));

  function renderFilters() {
    const el = $('filters');
    const used = usedFilters();
    if (activeCat !== 'all' && !used.some((d) => d.id === activeCat)) activeCat = 'all';
    el.hidden = used.length === 0;
    el.innerHTML = '';
    [{ id: 'all', label: t().all }, ...used.map((d) => ({ id: d.id, label: typeof d.filter === 'object' ? d.filter[lang] : d[lang] }))].forEach((c) => {
      const b = document.createElement('button');
      b.className = 'chip';
      b.type = 'button';
      b.dataset.id = c.id;
      b.textContent = c.label;
      b.setAttribute('aria-pressed', String(c.id === activeCat));
      b.addEventListener('click', () => selectFilter(c.id));
      el.appendChild(b);
    });
  }
  function selectFilter(id) {
    activeCat = id;
    // update in place so keyboard focus stays on the pressed button
    $('filters').querySelectorAll('.chip').forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.id === id)));
    const url = new URL(location.href);
    if (id === 'all') url.searchParams.delete('cat'); else url.searchParams.set('cat', id);
    history.replaceState(null, '', url);
    render();
    $('status').textContent = t().works(visible.length); // one calm announcement instead of the whole grid
  }

  // ---------- grid ----------
  const ICONS = {
    video: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8 5.5v13l10-6.5z" class="fill"/></svg>',
    carousel: '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="7" y="7" width="13" height="13" rx="2" class="fill"/><path d="M4 16V6a2 2 0 0 1 2-2h10" stroke-width="2"/></svg>',
  };

  function labelTile(tile, post) {
    if (!tile || !post) return;
    const names = catNames(post);
    tile.setAttribute('aria-label', [names.join(', ') || t().work, fmtDate(post.timestamp)].filter(Boolean).join(' · '));
    const cap = tile.querySelector('.tile-cap');
    if (!cap) return;
    const line = captionFor(post).split('\n')[0] || names.slice(0, 2).join(' · ');
    cap.querySelector('b').textContent = line;
    cap.querySelector('b').hidden = !line;
    cap.querySelector('small').textContent = fmtDate(post.timestamp);
  }

  function tileFor(post, index) {
    const first = post.media[0];
    const tile = document.createElement('button');
    tile.type = 'button';
    tile.className = 'tile';
    tile.dataset.i = index;
    tile.addEventListener('click', () => openLightbox(index, tile));
    if (index < 12) tile.style.setProperty('--i', index % PAGE); // stagger only the first screenful
    if (index === 0 && activeCat === 'all') tile.classList.add('feature');

    if (post.sample) {
      tile.innerHTML = `<div class="swatch" style="background:${post.color}"><span>${post.label || ''}</span></div>`;
    } else {
      const img = document.createElement('img');
      img.loading = index < 8 ? 'eager' : 'lazy';
      img.decoding = 'async';
      img.alt = '';
      // the large featured tile uses the full image; others the small thumbnail
      img.src = first.type === 'video' ? first.poster : (index === 0 && activeCat === 'all' ? first.src : first.thumb || first.src);
      tile.appendChild(img);
      if (first.type === 'video' && !first.external && !reduceMotion) {
        const v = document.createElement('video');
        v.muted = true;
        v.loop = true;
        v.playsInline = true;
        v.preload = 'none';
        v.setAttribute('aria-hidden', 'true');
        v.dataset.src = first.preview || first.src;
        tile.appendChild(v);
        tile.classList.add('has-video');
        if (canHover && post.media.length === 1) {
          tile.addEventListener('mouseenter', () => playTile(tile));
          tile.addEventListener('mouseleave', () => stopTile(tile));
        }
      }
      if (canHover && post.media.length > 1 && !reduceMotion) addSlideshow(tile, img, post.media);
      if (canHover) {
        tile.insertAdjacentHTML('beforeend', '<span class="tile-cap" aria-hidden="true"><b dir="auto"></b><small></small></span>');
      }
    }
    const kind = post.media.length > 1 ? 'carousel' : first.type === 'video' ? 'video' : null;
    if (kind) tile.insertAdjacentHTML('beforeend', `<span class="badge">${ICONS[kind]}</span>`);
    labelTile(tile, post);
    return tile;
  }

  // Desktop: hovering a multi-photo post flips through its pictures every 1.5 s.
  const SLIDE_MS = 1500;
  const FADE_MS = 350; // keep in sync with .fader transition in style.css
  const stillOf = (m) => (m.type === 'video' ? m.poster : m.thumb || m.src);

  function addSlideshow(tile, img, media) {
    const dots = document.createElement('span');
    dots.className = 'tile-dots';
    dots.setAttribute('aria-hidden', 'true');
    dots.innerHTML = media.map(() => '<i></i>').join('');
    tile.appendChild(dots);
    // A second image on top fades in with the next picture, then hands it to the base image.
    const fader = document.createElement('img');
    fader.className = 'fader';
    fader.alt = '';
    img.after(fader);
    const base = img.src;
    let i = 0;
    let timer = null;
    let step = 0;
    const show = (n) => {
      i = n;
      dots.querySelectorAll('i').forEach((d, k) => d.classList.toggle('on', k === i));
      const src = n === 0 ? base : stillOf(media[i]);
      if (img.src === new URL(src, location.href).href) return;
      const mine = ++step;
      fader.classList.remove('in');
      fader.onload = () => {
        if (mine !== step) return;
        fader.classList.add('in');
        setTimeout(() => {
          if (mine !== step) return;
          img.src = src;
          fader.classList.remove('in');
        }, FADE_MS);
      };
      if (fader.src.endsWith(src) && fader.complete) fader.onload(); // same src won't fire load again
      else fader.src = src;
    };
    tile.addEventListener('mouseenter', () => {
      media.forEach((m) => { new Image().src = stillOf(m); }); // preload so each flip is instant
      tile.classList.add('sliding');
      show(0);
      timer = setInterval(() => show((i + 1) % media.length), SLIDE_MS);
    });
    tile.addEventListener('mouseleave', () => {
      clearInterval(timer);
      tile.classList.remove('sliding');
      show(0);
    });
  }

  function render() {
    stopAll();
    const def = filterDefs().find((d) => d.id === activeCat);
    visible = def ? posts.filter((p) => matches(p, def)) : posts;
    grid.innerHTML = '';
    shown = 0;
    showMore();
    $('empty').hidden = visible.length > 0;
  }
  function showMore() {
    const frag = document.createDocumentFragment();
    const end = Math.min(visible.length, shown + PAGE);
    for (let i = shown; i < end; i++) frag.appendChild(tileFor(visible[i], i));
    grid.appendChild(frag);
    const firstNew = grid.children[shown];
    shown = end;
    $('moreBtn').hidden = shown >= visible.length;
    if (!canHover && !reduceMotion) setTimeout(updateTopRow, 0);
    return firstNew;
  }
  $('moreBtn').addEventListener('click', () => {
    const firstNew = showMore();
    if (firstNew) firstNew.focus({ preventScroll: true }); // keep keyboard users in place
  });

  // ---------- video previews ----------
  function playTile(tile) {
    const v = tile.querySelector('video');
    if (!v) return;
    if (!v.src) v.src = v.dataset.src;
    const p = v.play();
    if (p) p.then(() => tile.classList.add('playing')).catch(() => {});
  }
  function stopTile(tile) {
    const v = tile.querySelector('video');
    if (!v) return;
    v.pause();
    tile.classList.remove('playing');
  }
  function stopAll() { grid.querySelectorAll('.tile.playing').forEach(stopTile); }

  // Touch devices: autoplay (muted) the videos in the topmost row that is mostly on screen.
  function updateTopRow() {
    if (!$('lightbox').hidden) return;
    const vh = window.innerHeight;
    let rowTop = null;
    for (const tile of grid.children) {
      const r = tile.getBoundingClientRect();
      if (r.bottom < 0) continue;
      if (r.top > vh) break;
      const visiblePart = Math.min(r.bottom, vh) - Math.max(r.top, 0);
      if (visiblePart >= r.height * 0.6) { rowTop = r.top; break; }
    }
    grid.querySelectorAll('.tile.has-video').forEach((tile) => {
      const inRow = rowTop !== null && Math.abs(tile.getBoundingClientRect().top - rowTop) < 2;
      if (inRow) { if (!tile.classList.contains('playing')) playTile(tile); }
      else if (tile.classList.contains('playing')) stopTile(tile);
    });
  }
  if (!canHover && !reduceMotion) {
    let ticking = false;
    const onScroll = () => {
      if (ticking) return;
      ticking = true;
      setTimeout(() => { ticking = false; updateTopRow(); }, 100); // timer, not rAF: rAF can stall in in-app browsers
    };
    addEventListener('scroll', onScroll, { passive: true });
    addEventListener('resize', onScroll);
  }

  // ---------- floating WhatsApp: hide when the footer button is on screen ----------
  new IntersectionObserver(([e]) => $('waFloat').classList.toggle('hide', e.isIntersecting))
    .observe($('waButton'));

  // ---------- slim top bar: shown once the big logo has scrolled out of view ----------
  new IntersectionObserver(([e]) => {
    const show = !e.isIntersecting && e.boundingClientRect.top < 0;
    const bar = $('miniBar');
    bar.classList.toggle('show', show);
    bar.setAttribute('aria-hidden', String(!show));
    bar.querySelectorAll('a, button').forEach((el) => { el.tabIndex = show ? 0 : -1; });
  }).observe($('logo'));

  // ---------- lightbox ----------
  const lb = $('lightbox');
  let lbPost = 0;
  let lbSlide = 0;
  let returnFocus = null;

  // everything outside the viewer is made inert while it's open
  const setBackgroundInert = (on) => document.querySelectorAll('body > :not(#lightbox):not(script)')
    .forEach((el) => { el.inert = on; });

  function openLightbox(i, from) {
    stopAll();
    lbPost = i;
    lbSlide = 0;
    returnFocus = from || document.activeElement;
    lb.hidden = false;
    document.body.classList.add('lb-open');
    setBackgroundInert(true);
    showSlide();
    $('lbClose').focus();
  }
  function closeLightbox() {
    const v = $('lbStage').querySelector('video');
    if (v) v.pause();
    $('lbStage').innerHTML = '';
    lb.hidden = true;
    document.body.classList.remove('lb-open');
    setBackgroundInert(false);
    if (returnFocus && document.contains(returnFocus)) returnFocus.focus({ preventScroll: true });
    if (!canHover && !reduceMotion) updateTopRow();
  }
  function showSlide() {
    const post = visible[lbPost];
    const m = post.media[lbSlide];
    const stage = $('lbStage');
    const names = catNames(post);
    stage.innerHTML = '';
    if (post.sample) {
      stage.innerHTML = `<div class="swatch-lg" style="background:${post.color}"></div>`;
    } else if (m.type === 'video' && !m.external) {
      const v = document.createElement('video');
      v.src = m.src;
      v.poster = m.poster || '';
      v.controls = true;
      v.playsInline = true;
      v.autoplay = !reduceMotion; // opened by a tap, so sound is allowed
      stage.appendChild(v);
    } else {
      // photos, and the covers of Reels Instagram doesn't let us download
      const img = document.createElement('img');
      img.src = m.type === 'video' ? m.poster : m.src;
      img.alt = names.join(', ') || t().work;
      stage.appendChild(img);
    }
    const date = fmtDate(post.timestamp);
    lb.setAttribute('aria-label', [names.join(', ') || t().work, date].filter(Boolean).join(' · '));
    const slides = post.media.length > 1 ? ` · ${lbSlide + 1}/${post.media.length}` : '';
    $('lbCounter').textContent = `${lbPost + 1} / ${visible.length}${slides}`;
    $('lbDate').textContent = [date, ...names].filter(Boolean).join(' · ');
    const caption = captionFor(post);
    $('lbCaption').textContent = caption;
    $('lbCaption').hidden = !caption;
    $('lbWant').href = waLink(`${t().wantMessage}\n${post.permalink || ''}`.trim());
    $('lbLink').hidden = !post.permalink;
    $('lbLink').href = post.permalink || '#';
    // dots are real buttons: tap one to jump to that photo
    $('lbDots').innerHTML = post.media.length > 1
      ? post.media.map((_, i) => `<button type="button" class="${i === lbSlide ? 'on' : ''}" data-slide="${i}" aria-label="${i + 1} / ${post.media.length}"${i === lbSlide ? ' aria-current="true"' : ''}><i></i></button>`).join('')
      : '';
    $('lbPrev').disabled = lbPost === 0 && lbSlide === 0;
    $('lbNext').disabled = lbPost === visible.length - 1 && lbSlide === post.media.length - 1;
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
  // jump straight to the next/previous work, skipping the rest of a multi-photo post
  function jumpPost(dir) {
    const np = lbPost + dir;
    if (np < 0 || np >= visible.length) return;
    lbPost = np;
    lbSlide = 0;
    showSlide();
  }
  $('lbDots').addEventListener('click', (e) => {
    const b = e.target.closest('button[data-slide]');
    if (!b) return;
    lbSlide = +b.dataset.slide;
    showSlide();
    const again = $('lbDots').querySelector(`[data-slide="${lbSlide}"]`);
    if (again) again.focus();
  });
  $('lbNext').onclick = () => step(1);
  $('lbPrev').onclick = () => step(-1);
  $('lbClose').onclick = closeLightbox;
  lb.addEventListener('click', (e) => {
    if (e.target === lb || e.target.id === 'lbStage') closeLightbox();
  });
  addEventListener('keydown', (e) => {
    if (lb.hidden) return;
    if (e.key === 'Escape') closeLightbox();
    if (e.key === 'ArrowRight') { if (e.shiftKey) jumpPost(1); else step(1); }
    if (e.key === 'ArrowLeft') { if (e.shiftKey) jumpPost(-1); else step(-1); }
    if (e.key === 'PageDown') { e.preventDefault(); jumpPost(1); }
    if (e.key === 'PageUp') { e.preventDefault(); jumpPost(-1); }
    if (e.key === 'Tab') { // keep focus inside the viewer
      const items = [...lb.querySelectorAll('button:not([disabled]), a[href]:not([hidden]), video')]
        .filter((el) => el.offsetParent !== null);
      if (!items.length) return;
      const first = items[0];
      const last = items[items.length - 1];
      if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
      else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
    }
  });
  // swipe sideways = next/previous photo, swipe up = next work, swipe down = close
  let touch = null;
  $('lbStage').addEventListener('touchstart', (e) => {
    touch = { x: e.touches[0].clientX, y: e.touches[0].clientY };
  }, { passive: true });
  $('lbStage').addEventListener('touchend', (e) => {
    if (!touch) return;
    const dx = e.changedTouches[0].clientX - touch.x;
    const dy = e.changedTouches[0].clientY - touch.y;
    touch = null;
    if (Math.max(Math.abs(dx), Math.abs(dy)) < 50) return;
    if (Math.abs(dx) > Math.abs(dy)) step(dx < 0 ? 1 : -1);
    else if (dy > 0) closeLightbox();
    else jumpPost(1);
  });

  // ---------- sparkles in the bright stripe ----------
  // Fills every empty part of the stripe with the logo's stars, keeping clear of the logo, text and buttons.
  // A fixed seed means the same pattern on every visit.
  function drawSparkles() {
    const svg = $('sparkles');
    if (!svg || getComputedStyle(svg).display === 'none') return;
    const hero = svg.parentElement.getBoundingClientRect();
    const W = Math.round(hero.width);
    const H = Math.round(hero.height);
    const PAD = 22;
    // the whole content column (from the left buttons to the right icons, full height) stays clear
    const colL = document.querySelector('.hero .controls').getBoundingClientRect().left - hero.left - PAD;
    const colR = document.querySelector('.hero .socials').getBoundingClientRect().right - hero.left + PAD;
    const placed = [];
    const GAP = 10; // minimum space between two sparkles, so none overlap
    const free = (x, y, s) => x - s > 10 && x + s < W - 10 && y - s > 8 && y + s < H - 8
      && (x + s < colL || x - s > colR)
      && placed.every(([px, py, ps]) => Math.hypot(x - px, y - py) > s + ps + GAP);
    let seed = 7;
    const rnd = () => { seed = (seed * 16807) % 2147483647; return seed / 2147483647; };
    const star = (x, y, s) => `<path d="M${x} ${y - s}Q${x} ${y} ${x + s} ${y}Q${x} ${y} ${x} ${y + s}Q${x} ${y} ${x - s} ${y}Q${x} ${y} ${x} ${y - s}Z"/>`;
    const CELL = 62; // one sparkle per cell, jittered, so they spread evenly without looking gridded
    let out = '';
    for (let gy = 0; gy < H; gy += CELL) {
      for (let gx = 0; gx < W; gx += CELL) {
        const x = Math.round(gx + rnd() * CELL);
        const y = Math.round(gy + rnd() * CELL);
        const k = rnd();
        const s = k < 0.5 ? 1.2 + rnd() * 0.7 : k < 0.88 ? 4 + rnd() * 3.5 : 9 + rnd() * 5;
        if (!free(x, y, s)) continue;
        placed.push([x, y, s]);
        out += s < 2 ? `<circle cx="${x}" cy="${y}" r="${s.toFixed(1)}"/>` : star(x, y, Math.round(s));
      }
    }
    svg.setAttribute('viewBox', `0 0 ${W} ${H}`);
    svg.innerHTML = out;
  }
  let sparkleTimer;
  const redrawSparkles = () => { clearTimeout(sparkleTimer); sparkleTimer = setTimeout(drawSparkles, 120); };
  addEventListener('resize', redrawSparkles);
  if (document.fonts) document.fonts.ready.then(drawSparkles);
  document.querySelectorAll('.lang-toggle').forEach((b) => b.addEventListener('click', redrawSparkles));

  $('year').textContent = new Date().getFullYear();
  load();
  drawSparkles();
})();
