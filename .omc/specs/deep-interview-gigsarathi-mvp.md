# Deep Interview Spec: Gigsarathi WhatsApp + Telegram MVP

## Metadata
- Interview ID: gigsarathi-mvp-2026-05-04
- Rounds: 23
- Final Ambiguity Score: 1.0%
- Type: greenfield
- Generated: 2026-05-05
- Threshold: 5% (user target: 95% clarity)
- Initial Context Summarized: No (PRD read in full)
- Status: PASSED (99% clarity)

---

## Clarity Breakdown
| Dimension | Score | Weight | Weighted |
|-----------|-------|--------|----------|
| Goal Clarity | 0.99 | 40% | 0.396 |
| Constraint Clarity | 0.99 | 30% | 0.297 |
| Success Criteria | 0.99 | 30% | 0.297 |
| **Total Clarity** | | | **0.990** |
| **Ambiguity** | | | **1.0%** |

---

## Goal

Build a dual-platform (WhatsApp + Telegram) earnings companion bot for gig workers in Kolhapur, Maharashtra. Workers interact via quick-reply buttons to log daily earnings in under 30 seconds, receive an immediate real-profit summary (gross minus fuel), get heuristic zone+time recommendations for the next day, and build an earnings history that unlocks loan eligibility checks. The system is a polyglot microservices design: TypeScript bot adapters handle inbound messages and route to a Java Spring Boot core that owns all business logic, state, and outbound messaging.

---

## Architecture

### Services (Docker Compose local)

```
┌─────────────────────────────────┐   ┌────────────────────────────────────────┐
│  whatsapp-bot (TypeScript/Hono) │   │  Java Spring Boot (gigsarathi-core)    │
│  POST /webhook (Meta Cloud API) │──>│  • All flow state machine logic        │
│  Inbound only                   │   │  • MongoDB: users, earnings, zones,    │
└─────────────────────────────────┘   │    events, referrals                   │
                                      │  • Redis: conversation session state   │
┌─────────────────────────────────┐   │  • Spring Scheduler: 9PM, peak, Sun    │
│  telegram-bot (TypeScript/Grammy│──>│  • Outbound: calls Meta Cloud API +    │
│  Webhook mode via ngrok tunnel  │   │    Telegram Bot API directly           │
│  Inbound only                   │   └────────────────────────────────────────┘
└─────────────────────────────────┘
         │                                        │
         └──────────────── ngrok ─────────────────┘
                    (external, single port exposed)
```

### Message Flow

**Inbound (user → bot):**
1. User taps button on WhatsApp → Meta Cloud API sends POST to Hono webhook
2. Hono parses payload → extracts `{platform: "whatsapp", userId: "+91...", messageType, payload}`
3. Hono calls `POST /api/v1/messages` on Java Spring Boot
4. Java reads Redis: `session:whatsapp:{userId}` → `{flowType, stepIndex, pendingData}`
5. Java processes business logic, updates Redis + MongoDB as needed
6. Java returns `{messageType: "interactive_buttons", text: "...", buttons: [...]}`
7. Hono translates response to Meta Cloud API format and sends

**Same flow for Telegram (Grammy → Java → Grammy sends via Telegram Bot API)**

**Outbound (bot → user, proactive):**
- Java Spring Scheduler fires → calls Meta Cloud API / Telegram Bot API directly
- No TypeScript involvement for proactive messages

### Infrastructure Stack
| Component | Technology |
|-----------|-----------|
| Bot adapter (WhatsApp) | TypeScript + Hono + @hono/node-server |
| Bot adapter (Telegram) | TypeScript + Grammy |
| Core API | Java 21 + Spring Boot 3.x + Maven |
| Database | MongoDB (Spring Data MongoDB) |
| Session cache | Redis OSS (Spring Data Redis) |
| Scheduler | Spring `@Scheduled` (cron expressions, IST = UTC+5:30) |
| Observability | Datadog (agent in Docker Compose, JMX + APM) |
| Containerization | Docker + Docker Compose |
| Local WA/TG tunnel | ngrok (external process, exposes single port) |
| WhatsApp API | Meta Cloud API (webhook-based) |
| Telegram API | Grammy (webhook mode with ngrok) |

