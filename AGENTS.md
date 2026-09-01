# Tawaasol Chat — Base44 Dev Environment

## What this is
A realtime chat **backend API** (Node.js + Express + Socket.io + MongoDB). The `mobile/` and `android/` dirs are Kotlin Android clients (not run here); `backend/` is an unused Ktor stub. The runnable web service is `server.js` at the repo root.

## Running
```
docker compose -f docker-compose.base44.yml up -d
```
- `app` service: `node:22` base image, repo bind-mounted at `/app`, runs `nodemon --legacy-watch server.js` (live reload on edits).
- `mongo` service: `mongo:7` with auth; app waits for it to be healthy.
- Web entry point on host port **3000** (`GET /` returns service info, `GET /health` returns status).
- Dependencies install on container start via `npm install` (node_modules in a named volume).

## Secrets
None required to boot. Firebase FCM degrades gracefully when `serviceAccountKey.json` / `GOOGLE_APPLICATION_CREDENTIALS` are absent (logs a warning). JWT secrets and Mongo URI have dev defaults in `.env.base44-defaults`. To enable push notifications, provide a Firebase service account key.

## Notes / quirks
- `.env.example` in this repo is mislabeled — it actually contains an alternate (hardened) `server.js`, not env vars. The real running code is `server.js`.
- `mongoose` `useNewUrlParser`/`useUnifiedTopology` options are deprecated no-ops (harmless warnings).
- The `uuid` package pins v10 (npm deprecation warnings are cosmetic).
- Added `GET /` and `GET /health` routes to `server.js` so the API is visible in the web preview (the original had no GET routes).
