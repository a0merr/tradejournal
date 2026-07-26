# Deploying

Both configurations build the **same** artifact: the root [`Dockerfile`](Dockerfile) compiles
the React bundle and copies it onto the Spring Boot classpath, so one container serves the
dashboard and the API from one origin. That is deliberate — the previous two-service layout
had the frontend and API on separate hostnames, which meant two environment variables had to
agree with URLs the platform assigns, and a wrong pair produced a CORS error at login rather
than a build failure. One origin removes the failure mode instead of documenting it.

The same image is built, booted against PostgreSQL, and driven over HTTP by the `image` job in
[CI](.github/workflows/ci.yml) on every commit, so "it deploys" is checked continuously rather
than discovered at deploy time.

---

## Option A — Render (free tier)

[`render.yaml`](render.yaml) provisions a managed PostgreSQL 16 and one Docker web service.

1. In [Render](https://dashboard.render.com): **New + → Blueprint**, connect the
   `a0merr/tradejournal` repo, accept the detected `render.yaml`.
2. Render creates:
   | Resource | Name | Notes |
   |---|---|---|
   | PostgreSQL | `tradejournal-db` | free plan — **deleted ~30 days after creation** |
   | Web (Docker) | `tradejournal-amerritt` | health-checked at `/actuator/health` |
3. First boot: Flyway applies `V1`–`V6` and, because `SPRING_PROFILES_ACTIVE=demo`, the
   repeatable seed loads a demo account.
4. The service URL is the whole app — dashboard at `/`, API under `/api`, OpenAPI at
   `/swagger-ui`.

**No URL wiring needed.** Nothing in `render.yaml` refers to a hostname Render assigns, so a
name collision cannot silently point the frontend somewhere else. (An earlier version did
hard-code `tradejournal-web.onrender.com`; that hostname now belongs to an unrelated account,
which is exactly the failure this layout removes.)

**Free-tier caveats, worth stating next to any demo link:**

- The instance **sleeps after inactivity**; the first request after idle takes ~30–60s while
  the JVM cold-starts.
- The free database is deleted ~30 days after creation. Upgrade the plan or re-run the
  Blueprint to keep the demo alive.

---

## Option B — Fly.io

[`fly.toml`](fly.toml) runs one machine with `auto_stop_machines = "suspend"`, so an idle demo
costs almost nothing and wakes in about a second instead of a full JVM boot — a materially
better first impression than a 60-second cold start.

```bash
fly apps create tradejournal-amerritt --org personal

# managed Postgres, smallest size
fly postgres create --name tradejournal-db --region ord \
  --initial-cluster-size 1 --vm-size shared-cpu-1x --volume-size 1
fly postgres attach tradejournal-db --app tradejournal-amerritt

# Spring wants a JDBC URL and discrete credentials, not Fly's DATABASE_URL
fly secrets set --app tradejournal-amerritt \
  SPRING_DATASOURCE_URL="jdbc:postgresql://tradejournal-db.flycast:5432/tradejournal_amerritt" \
  SPRING_DATASOURCE_USERNAME="tradejournal_amerritt" \
  SPRING_DATASOURCE_PASSWORD="<from the attach output>" \
  TRADEJOURNAL_JWT_SECRET="$(openssl rand -base64 32)"

fly deploy
fly open
```

> **Prerequisite:** Fly requires a payment method before it will create an app —
> `Error: We need your payment information to continue!` is what you get otherwise. Add a card
> at `fly.io/dashboard/<org>/billing` first. Expect roughly $5/month for the machine plus the
> 1 GB Postgres volume, less with suspend-on-idle.

---

## Verifying a deploy

Run these against the deployed host before putting the URL anywhere public. They are the same
assertions the CI smoke job makes.

```bash
HOST="https://<the-deployed-host>"

# 1. the app is up and Flyway finished
curl -fsS "$HOST/actuator/health"                      # {"status":"UP"}

# 2. the dashboard is served from the same origin
curl -fsS "$HOST/" | grep -q 'id="root"' && echo "bundle served"

# 3. the seeded demo account can sign in
curl -fsS -X POST "$HOST/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@tradejournal.dev","password":"password"}'

# 4. an unauthenticated call is still refused, as a problem document
curl -s -o /dev/null -w '%{http_code}\n' "$HOST/api/positions"   # 401
```

## Demo login

```
email:    demo@tradejournal.dev
password: password
```

The seed is loaded only by the `dev` and `demo` profiles. A deployment holding real data
should set neither.

---

## When a first deploy fails

| Symptom | Cause | Fix |
|---|---|---|
| Container exits during boot, log ends at Flyway | Database unreachable or credentials wrong | Check `SPRING_DATASOURCE_URL` is a **JDBC** URL (`jdbc:postgresql://…`), not the platform's `postgres://…` form |
| `Schema-validation: missing table` | Migrations did not run against the database the app connected to | Confirm the datasource points at the provisioned database, not a default `postgres` one |
| Health check never passes, container is killed and restarted forever | Boot is slower than the health-check grace period on a cold free instance | Raise the grace period (`fly.toml`) or the health-check timeout (Render) |
| `401` on every request right after deploy | `TRADEJOURNAL_JWT_SECRET` changed between deploys | Expected — it invalidates outstanding tokens. Sign in again. Pin the secret so redeploys do not sign users out |
| Login works, dashboard is blank | The static bundle is missing from the image | You built `backend/Dockerfile` (API only) instead of the root `Dockerfile` |
| Out-of-memory kill on a 512 MB instance | JVM default heap sizing against a small container | The image already sets `-XX:MaxRAMPercentage=75`; if it still dies, move to 1 GB |
