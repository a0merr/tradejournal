# Deploying the live demo (Render)

The repo ships a [`render.yaml`](render.yaml) Blueprint that provisions the whole stack:
a managed PostgreSQL 16, the Spring Boot API (built from `backend/Dockerfile`), and the
static React frontend.

## One-time setup

1. Push this branch to `main` (or deploy from any branch).
2. In [Render](https://dashboard.render.com): **New + → Blueprint**, connect the
   `a0merr/tradejournal` repo, and accept the detected `render.yaml`.
3. Render creates three resources:
   | Resource | Name | Notes |
   |---|---|---|
   | PostgreSQL | `tradejournal-db` | free plan (expires after 30 days — recreate or upgrade) |
   | Web (Docker) | `tradejournal-api` | Spring Boot, health-checked at `/actuator/health` |
   | Static site | `tradejournal-web` | the dashboard |
4. First boot: Flyway applies migrations and the **dev seed** loads a demo account.

## Demo login

```
email:    demo@tradejournal.dev
password: password
```

## URL wiring (read this if login fails with a CORS/network error)

The frontend and API are separate origins, so two values must agree with the URLs
Render actually assigns:

- `tradejournal-web` env `VITE_API_BASE_URL` → the API's URL (e.g. `https://tradejournal-api.onrender.com`)
- `tradejournal-api` env `TRADEJOURNAL_CORS_ORIGINS` → the frontend's URL (e.g. `https://tradejournal-web.onrender.com`)

`render.yaml` pre-fills both with the default names. If Render appends a suffix because a
name was taken, edit the two env vars to the real URLs and redeploy. `VITE_API_BASE_URL` is
baked in at build time, so the static site must rebuild after changing it.

## Free-tier caveats (set expectations for reviewers)

- The free API instance **sleeps after inactivity**; the first request after idle takes
  ~30–60s to cold-start. Worth a note next to the demo link.
- The free Postgres database is deleted ~30 days after creation. Upgrade the plan, or
  re-run the Blueprint, to keep the demo alive long-term.

## Alternative: Fly.io

The same `backend/Dockerfile` deploys on Fly.io (`fly launch` + a Fly Postgres). Render is
documented here because the Blueprint is the lowest-friction path for a portfolio demo.