---

## Constraints

1. **Dual-platform**: WhatsApp and Telegram both supported from day 1 via platform-agnostic adapter pattern.
2. **Polyglot services**: TypeScript bot adapters are inbound-only transport. Java Spring Boot owns all business logic, Redis session state, and outbound message sending.
3. **MongoDB** for persistence (not Google Sheets, not SQL). Spring Data MongoDB for repositories.
4. **Redis OSS** for conversation session state with TTL. Session key: `session:{platform}:{userId}`.
5. **Docker Compose** for local development. ngrok runs externally and forwards to a single exposed port.
6. **Meta Cloud API** (direct, no managed BSP layer) for WhatsApp. Java sends outbound directly via Spring WebClient.
7. **Grammy webhook mode** for Telegram. Java sends outbound directly via Telegram Bot API (Spring WebClient).
8. **Admin interface**: REST API + Postman/curl for MVP. No web UI. Secured by API key header.
9. **Datadog** for infrastructure/APM observability. Business event analytics stored in MongoDB `events` collection.
10. **Launch city**: Kolhapur, Maharashtra (tier-2 city, simpler zone set, validates core product before scaling).
11. **No production hosting decision yet** — Docker Compose local is the only defined runtime for now.
12. **No loan partner decided** — loan eligibility detection and "Check Offer" click tracking are built; actual loan flow is a placeholder.
13. **IST timezone (UTC+5:30)** for all Spring Scheduler cron expressions.
14. **Soft opt-out**: User sends "STOP" (case-insensitive) → `user.status = OPTED_OUT`. Scheduler skips opted-out users. Bot still processes user-initiated inbound. "START" or any greeting re-enables (`status = ACTIVE`).
15. **Duplicate detection**: Rolling 20-hour window, not calendar date. No compound unique index on (userId, date). Query: `lastRecord.submittedAt > (Instant.now() - 20h)`.
16. **Hybrid supply for hotness score**: `effectiveSupply = zone.estimatedSupply ?? historicalCount(zone, timeSlot, last7d) ?? 0`. `ZoneHeuristic` document carries optional `estimatedSupply` (admin-set override). Historical count is derived from `earnings_records` submissions.

### Non-Goals
1. No web or mobile app for workers (WhatsApp + Telegram only).
2. No admin web dashboard for MVP (REST endpoints only).
3. No third-party product analytics tool (Mixpanel, PostHog, Amplitude) — MongoDB events collection only.
4. No Google Sheets integration.
5. No automated real-time location tracking.
6. No ML-based demand prediction.
7. No actual loan disbursement — only lead capture.
8. No cash referral reward for MVP — referral infrastructure built, `rewardAmount` is null.
9. No multi-city operations beyond Kolhapur in MVP.
10. No language personalization beyond Hindi-English tone in message copy.

---

## User Identity & Cross-Platform Accounts

- **Default**: Users are platform-isolated. A WhatsApp user and Telegram user are separate User documents even if the same person.
- **Optional linking**: Via 6-digit code. User requests a link code from either platform; enters it in the other. Earnings history merges into the older account (primary).
- **Linking token**: Stored in Redis with 10-minute TTL. Key: `link-token:{code}` → `{primaryUserId, platform}`.
- **Post-merge**: Secondary User document marked as `status: MERGED`, `mergedInto: primaryUserId`. Earnings records re-pointed to primary.

---

## Launch Configuration: Kolhapur

### Initial Zone Seed Data (MongoDB `zone_heuristics` collection)

| Zone | Time Slot | Base Demand Score | Notes |
|------|-----------|------------------|-------|
| Mangalwar Peth | Lunch | 8 | Dense market, restaurant hub |
| Mangalwar Peth | Dinner | 9 | Peak zone |
| Station Area | Breakfast | 7 | Morning commuter traffic |
| Station Area | Lunch | 7 | Consistent demand |
| Rankala Lake | Dinner | 7 | Evening crowd, restaurants |
| Shahupuri | Lunch | 6 | Business district |
| Shahupuri | Dinner | 6 | Moderate residential |
| Tarabai Park | Evening | 5 | Casual demand |
| Other | Any | 4 | Fallback |

