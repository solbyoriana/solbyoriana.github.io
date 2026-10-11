// Generates the background artwork in site/assets/, inspired by the logo:
//   speckle.svg — tileable flecks like the speckled sun in the logo
//   drips.svg   — black band with paint drips, repeated across the top of the page
// Run: node scripts/make-background.mjs   (deterministic — same output every time)

import { writeFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const OUT = join(dirname(fileURLToPath(import.meta.url)), '..', 'site', 'assets');
let seed = 7;
const rand = () => ((seed = (seed * 16807) % 2147483647) / 2147483647);
const r2 = (n) => Math.round(n * 100) / 100;

// ---- speckles: dark flecks plus a few light ones, wrapped so the tile repeats seamlessly ----
{
  const S = 420;
  const dots = [];
  const add = (x, y, r, fill, op) => {
    for (const dx of [-S, 0, S]) for (const dy of [-S, 0, S]) {
      const X = x + dx, Y = y + dy;
      if (X > -r && X < S + r && Y > -r && Y < S + r) dots.push(`<circle cx="${r2(X)}" cy="${r2(Y)}" r="${r2(r)}" fill="${fill}" opacity="${r2(op)}"/>`);
    }
  };
  for (let i = 0; i < 150; i++) add(rand() * S, rand() * S, 0.35 + rand() ** 3 * 1.5, '#111', 0.05 + rand() * 0.11);
  for (let i = 0; i < 40; i++) add(rand() * S, rand() * S, 0.5 + rand() * 1.1, '#fff', 0.55 + rand() * 0.4);
  writeFileSync(join(OUT, 'speckle.svg'),
    `<svg xmlns="http://www.w3.org/2000/svg" width="${S}" height="${S}" viewBox="0 0 ${S} ${S}">${dots.join('')}</svg>\n`);
}

// ---- drips: solid band with rounded drips of varied width/length, plus white specks on the black ----
{
  const W = 640, H = 60, BAND = 9;
  // Band with a gently wavy lower edge (period divides W so the tile repeats seamlessly).
  const wave = (x) => BAND + 1.6 * Math.sin((x / W) * Math.PI * 2 * 5) + 1 * Math.sin((x / W) * Math.PI * 2 * 11 + 1);
  let band = `M0 0H${W}V${r2(wave(W))}`;
  for (let x = W; x >= 0; x -= 8) band += `L${x} ${r2(wave(x))}`;
  band += 'Z';
  // Drips: [centre x, stem width, length]. Long ones get a rounder, heavier bulb at the end like the logo.
  const drips = [
    [22, 5, 9], [58, 9, 34], [96, 4, 7], [128, 7, 20], [176, 12, 46], [214, 5, 11],
    [246, 8, 27], [292, 4, 8], [330, 10, 39], [372, 6, 15], [410, 13, 50], [448, 5, 9],
    [482, 8, 24], [524, 4, 7], [556, 11, 42], [598, 6, 17], [626, 4, 8],
  ];
  const fillet = 5;
  const paths = drips.map(([cx, w, len]) => {
    const top = wave(cx) - 0.5, l = cx - w / 2, r = cx + w / 2;
    const bulb = len > 18 ? w * 0.68 : w / 2;
    const end = top + len - bulb;
    // stem with soft shoulders into the band, then a round bulb at the tip
    return `M${r2(l - fillet)} ${r2(top)}Q${r2(l)} ${r2(top)} ${r2(l)} ${r2(top + fillet)}L${r2(l)} ${r2(end)}`
      + `L${r2(r)} ${r2(end)}L${r2(r)} ${r2(top + fillet)}Q${r2(r)} ${r2(top)} ${r2(r + fillet)} ${r2(top)}Z`
      + `M${r2(cx - bulb)} ${r2(end)}a${r2(bulb)} ${r2(bulb)} 0 1 0 ${r2(bulb * 2)} 0a${r2(bulb)} ${r2(bulb)} 0 1 0 ${r2(-bulb * 2)} 0Z`;
  });
  // a couple of falling droplets below the longest drips
  const drops = [[176, 55, 2.6], [410, 54, 2.2]].map(([x, y, rr]) => `<circle cx="${x}" cy="${y}" r="${rr}"/>`);
  const specks = [];
  for (let i = 0; i < 26; i++) specks.push(`<circle cx="${r2(rand() * W)}" cy="${r2(1.5 + rand() * (BAND - 3))}" r="${r2(0.3 + rand() * 0.6)}" fill="#fff" opacity="${r2(0.5 + rand() * 0.5)}"/>`);
  writeFileSync(join(OUT, 'drips.svg'),
    `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}"><g fill="#111">${(band + paths.join('')).split(/(?=M)/).map((d) => `<path d="${d}"/>`).join('')}${drops.join('')}</g>${specks.join('')}</svg>\n`);
}

console.log('Wrote speckle.svg and drips.svg');
