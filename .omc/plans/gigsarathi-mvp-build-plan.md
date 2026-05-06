# Gigsarathi MVP Build Plan (Revised — Post Architect Review)

> ⚠️ **SUPERSESSION NOTICE — M2/M3/M4 Parallel Scope**
> The parallel execution plan for milestones M2, M3, and M4 is authoritative for those milestones.
> Specifically: **M2 deliverable #5 (`PATCH /admin/features`) is NOT M2 scope — it belongs to M4.**
> See `.omc/plans/gigsarathi-m234-execution-plan-codex.md` (v6, consensus-approved) for authoritative
> M2/M3/M4 scope, acceptance criteria, dependency surfaces, shared infrastructure, and merge order.
> The `chore/prep-commit` branch (Steps 0a–0d) must merge to `main` before any M2/M3/M4 branch is cut.
> M1 scope in this document is unaffected.

## Architect Review Summary
**Verdict**: REQUIRES REVISION → Revisions applied below.

**5 changes incorporated:**
1. **Outbound boundary clarified** — Java sends ALL outbound (both reactive replies and proactive scheduled). TypeScript adapters are strictly inbound-only: normalize payload → call Java `/api/v1/messages` → ACK platform webhook with HTTP 200. Java uses `PlatformMessageSender` (one impl per platform) for both paths. Eliminates dual-path asymmetry between reactive and proactive sends.
2. **Idempotency moved to M1** — Webhook replay protection (MongoDB `idempotency_records` collection, unique index on `{platform, messageId}`, **7-day TTL**) is M1 scope, not M4.
3. **M1 admin scope split** — M1 keeps `POST/GET /admin/zones` + `X-Admin-Key` auth only. `GET /admin/events/summary`, `GET /admin/earnings/export`, and `PATCH /admin/features` move to the milestone that first needs them (M2 for features, M3 for events/export).
4. **Events collection: no TTL** — Remove 90-day TTL. Storage risk is lower than analytics correctness risk for MVP. Add retention policy only after a `daily_event_counts` aggregate exists.
5. **Hotness tie-breaking** — Replace alphabetical `zone ASC` with `recommendationPriority ASC` (admin-configurable integer field on `zone_heuristics`, seeded for Kolhapur). Full order: `score DESC, recommendationPriority ASC, baseDemandScore DESC, zone ASC`.

---

## RALPLAN-DR Summary

### Principles (P1–P5)
1. **Spec before code** — every deliverable is acceptance-criteria-driven; no implementation starts without a testable AC.
2. **Polyglot by contract** — TypeScript adapters and Java core communicate only via the internal `POST /api/v1/messages` contract; no shared code, no shared state.
3. **Vertical slice delivery** — each milestone delivers a runnable, end-to-end working slice before moving to the next; no horizontal layers shipped in isolation.
4. **Redis for speed, Mongo for truth** — conversation session state lives exclusively in Redis with TTL; all durable business data lives in MongoDB; no cross-store bleed.
5. **Java owns all outbound** — Java Spring Boot is the sole process that calls Meta Cloud API and Telegram Bot API for ALL message types (reactive replies AND proactive scheduled messages). TypeScript adapters receive, normalize, call Java, and ACK — nothing more.

### Decision Drivers
1. **Time to first working bot conversation** — validates the polyglot boundary contract early and enables user testing.
2. **Spec fidelity** — all PRD button ranges, opt-out behaviour, hotness formula, and eligibility thresholds must be exactly as specified (rounds 16–23 resolved all ambiguity).
3. **Incremental complexity gate** — loan, account linking, and Datadog hardening are low-dependency features safely deferred to M4 without blocking core validation.

### Viable Options

**Option A: Vertical-slice-first (CHOSEN)**
- Each milestone = a working end-to-end user journey (onboarding → daily capture → recommendations → advanced features)
- Architect revision: M1 idempotency added; admin split reduces M1 scope; outbound boundary clarified
- Why chosen: validates polyglot contract earliest; real user flows demoable from week 2

**Option B: Horizontal layers first (REJECTED)**
- Build all infra → all Java services → TypeScript adapters
- Invalidated: nothing testable end-to-end until week 6; contract bugs discovered late