### Scheduler Cron Expressions (IST = UTC+5:30)
| Job | Cron (UTC) | Local Time |
|-----|-----------|-----------|
| Daily earnings prompt | `0 30 15 * * ?` | 9:00 PM IST |
| Inactive user nudge | `0 0 2 * * ?` | 7:30 AM IST (daily scan) |
| Weekly report (Sunday) | `0 0 14 * * SUN` | 7:30 PM IST Sunday |
| Peak nudge (30 min before peak) | Configurable per zone/time config | Varies |

---

## Acceptance Criteria

### Onboarding (Flow 1)
- [ ] User messaging the bot for the first time receives the onboarding prompt within 3 seconds
- [ ] Work type step presents: `[Food Delivery]` `[Ride]` `[Courier]` `[Multiple]` — food delivery is the primary MVP persona; bot UX is optimised for food delivery workers
- [ ] App step (for Food Delivery) presents: `[Swiggy]` `[Zomato]` `[Zepto]` `[Uber Eats]` `[Other]` (multi-select)
- [ ] User can complete full onboarding (work type → apps → city → confirmation) using only buttons; city allows manual text fallback
- [ ] User profile document created in MongoDB with: `userId`, `platform`, `phoneNumber`, `workType`, `appsUsed`, `city`, `onboardingStatus: COMPLETED`, `createdAt`, `lastActiveAt`
- [ ] `onboarding_started` and `onboarding_completed` events written to `events` collection
- [ ] Redis session state cleared after onboarding completion

### Daily Earnings Capture (Flow 2)
- [ ] Spring Scheduler sends daily prompt to all users with `lastActiveAt` within 7 days and `status != OPTED_OUT`, between 9:00–11:00 PM IST
- [ ] User completes full flow (orders → earnings → fuel → zone) using only buttons, with these exact PRD-defined button sets:
  - **Orders**: `[Less than 10]` `[10–15]` `[15–20]` `[20+]`
  - **Earnings**: `[₹0–500]` `[₹500–1000]` `[₹1000–1500]` `[₹1500+]` (midpoints: ₹250, ₹750, ₹1250, ₹1750)
  - **Fuel**: `[₹0–100]` `[₹100–200]` `[₹200+]` (midpoints: ₹50, ₹150, ₹250)
  - **Zone**: configured Kolhapur zones + `[Other]`
- [ ] `EarningsRecord` document created in MongoDB with all fields including `estimatedEarningsMidpoint`, `estimatedFuelMidpoint`, `estimatedProfit`
- [ ] Duplicate detection uses 20-hour rolling window: if `lastRecord.submittedAt > (now - 20h)`, bot asks "Replace today's record?" — Yes updates existing, No cancels
- [ ] Daily summary message shows: gross earnings range, fuel range, **real profit (midpoint)**, comparison to user average (if ≥3 records exist)
- [ ] `daily_record_submitted` and `daily_summary_viewed` events written
- [ ] Full flow completable in under 30 seconds for a returning user

### Tomorrow Plan (Flow 3)
- [ ] Triggered only when user taps "Yes" after daily summary
- [ ] System returns a projected earning range (not a single number)
- [ ] Recommendation uses the highest hotness score zone for Kolhapur at the planned time
- [ ] Hotness score formula: `(AvgEarnings × Demand) / (Supply + 1)` — falls back to base heuristic if insufficient data
- [ ] Recommendation message ≤ 4 short lines + 1 tip
- [ ] `tomorrow_plan_requested` event written

### Peak Nudge (Flow 4)
- [ ] Sends 30 minutes before the highest-scored time slot for users who opted into tomorrow plan
- [ ] Users inactive >configured threshold do NOT receive nudge
- [ ] Includes estimated extra earning potential
- [ ] Single acknowledgement button
- [ ] `peak_nudge_sent` event written

### Inactive User Nudge (Flow 5)
- [ ] Spring Scheduler identifies users with no `EarningsRecord` in last 2 days
- [ ] Message references user's last tracked profit when available
- [ ] Single "Track Now" button resumes daily earnings flow
- [ ] `inactive_nudge_sent` event written

