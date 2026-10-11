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

## Style filters
Filters (French, Chrome, Nail art…) are chosen automatically from the hashtags in your captions. The hashtag → filter list is in `site/content/categories.json`. A filter only appears when at least one post uses it.

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
