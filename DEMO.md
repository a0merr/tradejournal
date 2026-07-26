# 90-second demo video — script, shot list, and recording setup

The video exists to answer one question a reader has after the README: *does this actually
run?* So it shows the deployed system doing the things a screenshot cannot prove — an ingest
becoming a position, a retry **not** double-booking, a replayed refresh token killing a
session.

Ninety seconds is roughly 8 shots. Everything below is timed, and every command is
copy-pasteable, so a take is a single uninterrupted pass rather than an editing project.

---

## Before recording

**Capture**

| Setting | Value | Why |
|---|---|---|
| Tool | OBS Studio (free), or Win+G on Windows 11 | OBS gives a fixed capture region and no toolbar overlay |
| Resolution | 1920×1080, 30 fps | GitHub scales it down cleanly; text stays legible |
| Audio | none | Captions instead — most people watch a repo video muted |
| Cursor | visible, click highlighting on | The eye needs to know where the action is |
| Browser | one window, no bookmarks bar, no extensions | An unrelated tab or an extension badge dates the video and leaks context |
| Terminal | dark theme, font ≥ 16pt, ~100 columns | Small terminal text is the single most common failure in dev demos |

**Stage**

1. Point `DEMO_URL` at the deployed instance (or `http://localhost:8080` for a dry run):
   ```bash
   export DEMO_URL="https://<the-deployed-host>"
   ```
2. Sign in once and confirm the seeded account has data, then sign out — the video starts
   from the login screen.
3. Pre-open two windows, side by side: browser left, terminal right. Never alt-tab on camera.
4. Pre-type each command in the terminal but **do not press Enter** — typing live wastes
   seconds and invites typos. Use shell history (`↑`) between shots.
5. Have [`sample-fills.csv`](sample-fills.csv) already downloaded, and a second copy of the
   same file for the re-import shot.

**Rehearse the failure paths once.** Shots 4 and 6 are the whole point of the video; if the
retry returns 201 or the replay does not 401, stop and fix the system, not the take.

---

## Shot list

Captions are burned-in text, ~3 seconds each, bottom-left. Keep them under 8 words.

### 0:00–0:08 — It is live

**Screen:** browser at `$DEMO_URL`. Sign in as `demo@tradejournal.dev` / `password`.
The dashboard paints: performance cards, positions table, exposure chart.

**Caption:** `Deployed. Sign in, real data, one origin.`

> Do not narrate the stack here. The badges do that. This shot's job is to prove the URL works.

---

### 0:08–0:20 — Positions are derived, not stored

**Screen:** cursor over the positions table. Rest on the two price columns.

**Caption:** `avgEntryPrice = FIFO basis. avgFillPrice = execution average.`

> Hover one row long enough to read it. This is the detail that separates the project from a
> CRUD tutorial, and it needs a beat.

---

### 0:20–0:35 — A fill goes in, a position comes out

**Screen:** terminal, left half of the frame; browser stays visible on the right.

```bash
TOKEN=$(curl -s -X POST "$DEMO_URL/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@tradejournal.dev","password":"password"}' \
  | python -c 'import json,sys; print(json.load(sys.stdin)["token"])')

ACCOUNT=$(curl -s "$DEMO_URL/api/accounts" -H "Authorization: Bearer $TOKEN" \
  | python -c 'import json,sys; print(json.load(sys.stdin)[0]["id"])')

curl -s -i -X POST "$DEMO_URL/api/fills" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"accountId\":$ACCOUNT,\"symbol\":\"SOL-USD\",\"exchange\":\"COINBASE\",
       \"assetClass\":\"CRYPTO\",\"side\":\"BUY\",\"type\":\"MARKET\",
       \"quantity\":\"12\",\"price\":\"142.50\",\"fee\":\"0.35\",
       \"externalId\":\"exec-demo-001\"}" | head -1
```

Then click refresh in the browser: `SOL-USD` appears in the table.

**Caption:** `201 Created — the bot's entry point.`

---

### 0:35–0:50 — The retry that must not double-book

**Screen:** press `↑` and re-run **the identical command**. Highlight the status line.

**Caption:** `Same execution id, sent twice → 200, not a second fill.`

Refresh the browser. `SOL-USD` still shows quantity **12**, fill count **1**.

> This is the most important eight seconds in the video. Let the unchanged number sit on
> screen for a full beat before cutting.

---

### 0:50–1:05 — A broker statement, imported twice

**Screen:** browser. Use the dashboard's CSV upload with `sample-fills.csv`.
Result panel shows `imported: N, skipped: 0, failed: 0`.

Immediately upload **the same file again**: `imported: 0, skipped: N, failed: 0`.

**Caption:** `Re-importing a statement skips what is already on file.`

---

### 1:05–1:20 — A stolen session dies

**Screen:** terminal.

```bash
# capture a refresh token, then spend it
REFRESH=$(curl -s -X POST "$DEMO_URL/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@tradejournal.dev","password":"password"}' \
  | python -c 'import json,sys; print(json.load(sys.stdin)["refreshToken"])')

curl -s -o /dev/null -w 'first use  → %{http_code}\n' -X POST "$DEMO_URL/api/auth/refresh" \
  -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$REFRESH\"}"

# replay it, the way a thief would
curl -s -w '\nreplay     → %{http_code}\n' -X POST "$DEMO_URL/api/auth/refresh" \
  -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$REFRESH\"}"
```

Output reads `first use → 200`, `replay → 401` with the RFC 7807 body visible.

**Caption:** `Replaying a spent token revokes the whole family.`

---

### 1:20–1:27 — It is tested and it ships

**Screen:** quick pan across the CI run summary (the test table: 42 tests, 24 unit / 18
integration) and the `production image (build + boot + smoke)` job, green.

**Caption:** `42 tests. CI boots the deployed image every commit.`

---

### 1:27–1:30 — End card

**Screen:** static frame — repo URL, the live URL, name.

```
github.com/a0merr/tradejournal
<the-deployed-host>
Andrew Merritt
```

---

## After recording

1. Trim dead air at both ends; the video should start mid-motion.
2. Export **MP4 (H.264)** for LinkedIn and a **GIF under 10 MB** for the README — GitHub does
   not autoplay MP4 in markdown, so the GIF is what gets seen. Cap the GIF at 12 fps and
   960 px wide.
3. Commit as `docs/demo.gif`, and link the MP4 from the release.
4. Replace the [Demo video](README.md#demo-video) section with the embed:
   ```markdown
   ![90-second demo](docs/demo.gif)
   ```
5. Attach the MP4 to the GitHub release so it is downloadable at full quality.

## Checklist before publishing

- [ ] No real credentials, tokens, or personal data visible in any frame (the demo account is
      fine; your own is not)
- [ ] Every response code shown is the one the README claims
- [ ] Text is legible at 50% scale — that is how it renders inline on GitHub
- [ ] Under 95 seconds
- [ ] The GIF loops cleanly back to the login screen