### Loan Entry Point (Flow 6)
- [ ] Eligibility check runs after each `daily_record_submitted` event
- [ ] Eligible when: ≥5 submitted records AND ≥3 records in last 7 days AND avg gross earnings ≥ INR 700/day
- [ ] Loan prompt sent only once per eligibility period (feature-flag controlled)
- [ ] "Check Offer" and "Apply Now" clicks tracked as `loan_offer_clicked` events
- [ ] Loan messaging does NOT imply approval
- [ ] `loan_offer_shown` event written

### Weekly Report (Flow 7)
- [ ] Generated every Sunday at ~7:30 PM IST for users with ≥2 records in the week
- [ ] Report includes: total earnings, total fuel, real profit, best day, one improvement insight
- [ ] `weekly_report_sent` event written
- [ ] Users with <2 records receive a daily tracking reminder instead

### Referral (Flow 8)
- [ ] Triggered after positive engagement signals (≥3 tracking days OR weekly report interaction)
- [ ] Each user has a unique referral code (generated at onboarding)
- [ ] Referral link tracked: `referral_prompt_sent` and `referral_clicked` events
- [ ] `rewardAmount: null` for MVP — no cash reward messaging
- [ ] Feature flag `referralRewardEnabled: false` in MongoDB `app_config` collection

### Account Linking
- [ ] User can request a 6-digit link code on either platform
- [ ] Code expires after 10 minutes (Redis TTL)
- [ ] After successful linking: earnings records from secondary account merged into primary
- [ ] Secondary user doc marked `status: MERGED, mergedInto: primaryUserId`

### Admin REST API
- [ ] `POST /admin/zones` — create/update zone heuristic config
- [ ] `GET /admin/zones?city=Kolhapur` — list active zone configs
- [ ] `PATCH /admin/features` — enable/disable loan, referral features
- [ ] `GET /admin/events/summary?from=&to=` — funnel event counts
- [ ] `GET /admin/earnings/export?from=&to=` — CSV export of earning records
- [ ] All endpoints secured with `X-Admin-Key` header (configured via environment variable)

---

## Assumptions Exposed & Resolved

| Assumption | Challenge | Resolution |
|------------|-----------|------------|
| Google Sheets for DB | Contrarian: Sheets won't scale or secure WA numbers | MongoDB from day 1 |
| WhatsApp only | Contrarian: Is WA a hard constraint? | Both WA + Telegram required from MVP |
| Java for everything | Simplifier: Do you need two runtimes? | Polyglot confirmed: TypeScript for inbound, Java for all logic |
| Mumbai as launch city | None given | User chose Kolhapur (smarter tier-2 MVP) |
| Same user on WA + TG is one record | None given | Platform-isolated by default, optional 6-digit linking |
| TypeScript handles outbound push | None given | Java calls Meta API + TG API directly; TypeScript is inbound-only |
| Referral has a cash reward | Simplifier: simplest version? | Reward disabled for MVP, architecture supports it |
| Grammy uses long polling | None given | Webhook mode with external ngrok tunnel |

---

## Technical Context

### MongoDB Collections
| Collection | Purpose |
|-----------|---------|
| `users` | User profiles, platform identifiers, onboarding status, referral code |
| `earnings_records` | One document per user per day: ranges, midpoints, profit, zone |
| `zone_heuristics` | Admin-configurable zone/time demand scores for Kolhapur (and future cities) |
| `events` | All 14 event types from PRD Section 12.4 |
| `referrals` | Referral code → referrer userId, referred user, timestamp, reward status |
| `app_config` | Feature flags: `loanEnabled`, `referralRewardEnabled`, `referralRewardAmount` |

### EarningsRecord Button Ranges (PRD Defaults)
| Step | Button Labels | Midpoints |
|------|--------------|-----------|
| Orders | `Less than 10`, `10–15`, `15–20`, `20+` | — (ordinal, not averaged) |
| Gross Earnings | `₹0–500`, `₹500–1000`, `₹1000–1500`, `₹1500+` | ₹250, ₹750, ₹1250, ₹1750 |
| Fuel Cost | `₹0–100`, `₹100–200`, `₹200+` | ₹50, ₹150, ₹250 |
| Zone | Configured Kolhapur zones + `Other` | — |

