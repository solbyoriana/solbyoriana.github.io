# SOL by Oriana — website

Portfolio site for SOL · Nail & Beauty Artist. It shows every Instagram post (@solbyoriana) and updates itself.

**Live site:** https://solbyoriana.github.io/

## How it stays up to date
- Every **3 days** GitHub checks Instagram, downloads new posts and republishes the site.
- **Refresh now:** open https://github.com/solbyoriana/solbyoriana.github.io/actions/workflows/sync.yml → **Run workflow** → **Run workflow**. The site updates about 2–5 minutes later. (Bookmark this page on your phone; it works in the GitHub app too.)

## Hide a post
Add the post's Instagram link to `site/content/hidden.json`, for example:
```json
["https://www.instagram.com/p/ABC123xyz/"]
```
Save/commit it (on github.com you can edit the file with the pencil icon). The site updates in a couple of minutes. Or just ask Claude: "hide this post: <link>".

## Pin your best work first
Put Instagram links in `site/content/pinned.json` in the order you want them; they appear first in the gallery, and the first one is shown large. All other posts follow, newest first. Or ask Claude: "pin this post: <link>".

## Style filters
Posts are tagged automatically from the hashtags and words in your captions (`site/content/categories.json`). Only categories marked `"filter"` appear as filter buttons (currently: painted art, French, chrome & cat eye, glitter); the rest (builder gel, acrylic, dip…) are shown as details in the viewer.

## Instagram connection (one-time)
The site reads posts with an Instagram access token stored as a GitHub secret named `IG_TOKEN` (repo **Settings → Secrets and variables → Actions**). It is never visible on the site or in the code.
The token is refreshed automatically on each sync. If Instagram ever issues a brand-new token, the workflow saves it using an optional secret `SECRETS_PAT` (a GitHub fine-grained token with *Secrets: read & write* on this repo). Without it, you'll get a warning and need to paste a new token roughly every 60 days.

## Project layout
- `site/` — the website (published as-is)
  - `content/posts.json` — posts (written by the sync)
  - `content/hidden.json`, `content/categories.json` — your settings
  - `media/` — downloaded photos & videos
- `scripts/sync-instagram.mjs` — the Instagram sync
- `.github/workflows/sync.yml` — schedule + refresh button + publishing

## Later
- **Own domain:** buy it, then add it under repo Settings → Pages → Custom domain. No code changes needed.
- **TikTok sync:** needs a TikTok developer app approval; until then the TikTok button links to your profile.