**Option C: Pure TypeScript monolith first, migrate later (REJECTED)**
- Invalidated: contradicts explicit polyglot constraint; spec requires Java to own all business logic from day 1

---

## Corrected Message Flow

### Inbound (user → bot, reactive reply)
```
User taps button on WhatsApp
→ Meta Cloud API: POST /webhook → Hono (whatsapp-bot)
→ Hono normalizes: { platform, userId, messageType, payload }
→ Hono: POST /api/v1/messages → Java Spring Boot
→ Java: check idempotency (MongoDB idempotency_records)
→ Java: read Redis session:{platform}:{userId}
→ Java: execute flow step, update Redis + MongoDB
→ Java: call Meta Cloud API directly (PlatformMessageSender) — sends reply to user
→ Java: returns { status: "sent" } to Hono
→ Hono: returns HTTP 200 ACK to Meta Cloud API
```
*Same pattern for Telegram (Grammy → Java → Java calls Telegram Bot API).*

### Outbound (Java → user, proactive scheduled)
```
Spring Scheduler fires cron
→ Java queries eligible users from MongoDB
→ Java: call Meta Cloud API / Telegram Bot API directly (same PlatformMessageSender)
→ No TypeScript involvement
```

---

## Top Delivery Risks & Mitigations

### Risk 1 — Meta Cloud API template approval blocks M1 scheduler testing
**Risk**: WhatsApp proactive messages (9PM daily prompt) require pre-approved templates from Meta. Approval takes 1–7 business days. M1 must verify WhatsApp scheduled outbound on both platforms — Telegram alone is not sufficient proxy.
**Mitigation**: Submit templates to Meta on day 1 of M1. Use Meta Cloud API **test phone numbers** (sandbox mode) for M1 sign-off — test numbers receive outbound messages without approved templates, satisfying the dual-platform M1 gate. Production WA scheduler goes live only once production template is approved (expected before M2 start). Telegram requires no template and runs in parallel throughout.
**Verification**: M1 sign-off requires WhatsApp scheduled outbound delivered to a Meta test phone number AND Telegram test user. Template submission receipt from Meta Business Manager obtained by end of M1 week 1.

### Risk 2 — Polyglot contract drift between TypeScript adapters and Java core
**Risk**: If the `POST /api/v1/messages` request/response contract diverges between TypeScript callers and Java handler, inbound messages will fail silently or return 500s without surfacing to the user.
**Mitigation**: Define and commit an OpenAPI spec for `POST /api/v1/messages` before writing any adapter code. Both TypeScript adapters must validate outgoing payloads and incoming responses against the spec. Integration test in M1 fires a synthetic webhook at Hono → verifies the round-trip through Java → Mongo.
**Verification**: OpenAPI spec file committed to repo root; integration test passes on `docker-compose up`; no TypeScript code references any Meta/Telegram API URL.

### Risk 3 — ngrok URL churn breaks Telegram webhook and WA webhook registration
**Risk**: ngrok free tier generates a new URL on each restart. Every URL change invalidates the Telegram `setWebhook` registration and requires updating the Meta Cloud API webhook URL manually. This creates development friction and can silently break all inbound messages.
**Mitigation**: Use a paid ngrok account with a stable subdomain, OR use `ngrok authtoken` + static domain config in `docker-compose.yml`. Telegram init container re-calls `setWebhook` idempotently on every `docker-compose up`. Meta webhook URL update is a one-time manual step documented in `README` with exact curl command.
**Verification**: `docker-compose down && docker-compose up` succeeds without manual Telegram intervention; Telegram receives test message after restart.

---

## Scheduler Cron Expressions (UTC — IST = UTC+5:30)

| Job | Cron (UTC) | IST Local Time |
|-----|-----------|----------------|
| Daily earnings prompt | `0 30 15 * * ?` | 9:00 PM IST |
| Inactive user nudge scan | `0 0 2 * * ?` | 7:30 AM IST (daily) |
| Weekly report (Sunday) | `0 0 14 * * SUN` | 7:30 PM IST Sunday |
| Peak nudge | Configurable per zone/time config | 30 min before peak slot |

All `@Scheduled` annotations in `scheduler/` package use these exact cron strings. The JVM timezone is NOT set to IST — all expressions are pre-calculated in UTC.

