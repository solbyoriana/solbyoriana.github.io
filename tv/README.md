# Studio TV (Skyworth SWTV-24AE-FHD, Google TV / Android 14)

- `screensaver/` — the SOL nail-art screensaver app. After 5 idle minutes it shows the website's photo posts in random order
  (same hide/promo rules as `site/js/app.js`), every 10 s with a slow fade, on a darkened blur with the SOL logo as a rounded card centred in the right-hand strip
  It refreshes from https://solbyoriana.github.io every 3 days and keeps up to 2 GB on the TV's USB stick.
  GitHub builds it (`.github/workflows/tv-screensaver.yml`) whenever this folder changes; download `sol-screensaver` from the run.
- `fix-tv.ps1` — puts everything back after a Google TV update: hides the extra apps, sets the timers
  (screensaver 5 min, sleep 4 h, animations ×0.5) and re-selects the screensaver.
- `tv-state-before.txt` — the TV's settings and app list before any of this, for a full undo
  (`adb shell pm enable <package>` switches an app back on).

Signing key: repo secrets `SOL_KEYSTORE` / `SOL_KEYSTORE_PASS`, backup in `%USERPROFILE%\.sol-tv\`.
Keep the backup: without the same key, a new build can only be installed after uninstalling the old one.
