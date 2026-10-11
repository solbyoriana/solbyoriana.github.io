// Pulls every post from the SOL Instagram account into site/content/posts.json
// and downloads the photos/videos into site/media/ (Instagram's own media links expire).
//
// Usage: IG_TOKEN=... node scripts/sync-instagram.mjs
// Needs Node 20+. Uses ffmpeg (if installed) to make small grid thumbnails and
// short muted preview clips, and to shrink full videos so the site stays light.

import { existsSync, mkdirSync, readFileSync, writeFileSync, readdirSync, unlinkSync, statSync, renameSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..', 'site');
const MEDIA_DIR = join(ROOT, 'media');
const CONTENT = join(ROOT, 'content');
const API = 'https://graph.instagram.com';
const FIELDS = 'id,caption,media_type,media_url,thumbnail_url,permalink,timestamp,children{id,media_type,media_url,thumbnail_url}';

let token = process.env.IG_TOKEN;
if (!token) {
  console.log('IG_TOKEN is not set or empty — skipping Instagram sync (site keeps its current content).');
  process.exit(0);
}

const hasFfmpeg = (() => {
  try { execFileSync('ffmpeg', ['-version'], { stdio: 'ignore' }); return true; } catch { return false; }
})();
if (!hasFfmpeg) console.warn('ffmpeg not found — media will be stored at original size, without preview clips.');

const readJson = (f, fallback) => { try { return JSON.parse(readFileSync(f, 'utf8')); } catch { return fallback; } };
const shortcodeOf = (s) => (String(s).match(/\/(?:p|reel|tv)\/([^/?#]+)/) || [])[1] || String(s).trim();

async function getJson(url) {
  const res = await fetch(url);
  const body = await res.json().catch(() => ({}));
  if (!res.ok || body.error) throw new Error(`Instagram API error (${res.status}): ${body.error?.message || res.statusText}`);
  return body;
}

// Long-lived tokens last 60 days; refreshing on every run keeps them alive.
async function refreshToken() {
  try {
    const r = await getJson(`${API}/refresh_access_token?grant_type=ig_refresh_token&access_token=${token}`);
    const days = Math.round(r.expires_in / 86400);
    console.log(`Token refreshed — valid for ${days} more days.`);
    if (r.access_token && r.access_token !== token) {
      token = r.access_token;
      if (process.env.GITHUB_OUTPUT) writeFileSync(process.env.GITHUB_OUTPUT, 'token_changed=true\n', { flag: 'a' });
      writeFileSync(join(dirname(ROOT), '.ig-token-new'), token); // picked up by the workflow, never committed
    }
  } catch (e) {
    console.warn(`Token refresh skipped: ${e.message}`);
  }
}

async function fetchAllMedia() {
  const items = [];
  let url = `${API}/me/media?fields=${encodeURIComponent(FIELDS)}&limit=50&access_token=${token}`;
  while (url) {
    const page = await getJson(url);
    items.push(...(page.data || []));
    url = page.paging?.next || null;
  }
  return items;
}

async function download(url, file) {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`Download failed (${res.status}) for ${file}`);
  writeFileSync(file, Buffer.from(await res.arrayBuffer()));
}

const ff = (args) => execFileSync('ffmpeg', ['-y', '-loglevel', 'error', ...args], { stdio: 'inherit' });

// Downloads one image/video (skipping anything already saved) and returns its entry for posts.json.
async function saveMedia(m) {
  const id = m.id;
  const rel = (name) => `media/${name}`;
  const abs = (name) => join(MEDIA_DIR, name);

  if (m.media_type === 'VIDEO') {
    const full = `${id}.mp4`, poster = `${id}.jpg`, preview = `${id}_p.mp4`;
    if (!existsSync(abs(full))) {
      const tmp = abs(`${id}.orig.mp4`);
      await download(m.media_url, tmp);
      if (hasFfmpeg) {
        // 720px wide, good quality, web-optimised
        ff(['-i', tmp, '-vf', 'scale=720:-2', '-c:v', 'libx264', '-crf', '26', '-preset', 'slow',
          '-c:a', 'aac', '-b:a', '96k', '-movflags', '+faststart', abs(full)]);
        unlinkSync(tmp);
      } else {
        renameSync(tmp, abs(full));
      }
    }
    if (!existsSync(abs(poster))) {
      if (m.thumbnail_url) await download(m.thumbnail_url, abs(poster));
      else if (hasFfmpeg) ff(['-i', abs(full), '-frames:v', '1', '-vf', 'scale=720:-2', '-q:v', '4', abs(poster)]);
    }
    if (hasFfmpeg && !existsSync(abs(preview))) {
      // short, silent, small clip for the grid autoplay/hover
      ff(['-i', abs(full), '-t', '6', '-an', '-vf', 'scale=360:-2', '-c:v', 'libx264', '-crf', '30',
        '-preset', 'slow', '-movflags', '+faststart', abs(preview)]);
    }
    return {
      type: 'video',
      src: rel(full),
      poster: rel(poster),
      ...(existsSync(abs(preview)) ? { preview: rel(preview) } : {}),
    };
  }

  const full = `${id}.jpg`, thumb = `${id}_t.jpg`;
  if (!existsSync(abs(full))) await download(m.media_url, abs(full));
  if (hasFfmpeg && !existsSync(abs(thumb))) ff(['-i', abs(full), '-vf', 'scale=480:-2', '-q:v', '4', abs(thumb)]);
  return { type: 'image', src: rel(full), ...(existsSync(abs(thumb)) ? { thumb: rel(thumb) } : {}) };
}

function categoriesFor(tags, categories) {
  return categories.filter((c) => c.tags.some((t) => tags.includes(t.toLowerCase()))).map((c) => c.id);
}

async function main() {
  mkdirSync(MEDIA_DIR, { recursive: true });
  await refreshToken();

  const categories = readJson(join(CONTENT, 'categories.json'), []);
  const hidden = new Set(readJson(join(CONTENT, 'hidden.json'), []).map(shortcodeOf));

  const items = await fetchAllMedia();
  console.log(`Found ${items.length} posts on Instagram.`);

  const posts = [];
  for (const item of items) {
    const shortcode = shortcodeOf(item.permalink || '');
    if (hidden.has(item.id) || hidden.has(shortcode)) continue;
    const parts = item.media_type === 'CAROUSEL_ALBUM' ? item.children?.data || [] : [item];
    try {
      const media = [];
      for (const part of parts) media.push(await saveMedia(part));
      if (!media.length) continue;
      const caption = item.caption || '';
      const tags = [...caption.matchAll(/#([\p{L}\p{N}_]+)/gu)].map((x) => x[1].toLowerCase());
      posts.push({
        id: item.id,
        permalink: item.permalink,
        timestamp: item.timestamp,
        caption: caption.replace(/(\s*#[\p{L}\p{N}_]+)+\s*$/u, '').trim(), // drop the trailing hashtag block
        categories: categoriesFor(tags, categories),
        media,
      });
    } catch (e) {
      console.warn(`Skipping post ${item.permalink}: ${e.message}`);
    }
  }

  // Remove files for posts that were deleted on Instagram or hidden.
  const keep = new Set(posts.flatMap((p) => p.media.flatMap((m) => [m.src, m.poster, m.preview, m.thumb])).filter(Boolean)
    .map((p) => p.replace('media/', '')));
  for (const f of readdirSync(MEDIA_DIR)) if (!keep.has(f)) unlinkSync(join(MEDIA_DIR, f));

  writeFileSync(join(CONTENT, 'posts.json'), JSON.stringify({ updated: new Date().toISOString(), posts }, null, 2) + '\n');
  const mb = readdirSync(MEDIA_DIR).reduce((s, f) => s + statSync(join(MEDIA_DIR, f)).size, 0) / 1e6;
  console.log(`Saved ${posts.length} posts. Media folder: ${mb.toFixed(0)} MB.`);
  if (mb > 800) console.warn('Media is approaching GitHub Pages\' 1 GB limit — consider lowering video quality.');
}

main().catch((e) => { console.error(e.message); process.exit(1); });
