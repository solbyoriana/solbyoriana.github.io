# Studio TV (Skyworth SWTV-24AE-FHD, Google TV / Android 14)

- `screensaver/` — the SOL nail-art screensaver app. After 5 idle minutes it shows the website's photo posts in random order
  (same hide/promo rules as `site/js/app.js`), every 10 s with a slow fade, on a darkened blur with the SOL logo as a round badge centred in the right-hand strip.
  It refreshes from https://solbyoriana.github.io every 3 days and keeps up to 2 GB on the TV's USB stick.
  GitHub builds it (`.github/workflows/tv-screensaver.yml`) whenever this folder changes; download `sol-screensaver` from the run.
- Halloween (October 1 – November 1, `HalloweenLayer.java`): pumpkin garlands in both top corners, a "Happy Halloween"
  sign planted in grass bottom-left, and bats. Flying: 3–7 round white-eyed bats; resting (hanging/sleeping, one per
  perch, 4 perches per garland): 2–5 classic, gothic or glowing-eyes bats; at most 8 in all (so in practice ≤ 6 flying).
  Every 10–20 min a round picks new counts, never the same as either of the two before. The two sides are never even
  (2+2, 3+3, 4+4): a flying bat switches sides Pac-Man style through the outer screen edges; crossovers over the photo
  are rare. `bat-preview.html` is the earlier (0–3 bats) version of these rules as a browser player.
  Art from Pixabay (free for commercial use): garland 9903377, sign 9199895, grass 9197546.
- `fix-tv.ps1` — puts everything back after a Google TV update: hides the extra apps, sets the timers
  (screensaver 5 min, screen never turns off, animations ×0.5) and re-selects the screensaver.
- `tv-state-before.txt` — the TV's settings and app list before any of this, for a full undo
  (`adb shell pm enable <package>` switches an app back on).

Signing key: repo secrets `SOL_KEYSTORE` / `SOL_KEYSTORE_PASS`, backup in `%USERPROFILE%\.sol-tv\`.
Keep the backup: without the same key, a new build can only be installed after uninstalling the old one.
