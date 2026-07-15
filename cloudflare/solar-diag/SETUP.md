# solar-diag setup

## Split of responsibility

| What | Where |
|------|--------|
| Worker source + CF deploy | **github.com/thesolarproject/solar** → `cloudflare/solar-diag/` |
| GitHub issues created by the worker | **github.com/thesolarproject/solar-diag** (keep **private**) |
| Device APK config | `SOLAR_DIAG_URL` + `SOLAR_DIAG_TOKEN` in solar `gradle.properties` / CI |

Do not put the GitHub PAT in the APK. Devices only know the Worker URL and ingest token.

---

## 1. Private issues repo (once)

1. Confirm **https://github.com/thesolarproject/solar-diag** exists and is private.
2. Fine-grained PAT with access **only** to that repo, **Issues: Read and write**.
3. Optional labels: `crash`, `error`, `diag-pull`, `reach`, `playback`, `rockbox`, `theme`, `radio`, `startup`, `deezer`, `podcasts`, `other`. Worker still works if labels are missing.

---

## 2. Deploy Worker from **solar** (not from solar-diag)

From a clone of **thesolarproject/solar**:

```bash
cd cloudflare/solar-diag
npm install
npx wrangler login
npx wrangler deploy
```

Note the URL, e.g. `https://solar-diag.<subdomain>.workers.dev`.

### Cloudflare Dashboard (Git-connected Worker)

If you attach the Worker to GitHub:

| Setting | Value |
|---------|--------|
| Repository | **thesolarproject/solar** |
| Root directory / path | **cloudflare/solar-diag** |
| Build / deploy | `npm install && npx wrangler deploy` (or CF’s Wrangler integration) |
| Production branch | `main` and/or `nightly` as you prefer |

Secrets still go on the Worker (`GITHUB_TOKEN`, `INGEST_TOKEN`), not in the repo.

---

## 3. Secrets

```bash
cd cloudflare/solar-diag
openssl rand -hex 32   # → INGEST_TOKEN / SOLAR_DIAG_TOKEN
npx wrangler secret put GITHUB_TOKEN
npx wrangler secret put INGEST_TOKEN
```

`wrangler.toml` sets `GITHUB_REPO = "thesolarproject/solar-diag"`.

---

## 4. Smoke-test

```bash
curl -sS "https://solar-diag.<subdomain>.workers.dev/health"

curl -sS -X POST "https://solar-diag.<subdomain>.workers.dev/v1/report" \
  -H "Content-Type: application/json" \
  -H "X-Solar-Diag-Token: YOUR_INGEST_TOKEN" \
  -d '{
    "type": "startup",
    "trigger": "routine",
    "device": { "model": "curl-test", "sdk": 17, "versionName": "setup" },
    "summary": "manual setup smoke",
    "files": [{ "name": "smoke.txt", "text": "hello from curl" }]
  }'
```

Expect `{ "ok": true, "issue_number": N, ... }` and a new issue on **thesolarproject/solar-diag**.

---

## 5. Solar APK / CI

In solar `gradle.properties` (local or CI secrets):

```properties
SOLAR_DIAG_URL=https://solar-diag.<subdomain>.workers.dev
SOLAR_DIAG_TOKEN=<same as INGEST_TOKEN>
```

Empty values → client idle (`not_configured`).

---

## Checklist

- [ ] Private repo `thesolarproject/solar-diag` + issues PAT  
- [ ] Worker code on `main`/`nightly` in **thesolarproject/solar** under `cloudflare/solar-diag/`  
- [ ] `wrangler deploy` (or CF Git deploy from that path)  
- [ ] Secrets `GITHUB_TOKEN` + `INGEST_TOKEN`  
- [ ] curl health + sample report → issue on solar-diag  
- [ ] Gradle `SOLAR_DIAG_*` for device builds  