### Opt-out Handling
| Trigger | Action | State |
|---------|--------|-------|
| User sends `STOP` / `stop` / `unsubscribe` | Bot replies confirmation; sets `user.status = OPTED_OUT` | Scheduler skips; inbound still processed |
| User sends `START` / `start` / `Hi` / any greeting | Sets `user.status = ACTIVE`; resumes normal flows | Scheduler re-includes |

Opt-out event: `opted_out` written to `events` collection. Opt-in event: `opted_in` written.

### Hybrid Supply Formula
```
effectiveSupply = zone.estimatedSupply          // admin-set override (optional)
               ?? historicalCount(zone,          // count of earnings_records
                                  timeSlot,      //   for this zone+timeSlot
                                  last 7 days)   //   submitted in last 7 days
               ?? 0                              // no data → best case
```
`ZoneHeuristic` document includes optional `estimatedSupply: Integer` field. When set by admin via `PATCH /admin/zones`, it overrides the historical count. Once sufficient historical data exists, admin can clear the override to let data drive supply.

### Redis Key Schema
| Key Pattern | Value | TTL |
|------------|-------|-----|
| `session:{platform}:{userId}` | `{flowType, stepIndex, pendingData, startedAt}` | 24h |
| `link-token:{6-digit-code}` | `{primaryUserId, primaryPlatform}` | 10 min |
| `loan-offered:{userId}` | `true` | 30 days (prevents re-sending) |

### Spring Boot Modules (suggested package structure)
```
com.gigsarathi
├── adapter/          # Inbound message DTO mapping (WA → common format)
├── bot/              # Outbound message sending (Spring WebClient → Meta API + TG API)
├── flow/             # Flow state machine (FlowType enum, StepResolver)
├── domain/
│   ├── user/         # User entity, UserRepository (MongoDB)
│   ├── earnings/     # EarningsRecord entity, profit calculations
│   ├── zone/         # ZoneHeuristic entity, hotness score service
│   ├── referral/     # ReferralCode entity, referral tracking
│   └── event/        # Event entity, EventRepository, EventService
├── scheduler/        # @Scheduled jobs (daily prompt, weekly report, inactive nudge)
├── admin/            # Admin REST controllers (secured by API key interceptor)
└── config/           # Redis, MongoDB, Datadog, feature flag configuration
```

---

## Ontology (Key Entities)

| Entity | Type | Key Fields | Relationships |
|--------|------|-----------|---------------|
| User | Core domain | userId, platform, phoneNumber, workType, appsUsed, city, referralCode, linkedUserId, status | has many EarningsRecords, has one ReferralLink |
| EarningsRecord | Core domain | recordId, userId, date, ordersRange, earningsRange, earningsMidpoint, fuelRange, fuelMidpoint, estimatedProfit, zone, submittedAt | belongs to User |
| ConversationSession | Supporting (Redis) | sessionKey (platform:userId), flowType, stepIndex, pendingData, startedAt | keyed by User platform identity |
| ZoneHeuristic | Supporting | city, zone, timeSlot, baseDemandScore, manualBoost, active | used by HotnessScoreService |
| HotnessScore | Computed | zone, timeSlot, avgEarnings, demand, supply, score | derived from EarningsRecords + ZoneHeuristics |
| ReferralLink | Supporting | referralCode, referrerId, referredUserId, clickedAt, convertedAt, rewardAmount | belongs to User |
| AccountLinkToken | Supporting (Redis) | 6-digit code, primaryUserId, platform, expiresAt | ephemeral, TTL 10 min |
| Event | Supporting | eventType, userId, platform, timestamp, properties | references User |
| AppConfig | Supporting | key (loanEnabled, referralRewardEnabled, etc.), value | global singleton per environment |
| WhatsAppAdapter | External system | Hono server, Meta Cloud API client | sends/receives for User on WA platform |
| TelegramAdapter | External system | Grammy bot, Telegram Bot API client | sends/receives for User on TG platform |

---

## Ontology Convergence