---

## Milestones

### M1 — Foundation Slice (~21 SP, 8–10 days)
**Goal**: Users can discover, onboard, and submit daily earnings on both WhatsApp and Telegram. Webhook idempotency in place from day 1. Admin can configure zones.

**Deliverables:**
1. **Monorepo scaffold** — `gigsarathi-core/` (Java 21 + Maven), `whatsapp-bot/` (Hono), `telegram-bot/` (Grammy), `scripts/`, `fixtures/`
2. **Docker Compose** — MongoDB, Redis, whatsapp-bot, telegram-bot, gigsarathi-core + Telegram init container (`scripts/set-telegram-webhook`, idempotent)
3. **ngrok integration** — single tunnel exposing the whatsapp-bot + telegram-bot ports
4. **Java package structure** — `com.gigsarathi.{adapter,bot,flow,domain,scheduler,admin,config}`
   - `bot` package contains `PlatformMessageSender` interface + `WhatsAppMessageSender` + `TelegramMessageSender` implementations
5. **Webhook idempotency** — MongoDB `idempotency_records` collection; unique compound index on `{platform, messageId}`; TTL 7 days; duplicate requests return 200 without reprocessing
6. **Flow 1: Onboarding** — work type → apps → city → confirmation; User document created; events written
7. **Flow 2: Daily Earnings Capture** — 9PM scheduler → orders → earnings → fuel → zone → summary; `EarningsRecord` created; PRD button sets exact; 20h duplicate detection
8. **Opt-out handling** — STOP → `OPTED_OUT`; START → `ACTIVE`; scheduler filter; events written
9. **Admin REST API (M1 scope only)** — `POST /admin/zones`, `GET /admin/zones?city=`; `X-Admin-Key` auth interceptor; `estimatedSupply` field on zones

**M1 Acceptance Criteria:**

*Onboarding*
- [ ] First-time user receives onboarding prompt within **≤3 seconds** of messaging the bot
- [ ] Work type step presents exactly: `[Food Delivery]` `[Ride]` `[Courier]` `[Multiple]`
- [ ] App step (Food Delivery) presents exactly: `[Swiggy]` `[Zomato]` `[Zepto]` `[Uber Eats]` `[Other]` (multi-select)
- [ ] Full onboarding (work type → apps → city → confirmation) completable with buttons only; city accepts manual text fallback
- [ ] MongoDB `users` document created with all required fields: `userId`, `platform`, `phoneNumber`, `workType`, `appsUsed`, `city`, `onboardingStatus: COMPLETED`, `createdAt`, `lastActiveAt`
- [ ] `onboarding_started` and `onboarding_completed` events written to `events` collection
- [ ] Redis session key `session:{platform}:{userId}` is **cleared** (deleted) after onboarding completion
- [ ] New WhatsApp and Telegram users each complete onboarding via their respective adapter

*Outbound boundary*
- [ ] Java sends all reactive replies via `WhatsAppMessageSender` / `TelegramMessageSender`; zero TypeScript code references Meta Cloud API or Telegram Bot API URLs

*Webhook idempotency*
- [ ] Replayed webhook with same `messageId` returns HTTP 200 but does NOT create duplicate `User` or `EarningsRecord`; idempotency TTL index on `idempotency_records` is exactly 7 days

*Daily Earnings Capture*
- [ ] 9PM scheduler (cron `0 30 15 * * ?`) sends to `status=ACTIVE` users with `lastActiveAt` within 7 days on **both WhatsApp (Meta test phone number) and Telegram**; both platforms must receive the proactive prompt before M1 is signed off
- [ ] Orders step presents exactly: `[Less than 10]` `[10–15]` `[15–20]` `[20+]`
- [ ] Earnings step presents exactly: `[₹0–500]` `[₹500–1000]` `[₹1000–1500]` `[₹1500+]`
- [ ] Fuel step presents exactly: `[₹0–100]` `[₹100–200]` `[₹200+]`
- [ ] `EarningsRecord` stores PRD midpoints: ₹250, ₹750, ₹1250, ₹1750 for earnings; ₹50, ₹150, ₹250 for fuel
- [ ] Daily summary message contains: gross earnings range, fuel range, real profit (midpoint), comparison to user average (shown only if ≥3 records exist)
- [ ] Returning user completes full daily capture flow in **≤30 seconds**
- [ ] 20h duplicate window triggers "Replace today's record?" confirmation; Yes updates existing record, No cancels
- [ ] `daily_record_submitted` and `daily_summary_viewed` events written

