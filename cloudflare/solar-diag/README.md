# solar-diag Cloudflare Worker

**Worker source / deploy from:** `thesolarproject/solar` → `cloudflare/solar-diag/`  
**Issue reports filed to:** private `thesolarproject/solar-diag` (GitHub Issues only)

Devices POST log bundles over HTTPS; this Worker opens issues on the private diag repo. The GitHub PAT never ships in the APK.

| Piece | Location |
|-------|----------|
| Worker code | this directory (in **solar**) |
| Cloudflare deploy | Wrangler / CF dashboard, root = `cloudflare/solar-diag` |
| Issues | **thesolarproject/solar-diag** (`GITHUB_REPO` in `wrangler.toml`) |
| Device client | `app/.../diag/SolarDiagClient.java` |

**Setup:** [SETUP.md](./SETUP.md)

## Quick deploy

```bash
cd cloudflare/solar-diag
npm install
npx wrangler login
npx wrangler secret put GITHUB_TOKEN   # PAT: issues write on solar-diag only
npx wrangler secret put INGEST_TOKEN   # shared with SOLAR_DIAG_TOKEN in gradle
npx wrangler deploy
```

## API

- `GET /health` — liveness  
- `POST /v1/report` — header `X-Solar-Diag-Token`, JSON body (see SETUP.md)