| Round | Entity Count | New | Changed | Stable | Stability Ratio |
|-------|-------------|-----|---------|--------|----------------|
| 1 | 6 | 6 | - | - | N/A |
| 2 | 6 | 0 | 0 | 6 | 100% |
| 3 | 6 | 0 | 0 | 6 | 100% |
| 4 | 7 | 1 (MessagingAdapter) | 0 | 6 | 86% |
| 5 | 9 | 2 (WhatsAppAdapter, TelegramAdapter) | 1 (MessagingAdapter→split) | 6 | 78% |
| 6 | 9 | 0 | 0 | 9 | 100% |
| 7–10 | 9 | 0 | 0 | 9 | 100% |
| 11 | 9 | 0 | 0 | 9 | 100% |
| 12 | 10 | 1 (AccountLinkToken) | 0 | 9 | 90% |
| 13–15 | 11 | 1 (AppConfig) | 0 | 10 | 91%→100% |

**Ontology fully converged at Round 13.** All 11 entities stable for 3 consecutive rounds.

---

## Open Questions (Accepted as Unknowns for MVP Build)

| Question | Impact | Mitigation |
|---------|--------|-----------|
| Production hosting (AWS/GCP/DO?) | Deployment pipeline, docker-compose.prod.yml shape | Design for container portability; any OCI-compatible host works |
| Loan partner | Whether "Apply Now" sends to a Google Form, Typeform, or actual API | Build tracking + click capture; loan destination is a configurable URL in `app_config` |
| WhatsApp template approval timeline | Outbound scheduled messages need approved templates | Build message content; submit templates to Meta early; test inbound flows first |

**Resolved in rounds 16–23 (no longer open):**
- Supply metric for hotness score → hybrid (historical submissions + admin override)
- Work type categories → PRD options (Food Delivery, Ride, Courier, Multiple); food delivery is primary persona
- Duplicate detection day boundary → rolling 20-hour window
- All PRD button ranges → confirmed as-is (orders, earnings, fuel)
- Loan eligibility thresholds → confirmed from PRD (≥5 records, ≥3 in 7d, avg ≥ ₹700/day)
- Opt-out handling → soft opt-out (`OPTED_OUT` status, STOP/START commands)

---

## Interview Transcript

<details>
<summary>Full Q&A (15 rounds)</summary>

### Round 1
**Q:** Which WhatsApp API provider are you using?
**A:** Not decided — need recommendation
**Ambiguity:** 29.4% (Goal: 0.88, Constraints: 0.48, Criteria: 0.70)

### Round 2
**Q:** Database choice?
**A:** MongoDB
**Ambiguity:** 27% (Goal: 0.88, Constraints: 0.56, Criteria: 0.70)

### Round 3
**Q:** Backend tech stack?
**A:** Java Spring Boot + Maven + MongoDB + Redis OSS + Docker + Docker Compose + Datadog
**Ambiguity:** 22.2% (Goal: 0.88, Constraints: 0.72, Criteria: 0.70)

### Round 4 — CONTRARIAN MODE
**Q:** Is WhatsApp a hard constraint or could you validate with Telegram/SMS first?
**A:** Want to release for both WhatsApp bot AND Telegram bot
**Ambiguity:** 19.6% (Goal: 0.90, Constraints: 0.78, Criteria: 0.70)

### Round 5
**Q:** Deployment target?
**A:** Docker Compose locally; Telegram via Grammy; WhatsApp via Hono + Meta Cloud API (webhook)
**Ambiguity:** 18.4% (Goal: 0.90, Constraints: 0.82, Criteria: 0.70)

### Round 6 — SIMPLIFIER MODE
**Q:** Polyglot (TypeScript bots + Java core) vs pure TypeScript vs pure Java?
**A:** Polyglot: TypeScript bots call Java Spring API
**Ambiguity:** 16% (Goal: 0.90, Constraints: 0.88, Criteria: 0.72)

### Round 7
**Q:** Admin interface shape?
**A:** REST API + Postman/curl (dev-only for MVP)
**Ambiguity:** 13.6% (Goal: 0.90, Constraints: 0.90, Criteria: 0.78)

### Round 8
**Q:** Launch city?
**A:** Kolhapur
**Ambiguity:** 12.4% (Goal: 0.93, Constraints: 0.90, Criteria: 0.78)