*Opt-out*
- [ ] STOP → `status=OPTED_OUT`; next scheduler run skips that user; `opted_out` event written
- [ ] START → `status=ACTIVE`; user re-enters scheduler pool

*Admin*
- [ ] `POST /admin/zones` creates/updates zone including `estimatedSupply`; `GET /admin/zones?city=Kolhapur` returns all 9 seeded zones
- [ ] Admin endpoints return 401 for requests without valid `X-Admin-Key`

*Infrastructure*
- [ ] Telegram init container calls `setWebhook` on every `docker-compose up`; script is idempotent (safe to re-run)
- [ ] All 5 M1 event types (`onboarding_started`, `onboarding_completed`, `daily_record_submitted`, `daily_summary_viewed`, `opted_out`) written to `events` collection with **no TTL index** on that collection

---

### M1 Verification Gate Before M2

All checks below must pass before M2 work begins. Run in order.

1. **Docker Compose cold boot** — `docker-compose down -v && docker-compose up`; all services reach healthy state; Telegram init container exits 0
2. **Zone seed** — `GET /admin/zones?city=Kolhapur` returns exactly 9 zone entries; each has `recommendationPriority` set
3. **WhatsApp onboarding** — Send "Hi" from a fresh WA number; receive first prompt **within 3s**; complete work-type → apps (multi-select) → city → confirmation using only buttons; verify `users` doc in Mongo has all 9 required fields (`userId`, `platform`, `phoneNumber`, `workType`, `appsUsed`, `city`, `onboardingStatus: COMPLETED`, `createdAt`, `lastActiveAt`); verify Redis session key `session:whatsapp:{userId}` is absent post-completion; verify `onboarding_started` and `onboarding_completed` events in `events` collection
4. **Telegram onboarding** — Repeat step 3 via Telegram; verify same Mongo document shape, Redis cleanup, and both onboarding events written
5. **Daily capture + dual-platform scheduler delivery** — Wait for or manually advance the `DailyEarningsScheduler` clock (e.g. fire via Spring Actuator or advance test clock); verify prompt is **actually delivered** to both the Meta test phone number (WhatsApp) and Telegram test user — not merely triggered internally; complete orders → earnings → fuel → zone on one platform; verify `EarningsRecord` in Mongo with correct midpoints; verify `daily_record_submitted` and `daily_summary_viewed` events; time the full returning-user flow to confirm ≤30s; also confirm cron annotation in `DailyEarningsScheduler.java` is exactly `0 30 15 * * ?` (`grep "@Scheduled" gigsarathi-core/src/main/java/com/gigsarathi/scheduler/DailyEarningsScheduler.java`)
6. **Opt-out / opt-in cycle** — Send `STOP` from an active user; verify `status=OPTED_OUT` in Mongo and `opted_out` event written; trigger scheduler again and confirm that user receives no prompt; send `START` from same user; verify `status=ACTIVE` and user receives the next scheduled prompt
7. **Event completeness** — Query `db.events.find({userId: <test_user>})` and confirm all 5 required M1 event types are present: `onboarding_started`, `onboarding_completed`, `daily_record_submitted`, `daily_summary_viewed`, `opted_out` (the `opted_out` event is written in step 6; this step confirms the full set exists in the collection before M2)
8. **Duplicate webhook replay** — Replay the same webhook payload (identical `messageId`) twice; verify second call returns 200 and Mongo shows exactly 1 `EarningsRecord`; verify `idempotency_records` TTL index is 7 days (`expireAfterSeconds: 604800`)
9. **Java-only outbound** — Run two checks: (a) `grep -rn "graph.facebook.com\|api.telegram.org\|sendMessage\|sendText\|bot\.api\." whatsapp-bot/src/ telegram-bot/src/` returns **zero results** — catches both URL-based and Grammy/SDK send calls; (b) confirm reactive replies arrive at the test user's WhatsApp/Telegram after sending an inbound message, proving `PlatformMessageSender` in `gigsarathi-core/` is the active outbound path
10. **Admin auth** — `curl -X POST /admin/zones` without header returns 401; with valid `X-Admin-Key` returns 200
11. **MongoDB indexes** — `db.idempotency_records.getIndexes()` shows unique compound index on `{platform, messageId}` and TTL index with `expireAfterSeconds: 604800`; `db.events.getIndexes()` shows **no TTL index**

---

### M2 — Intelligence Slice (~14 SP, 6–8 days)
**Goal**: System recommends tomorrow's best zone+time and sends peak nudges.

**Deliverables:**
1. **Hotness score engine** — `(AvgEarnings × Demand) / (Supply + 1)` with hybrid supply: `zone.estimatedSupply ?? historicalCount(zone, timeSlot, last7d) ?? 0`
2. **Tie-breaking order** — `score DESC, recommendationPriority ASC, baseDemandScore DESC, zone ASC`; `recommendationPriority` is admin-settable integer on `zone_heuristics`, seeded in Kolhapur fixtures
3. **Flow 3: Tomorrow Plan** — triggered by "Yes" after daily summary; highest hotness zone returned as range, ≤4 lines + tip; `tomorrow_plan_requested` event
4. **Flow 4: Peak Nudge** — Spring Scheduler sends 30min before highest-scored slot; opted-in users only; `peak_nudge_sent` event
5. **Admin additions (M2)** — `PATCH /admin/features` (enable/disable loan, referral flags)

**M2 Acceptance Criteria:**
- [ ] Hotness score uses PRD formula; falls back to `baseDemandScore` heuristic when <3 records exist for zone+timeSlot
- [ ] Supply resolution: admin `estimatedSupply` overrides historical count; historical count falls back to 0
- [ ] Tie-breaking is deterministic and uses `recommendationPriority` as primary tiebreaker after score
- [ ] Peak nudge NOT sent to users inactive >7 days or `status=OPTED_OUT`
- [ ] `PATCH /admin/features` toggles `loanEnabled`, `referralRewardEnabled` in `app_config`

---

### M3 — Engagement Slice (~14 SP, 6–8 days)
**Goal**: Inactive users re-engaged; weekly reports generated; referral infrastructure live.

**Deliverables:**
1. **Flow 5: Inactive Nudge** — daily 7:30AM IST; no-record-in-2-days users; references last profit; "Track Now" button
2. **Flow 7: Weekly Report** — Sunday 7:30PM IST; ≥2 records get report (total, fuel, profit, best day, insight); <2 records get reminder
3. **Flow 8: Referral** — after ≥3 tracking days OR weekly report interaction; unique code at onboarding; click tracking; `rewardAmount: null`
4. **Admin additions (M3)** — `GET /admin/events/summary?from=&to=`, `GET /admin/earnings/export?from=&to=` CSV

**M3 Acceptance Criteria:**
- [ ] Inactive nudge skips `OPTED_OUT` users and users active within 2 days
- [ ] Weekly report: all 5 data points included; users with <2 records receive reminder only
- [ ] `inactive_nudge_sent`, `weekly_report_sent`, `referral_prompt_sent`, `referral_clicked` events written
- [ ] Referral code unique per user; generated at onboarding; `rewardAmount: null`; no reward messaging shown
- [ ] Admin `GET /admin/events/summary` returns correct funnel counts from full `events` collection (no TTL truncation)

---

### M4 — Advanced Slice (~14 SP, 6–8 days)
**Goal**: Loan eligibility, account linking, Datadog, load hardening.

**Deliverables:**
1. **Flow 6: Loan Entry Point** — eligibility: ≥5 records AND ≥3 in last 7d AND avg gross ≥₹700; Redis `loan-offered:{userId}` (30d TTL); feature-flag gated
2. **Account linking** — 6-digit code; Redis `link-token:{code}` (10min TTL); earnings merge to primary; secondary `MERGED`
3. **Datadog full wiring** — JMX + APM on Java; log forwarding; scheduler job health dashboard
4. **Hardening** — load test M1 flows; review idempotency record accumulation; tune MongoDB indexes