### Round 9
**Q:** How do users find and start the bot?
**A:** Multiple channels — wa.me link + QR codes + referral links all work
**Ambiguity:** 11.6% (Goal: 0.95, Constraints: 0.90, Criteria: 0.78)

### Round 10
**Q:** Cross-platform user identity (WA + Telegram)?
**A:** Link accounts with 6-digit code; unlinked users are platform-isolated; earnings merge after linking
**Ambiguity:** 10.1% (Goal: 0.95, Constraints: 0.90, Criteria: 0.83)

### Round 11
**Q:** Where does the conversation flow state machine live?
**A:** Java Spring Boot owns all state including flow step
**Ambiguity:** 8.6% (Goal: 0.95, Constraints: 0.91, Criteria: 0.87)

### Round 12
**Q:** How does Java initiate outbound messages (scheduled prompts)?
**A:** Java calls Meta Cloud API + Telegram Bot API directly
**Ambiguity:** 7.1% (Goal: 0.95, Constraints: 0.92, Criteria: 0.91)

### Round 13
**Q:** Where do the 14 business events get stored and queried?
**A:** MongoDB events collection (custom, no 3rd-party tool)
**Ambiguity:** 5.9% (Goal: 0.95, Constraints: 0.94, Criteria: 0.93)

### Round 14
**Q:** Grammy Telegram bot — long-polling or webhook mode?
**A:** Webhook mode with ngrok external tunnel forwarding to single exposed Docker port
**Ambiguity:** 5.3% (Goal: 0.95, Constraints: 0.96, Criteria: 0.93)

### Round 15
**Q:** Referral reward — tangible or disabled for MVP?
**A:** Reward disabled for MVP, architecture supports it (rewardAmount: null)
**Ambiguity:** 4.4% ✅ (Goal: 0.95, Constraints: 0.96, Criteria: 0.96)

### Round 16
**Q:** How should "Supply" be measured in the hotness score given no real-time location tracking?
**A:** Hybrid — historical submissions (last 7 days) + admin-set supply count + static config
**Ambiguity:** 3.4% (Goal: 0.96, Constraints: 0.97, Criteria: 0.97)

### Round 17
**Q:** What gig work type categories should onboarding offer?
**A:** Food Delivery primary for MVP + open-ended (PRD options: Food Delivery, Ride, Courier, Multiple)
**Ambiguity:** 3.3% (Goal: 0.97, Constraints: 0.97, Criteria: 0.97)

### Round 18
**Q:** When does "today" reset for duplicate earnings detection?
**A:** Rolling 20-hour window (not IST midnight; no date-based unique index)
**Ambiguity:** 2.7% (Goal: 0.97, Constraints: 0.98, Criteria: 0.97)

### Round 19
**Q:** What earnings range buckets should daily capture offer?
**A:** PRD defaults — ₹0–500, ₹500–1000, ₹1000–1500, ₹1500+
**Ambiguity:** 2.3% (Goal: 0.98, Constraints: 0.98, Criteria: 0.99)

### Round 20
**Q:** Confirm or adjust loan eligibility thresholds?
**A:** Confirmed PRD defaults — ≥5 records, ≥3 in last 7 days, avg gross ≥ ₹700/day
**Ambiguity:** 1.7% (Goal: 0.98, Constraints: 0.98, Criteria: 0.99)

### Round 21
**Q:** What happens when a user texts "STOP"?
**A:** Soft opt-out — status: OPTED_OUT, scheduler skips, inbound still works, START re-enables
**Ambiguity:** 1.4% (Goal: 0.98, Constraints: 0.99, Criteria: 0.99)

### Round 22
**Q:** What fuel cost range buckets should daily capture offer?
**A:** PRD defaults — ₹0–100, ₹100–200, ₹200+
**Ambiguity:** 1.2% (Goal: 0.985, Constraints: 0.99, Criteria: 0.99)

### Round 23
**Q:** What order-count buckets should daily capture offer?
**A:** PRD defaults — Less than 10, 10–15, 15–20, 20+
**Ambiguity:** 1.0% ✅ (Goal: 0.99, Constraints: 0.99, Criteria: 0.99)

</details>