**M4 Acceptance Criteria:**
- [ ] Loan prompt: only to eligible users; `loan-offered:{userId}` Redis key prevents re-send within 30d
- [ ] `loan_offer_shown`, `loan_offer_clicked` events written; no approval-implied language
- [ ] 6-digit link code expires 10min (Redis TTL verified); after linking: secondary `status=MERGED`; earnings re-pointed to primary
- [ ] Datadog dashboard shows scheduler success/failure counts

---

## Monorepo Structure

```
gigsarathi/
├── gigsarathi-core/              # Java 21 + Spring Boot 3.x + Maven
│   ├── src/main/java/com/gigsarathi/
│   │   ├── adapter/              # Inbound DTO mapping → InboundMessage (platform-agnostic)
│   │   ├── bot/                  # PlatformMessageSender interface
│   │   │   ├── PlatformMessageSender.java
│   │   │   ├── WhatsAppMessageSender.java  # WebClient → Meta Cloud API
│   │   │   └── TelegramMessageSender.java  # WebClient → Telegram Bot API
│   │   ├── flow/                 # FlowType enum, StepResolver, flow state machine
│   │   ├── domain/
│   │   │   ├── user/             # User entity, UserRepository
│   │   │   ├── earnings/         # EarningsRecord, profit calculations, midpoint constants
│   │   │   ├── zone/             # ZoneHeuristic (incl. recommendationPriority, estimatedSupply)
│   │   │   │                     # HotnessScoreService
│   │   │   ├── referral/         # ReferralCode entity
│   │   │   └── event/            # Event entity, EventRepository, EventService
│   │   ├── scheduler/            # @Scheduled: daily(21:00 IST), weekly(SUN 19:30 IST),
│   │   │                         # inactive(07:30 IST), peak(configurable)
│   │   ├── admin/                # Admin REST controllers (M1: zones; M2: features; M3: events/export)
│   │   └── config/               # Redis, MongoDB, feature flags, API key interceptor, WebClient
│   └── src/test/java/com/gigsarathi/
├── whatsapp-bot/                 # TypeScript + Hono (INBOUND ONLY)
│   ├── src/
│   │   ├── webhook.ts            # POST /webhook — parse Meta Cloud API; forward to Java; ACK
│   │   └── client.ts             # POST /api/v1/messages caller
│   └── package.json
├── telegram-bot/                 # TypeScript + Grammy (INBOUND ONLY)
│   ├── src/
│   │   ├── bot.ts                # Grammy webhook handler; forward to Java; ACK
│   │   └── client.ts             # POST /api/v1/messages caller
│   └── package.json
├── scripts/
│   └── set-telegram-webhook.sh   # Idempotent; called by Compose init container on startup
├── fixtures/
│   └── kolhapur-zones.json       # 9 zone/timeslot entries with recommendationPriority seeded
└── docker-compose.yml            # includes telegram-webhook-init service
```

---

## MongoDB Collections Summary

| Collection | Notes |
|-----------|-------|
| `users` | Platform identity, status, referral code, linkedUserId |
| `earnings_records` | PRD midpoints, zone, submittedAt; index on `{userId, submittedAt}` |
| `zone_heuristics` | Includes `estimatedSupply`, `recommendationPriority`, `baseDemandScore` |
| `events` | All 14 event types; **no TTL index** for MVP |
| `referrals` | Code, referrer, referred, rewardAmount=null |
| `app_config` | Feature flags (loanEnabled, referralRewardEnabled) |
| `idempotency_records` | Unique `{platform, messageId}`; TTL index 7 days |

---

## Redis Key Schema

| Key | Value | TTL |
|-----|-------|-----|
| `session:{platform}:{userId}` | `{flowType, stepIndex, pendingData, startedAt}` | 24h |
| `link-token:{6-digit-code}` | `{primaryUserId, primaryPlatform}` | 10 min |
| `loan-offered:{userId}` | `true` | 30 days |

---

## Story Point Totals

| Milestone | SP | Duration |
|-----------|-----|----------|
| M1 | ~21 | 8–10 days |
| M2 | ~14 | 6–8 days |
| M3 | ~14 | 6–8 days |
| M4 | ~14 | 6–8 days |
| **Total** | **~63** | **~26–34 days** |
