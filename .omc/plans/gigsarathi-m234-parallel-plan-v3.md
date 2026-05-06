# Gigsarathi M2/M3/M4 Parallel Launch Plan (v3 — Post Critic ITERATE)

## Plan Status
- Planner: v1, v2, v3
- Architect: ITERATE (4 items, all addressed in v2)
- Critic: ITERATE (4 major findings, all addressed in v3)
- Critic v2: PENDING

---

## Changes from v2 (Critic ITERATE items)

1. **Step 0c added**: Pre-declare `FlowType` enum values (`TOMORROW_PLAN`, `REFERRAL`, `LOAN`, `ACCOUNT_LINK`) in prep commit — eliminates enum file as a conflict surface
2. **`PostDailySummaryAction` ordering contract defined**: `@Order(N)` required, values reserved per milestone (M2=10, M3=20, M4=30); `findFirst()` semantics documented; unit test required in prep commit
3. **STOP-during-chain semantics added**: Explicit AC on `OptOutHandler`: STOP in any chained flow clears entire chain, no resume on START
4. **M3 v1.5 dropped from MVP scope**: Weekly report v1 (raw aggregation) is the final MVP deliverable; hotness-scored insight is post-MVP backlog, no deferred ticket implied
5. **MVP plan supersession note added**: M2 deliverable #5 in the MVP plan is explicitly superseded
6. **Referral code backfill specified**: Users onboarded before M3 deploy receive a referral code on their next bot interaction (lazy migration); documented as M3 AC
7. **Acceptance gate strengthened**: 7 additional items covering FlowType, ordering, STOP-chain, and v1.5 decision
8. **Hotness cache invalidation**: `POST /admin/zones` must invalidate `hotness:{zone}:*` Redis keys; defined in M2 AC
9. **`app_config` bootstrap**: M4 creates collection with default values (`loanEnabled: false`, `referralRewardEnabled: false`) via a startup `@EventListener(ApplicationReadyEvent.class)` that upserts defaults if absent

---

## RALPLAN-DR Summary

### Principles
1. **Prep before branch** — any shared code (logic OR vocabulary) that multiple branches will touch must be resolved on `main` before any branch diverges. Enum values are vocabulary and follow the same rule as logic.
2. **Ownership by consumer** — a feature flag belongs to the milestone that first reads it, not the milestone that first writes it.
3. **Independence declared, not assumed** — each milestone's dependency surface is explicitly listed; unstated dependencies are forbidden scope creep. Enum values and action ordering are dependency surfaces.
4. **Merge order is the integration plan** — branch isolation is only safe if merge order is defined and verified before branching.
5. **Intelligence is additive, not deferred** — a milestone that produces recommendations (M2) and a milestone that consumes them (M3) decouple via raw-data v1 with NO forward dependency to a "v1.5". If hotness insight matters for weekly report, it belongs in M3 v1 scope — otherwise it is post-MVP, full stop.

### Decision Drivers
1. **Eliminate shared surfaces before branching** — both logic files (`DailyEarningsFlow`) and vocabulary files (`FlowType` enum) must have all needed content on `main` before branch divergence.
2. **Enable true concurrent development** — M2, M3, M4 proceed in parallel only if their dependency surfaces are non-overlapping after prep.
3. **Deterministic merge path** — merge order M4 → M2 → M3 is locked before work starts; rebase discipline enforced at M3.

### Viable Options

**Option A: Sequential M2 → M3 → M4 (REJECTED)**
- Safest for merge conflicts; slowest for delivery; wastes ~3 developer-weeks of concurrent capacity
- Invalidated: all identified shared surfaces are resolvable in prep; remaining surfaces are non-overlapping

**Option B: M2 + M4 parallel, M3 sequential after M2 (REJECTED)**
- Avoids M3's hotness dependency — but the v1.5 hotness follow-up is now explicitly DROPPED, making M3 fully independent in v3
- Invalidated: with v1.5 dropped, M3 has no dependency on M2 in v1; Option B saves no time while losing 1 developer-week of parallelism

**Option C: All three in parallel with prep commit + merge ordering (CHOSEN)**
- Pre-branch prep commit eliminates all shared surfaces (logic, vocabulary, ordering contract, STOP semantics)
- M3 weekly report v1 uses raw `EarningsRecord` aggregation; no M2 dependency; no v1.5 follow-up
- Merge order: M4 (no conflicts) → M2 → M3 (rebases onto M2)
- Only "v1.5" reference in this document: it is post-MVP, unscoped, unowned, and NOT implied by any AC in this plan

---

## Step 0: Pre-Branch Prep Commit on `main` (BLOCKING — before any branch is cut)

This commit must land on `main` and pass CI before M2, M3, or M4 branches are created.
No milestone branch may diverge from main until this commit is green.
**The prep commit is the gate.** Step 0 has a designated reviewer (team lead or most senior dev) before the gate is opened.

### 0a — `PostDailySummaryAction` interface + refactor of `DailyEarningsFlow.handleZone()`

**Problem:** In `DailyEarningsFlow.java`, `clearSession()` is called before `sendMessage()`, with no extension point for chaining. Both M2 (tomorrow plan) and M3 (referral) need to trigger after the summary without modifying this method directly.

**Interface contract (Javadoc must include the following):**

```java
/**
 * Extension point for flows that chain off the daily earnings summary.
 *
 * ORDERING CONTRACT: Implementations MUST declare @Order(N) where N is
 * reserved by milestone: M2=10, M3=20, M4=30. Lower N fires first.
 * The FIRST non-empty Optional<SessionState> returned wins and becomes
 * the new session. Subsequent actions are NOT called once a winner is found.
 *
 * STOP SEMANTICS: If the user sends STOP while in any chained flow
 * (flowType != DAILY_EARNINGS and flowType != ONBOARDING), OptOutHandler
 * clears the entire session chain. No resume on START. Implementations
 * must NOT prevent STOP from propagating.
 *
 * COMPOSITION RULE: An action should return Optional.empty() if the user
 * does not qualify (e.g., insufficient tracking days, feature flag off).
 * It should return Optional.of(SessionState) only when it definitively
 * takes ownership of the next session step.
 */
public interface PostDailySummaryAction {
    Optional<SessionState> apply(String userId, String platform, EarningsRecord record);
}
```

**`DailyEarningsFlow` refactored tail of `handleZone()`:**

```java
messageSender.sendMessage(userId, platform, sb.toString());
eventService.emit("daily_summary_viewed", userId, platform, Map.of());

Optional<SessionState> chained = actions.stream()
    .map(a -> a.apply(userId, platform, record))
    .filter(Optional::isPresent)
    .map(Optional::get)
    .findFirst();

if (chained.isPresent()) {
    sessionService.saveSession(platform, userId, chained.get());
} else {
    sessionService.clearSession(platform, userId);
}
```

Note: `sendMessage` and `eventService.emit` are moved BEFORE the chaining logic (corrects the pre-existing M1 ordering where `clearSession` preceded `sendMessage`).

M1 ships with empty `actions` list → `clearSession` as before. All M1 tests pass unchanged.

**Unit test required in prep commit:**
- Empty `actions` list → session cleared
- Single action returning `Optional.empty()` → session cleared
- Single action returning `Optional.of(state)` → session saved with that state
- Two actions both returning non-empty → first by `@Order` wins, second not called
- Two actions, first returns `Optional.empty()`, second returns non-empty → second wins

**Acceptance gate items for 0a:**
- [ ] `PostDailySummaryAction` interface exists with Javadoc stating ordering, STOP semantics, composition rules
- [ ] `DailyEarningsFlow` constructor accepts `List<PostDailySummaryAction>` (Spring auto-collects `@Component` impls)
- [ ] `sendMessage` and `eventService.emit` called BEFORE chaining block
- [ ] All 5 unit test cases above pass
- [ ] M1 tests pass with empty actions list

### 0b — `previousFlow` field on `SessionState`

```java
// Add to SessionState:
private final String previousFlow; // nullable; null for entry-point flows (ONBOARDING, DAILY_EARNINGS)
```

Redis session schema update:

| Key | Value | TTL |
|-----|-------|-----|
| `session:{platform}:{userId}` | `{flowType, stepIndex, pendingData, startedAt, previousFlow}` | 24h |

`previousFlow` is null for ONBOARDING and DAILY_EARNINGS. Set to the outgoing `flowType` when a `PostDailySummaryAction` creates the chained session.

**Acceptance gate item for 0b:**
- [ ] `SessionState` POJO/record has `previousFlow` field (nullable String)
- [ ] Serialization round-trip test: null and non-null `previousFlow` both serialize/deserialize correctly

### 0c — `FlowType` enum pre-declaration (Critic Major Finding #1)

**Problem:** `FlowType.java` currently contains `{ONBOARDING, DAILY_EARNINGS, NONE}`. M2 needs `TOMORROW_PLAN`; M3 needs `REFERRAL`; M4 needs `LOAN` and `ACCOUNT_LINK`. All three branches would edit the same file → guaranteed merge conflict on every branch. Enum vocabulary files are shared surfaces subject to the same rule as logic files.

**Fix:** Pre-declare all required values in prep commit:

```java
public enum FlowType {
    NONE,
    ONBOARDING,
    DAILY_EARNINGS,
    TOMORROW_PLAN,    // M2: chained off daily summary "Yes" response
    REFERRAL,         // M3: chained off daily summary after ≥3 tracking days
    LOAN,             // M4: loan eligibility entry point
    ACCOUNT_LINK      // M4: 6-digit account linking
}
```

Values are unused at prep-commit time. M2/M3/M4 branches reference existing enum values — no enum file edits needed on any branch.

**Acceptance gate item for 0c:**
- [ ] `FlowType` enum has all 6 values (`NONE`, `ONBOARDING`, `DAILY_EARNINGS`, `TOMORROW_PLAN`, `REFERRAL`, `LOAN`, `ACCOUNT_LINK`) on `main` before any branch is cut
- [ ] `FlowEngine` compiles and all M1 tests pass (new values are unused but present)

### 0d — STOP-during-chain semantics in `OptOutHandler` (Critic Major Finding #3)

**Problem:** `OptOutHandler.handleStop()` calls `clearSession()` unconditionally. When a chained flow is active (`flowType ∈ {TOMORROW_PLAN, REFERRAL, LOAN, ACCOUNT_LINK}`), STOP must still clear the entire chain. The behavior is correct as-is, but it is undocumented — M2/M3/M4 devs may add logic that inadvertently interferes (e.g., checking `previousFlow` before allowing STOP).

**Fix:** Add explicit comment to `OptOutHandler.handleStop()`:

```java
// STOP always clears the entire session regardless of flowType or previousFlow.
// This applies to all chained flows (TOMORROW_PLAN, REFERRAL, LOAN, ACCOUNT_LINK).
// START returns the user to the ACTIVE scheduler pool; it does NOT resume
// a previously interrupted chain.
sessionService.clearSession(platform, userId);
```

**Unit test required in prep commit:**
- STOP while `flowType=TOMORROW_PLAN` → session cleared, `status=OPTED_OUT`, no resume
- STOP while `flowType=REFERRAL` → same
- START after STOP with `previousFlow` set → session does NOT restore previous flow

**Acceptance gate item for 0d:**
- [ ] STOP-chain unit tests (3 cases above) pass
- [ ] No milestone branch adds a guard on STOP that checks `flowType` or `previousFlow`

### Prep Commit: Full Acceptance Gate Summary

All of the following must be green on `main` before any M2/M3/M4 branch is cut:

- [ ] 0a: `PostDailySummaryAction` interface with Javadoc + `DailyEarningsFlow` refactored (5 unit tests)
- [ ] 0b: `SessionState.previousFlow` field with null/non-null serialization test
- [ ] 0c: `FlowType` enum has all 6 values; M1 tests pass
- [ ] 0d: STOP-chain semantics commented in `OptOutHandler`; 3 unit tests pass
- [ ] `mvn test` green on `main` (all M1 tests pass, new prep tests pass)
- [ ] Prep commit reviewed and approved by team lead before branches are cut
- [ ] M1 verification gate (all 11 checks from MVP plan) complete

---

## M2 — Intelligence Slice (parallel with M3, M4)

### Branch: `feature/m2-intelligence`
### Base: `main` after prep commit

**Deliverables:**

1. **HotnessScoreService** — formula: `(AvgEarnings × Demand) / (Supply + 1)`
   - Supply resolution: `zone.estimatedSupply ?? historicalCount(zone, timeSlot, last7d) ?? 0`
   - Tie-breaking: `score DESC, recommendationPriority ASC`
   - **Redis cache**: `hotness:{zone}:{timeSlot}:{date}` with **1h TTL**
   - **Cache invalidation**: `POST /admin/zones` (and any zone update) must call `redisTemplate.delete("hotness:{zone}:*")` or equivalent pattern-delete after writing to MongoDB. Without this, admin supply updates take up to 1h to affect recommendations.

2. **`TomorrowPlanAction implements PostDailySummaryAction`**
   - `@Order(10)` — M2 fires before M3 (M3=20)
   - Triggers after daily summary when user replies "Yes"
   - Sets `previousFlow = "DAILY_EARNINGS"`, `flowType = TOMORROW_PLAN`
   - `tomorrow_plan_requested` event written

3. **Flow 4: Peak Nudge** — 30min before highest-scored slot; `ACTIVE` users with `lastActiveAt` within 7 days only; `peak_nudge_sent` event

4. ~~`PATCH /admin/features`~~ → **MOVED TO M4** (see note below)

> **MVP plan supersession (Critic Required Change #5):** The MVP build plan (line ~190) lists `PATCH /admin/features` as M2 deliverable #5. That entry is **superseded by this parallel plan**. M2 does not implement `PATCH /admin/features`. M4 owns it. Any M2 developer reading the MVP plan should disregard deliverable #5 and use this document as authoritative scope.

### M2 Acceptance Criteria
- [ ] `HotnessScoreService` implements formula; supply fallback chain verified by unit test
- [ ] Tie-breaking: `score DESC, recommendationPriority ASC` — deterministic unit test for equal scores
- [ ] Score results cached at `hotness:{zone}:{timeSlot}:{date}` with 1h TTL
- [ ] `POST /admin/zones` (zone upsert) triggers deletion of `hotness:{zone}:*` Redis keys; verified by unit/integration test
- [ ] `TomorrowPlanAction` is annotated `@Order(10)` and implements `PostDailySummaryAction`; fires after daily summary on "Yes"; `tomorrow_plan_requested` event written; new session has `flowType=TOMORROW_PLAN`, `previousFlow=DAILY_EARNINGS`
- [ ] Peak nudge not sent to `OPTED_OUT` or `lastActiveAt` > 7 days users
- [ ] M2 branch does NOT modify `DailyEarningsFlow.handleZone()` — only adds new classes
- [ ] M2 branch does NOT add entries to `FlowType` enum (all values pre-declared in prep commit)
- [ ] M2 branch does NOT implement or reference `PATCH /admin/features`

### M2 Dependency Surface
- **Reads**: `EarningsRecord`, `ZoneHeuristic` (M1), `FlowType.TOMORROW_PLAN` (prep commit)
- **Adds**: `TomorrowPlanAction` (new file), `HotnessScoreService` (new file), `PeakNudgeScheduler` (new file)
- **Does NOT touch**: `DailyEarningsFlow.handleZone()`, `FlowType.java`, `app_config`, `SessionState.java`

---

## M3 — Engagement Slice (parallel with M2, M4)

### Branch: `feature/m3-engagement`
### Base: `main` after prep commit
### Merge base: rebase onto M2-merged-main before merging (merge order: M4 → M2 → M3)

**Deliverables:**

1. **Flow 5: Inactive Nudge** — daily 7:30AM IST; no record in 2 days; references last profit; "Track Now" button
2. **Flow 7: Weekly Report** — Sunday 7:30PM IST; ≥2 records get report; <2 records get reminder
   - "Best day": max gross earnings by `submittedAt`
   - "Top zone": zone from the best `EarningsRecord` (raw aggregation, NOT `HotnessScoreService`)
   - **No v1.5 follow-up**: this is the final MVP weekly report. Hotness-scored zone insight is post-MVP scope with no implied timeline. Not referenced in this plan.
3. **`ReferralPromptAction implements PostDailySummaryAction`** — `@Order(20)`; fires after daily summary when `count(EarningsRecord for user) >= 3`; `previousFlow="DAILY_EARNINGS"`, `flowType=REFERRAL`; `referral_prompt_sent` event
4. **Referral code lazy migration** — users onboarded before M3 deploy have no referral code. On their next bot interaction after M3 deploy, `ReferralService.ensureReferralCode(userId)` generates and saves a code if absent. No bulk migration script needed for MVP.
5. **Admin additions (M3)** — `GET /admin/events/summary?from=&to=`, `GET /admin/earnings/export?from=&to=` CSV

### M3 Acceptance Criteria
- [ ] Inactive nudge skips `OPTED_OUT` users and users with a record within 2 days
- [ ] Weekly report: all 5 data points (total, fuel, profit, best day, top zone); best day and top zone from raw `EarningsRecord` aggregation — `HotnessScoreService` is not imported
- [ ] Weekly report: users with <2 records receive reminder message only
- [ ] `ReferralPromptAction` is annotated `@Order(20)` and implements `PostDailySummaryAction`; fires when `EarningsRecord` count ≥ 3; sets `previousFlow=DAILY_EARNINGS`, `flowType=REFERRAL`; `referral_prompt_sent` event written
- [ ] `ReferralService.ensureReferralCode(userId)` called on first bot interaction for pre-M3 users; generates unique code if absent; idempotent (safe to call on every interaction)
- [ ] Referral code unique per user; `rewardAmount: null`; no reward messaging shown
- [ ] `GET /admin/events/summary` returns correct funnel counts from `events` collection
- [ ] M3 branch does NOT modify `DailyEarningsFlow.handleZone()`
- [ ] M3 branch does NOT add entries to `FlowType` enum
- [ ] M3 branch does NOT import `HotnessScoreService`

### M3 Dependency Surface
- **Reads**: `EarningsRecord`, `User` (M1); `SessionState.previousFlow` (prep commit); `FlowType.REFERRAL` (prep commit)
- **Adds**: `ReferralPromptAction` (new file), `InactiveNudgeScheduler` (new file), `WeeklyReportScheduler` (new file), admin event/export controllers (new files)
- **Does NOT touch**: `DailyEarningsFlow.handleZone()`, `FlowType.java`, `HotnessScoreService`, `app_config`

---

## M4 — Advanced Slice (parallel with M2, M3)

### Branch: `feature/m4-advanced`
### Base: `main` after prep commit
### Merge first (before M2 and M3) — fully non-overlapping with both

**Deliverables:**

1. **Flow 6: Loan Entry Point** — eligibility: ≥5 records AND ≥3 in last 7d AND avg gross ≥₹700; Redis `loan-offered:{userId}` (30d TTL); `loanEnabled` feature-flag gated
2. **Account linking** — 6-digit code; Redis `link-token:{code}` (10min TTL); earnings merge to primary; secondary `MERGED`
3. **`PATCH /admin/features`** — toggles `loanEnabled`, `referralRewardEnabled` in `app_config`; `X-Admin-Key` auth; **M4 is the first milestone that reads these flags**
4. **`app_config` bootstrap** — `@EventListener(ApplicationReadyEvent.class)` upserts document on startup if absent: `{loanEnabled: false, referralRewardEnabled: false}`. Safe to re-run (upsert, not insert). This ensures the collection exists before `PATCH /admin/features` is called and before loan eligibility is checked.
5. **Datadog full wiring** — JMX + APM; log forwarding; scheduler job health dashboard
6. **Hardening** — load test M1 flows; review idempotency record accumulation; tune MongoDB indexes

### M4 Acceptance Criteria
- [ ] `app_config` document upserted with defaults on startup if absent; verified by `docker-compose down -v && docker-compose up` (collection created with defaults)
- [ ] `PATCH /admin/features` toggles `loanEnabled`, `referralRewardEnabled`; requires valid `X-Admin-Key`; returns 401 without header
- [ ] Loan prompt only to eligible users; `loan-offered:{userId}` Redis key prevents re-send within 30d
- [ ] Loan prompt only shown when `loanEnabled=true`
- [ ] `loan_offer_shown`, `loan_offer_clicked` events written; no approval-implied language
- [ ] 6-digit link code expires 10min (Redis TTL verified); after linking: secondary `status=MERGED`; earnings re-pointed to primary
- [ ] Datadog dashboard shows scheduler success/failure counts
- [ ] M4 branch does NOT add entries to `FlowType` enum (`LOAN` and `ACCOUNT_LINK` are pre-declared in prep commit)

### M4 Dependency Surface
- **Reads**: `EarningsRecord`, `User` (M1); `FlowType.LOAN`, `FlowType.ACCOUNT_LINK` (prep commit)
- **Creates**: `app_config` collection (owns it); loan flow (new file); account linking flow (new file); `PATCH /admin/features` controller (new file); Datadog config
- **Does NOT touch**: `DailyEarningsFlow`, `HotnessScoreService`, `PostDailySummaryAction`, flow chaining, `FlowType.java`

---

## Conflict Hotspot Table (revised — Critic Required Change)

| File | Risk (before prep) | Risk (after prep) | Mitigation |
|------|-------------------|-------------------|-----------|
| `DailyEarningsFlow.java` | HIGH | NONE | Step 0a refactors to `List<PostDailySummaryAction>` |
| `FlowType.java` | HIGH | NONE | Step 0c pre-declares all 6 values |
| `SessionState.java` | MEDIUM | NONE | Step 0b adds `previousFlow`; no branch touches it again |
| `OptOutHandler.java` | MEDIUM | NONE | Step 0d comments STOP semantics; no branch adds guards |
| Spring context (`@SpringBootApplication`) | LOW | LOW | M2/M3 both add beans via `@Component`; no manual wiring; `@SpringBootApplication` not touched |
| `application.yml` | LOW | LOW | M2 adds `hotness.cache.ttl`; M4 adds `datadog.*`; no overlap between branches |
| `GigsarathiApplicationTests.java` | LOW | LOW | Full-context test; new beans must have satisfiable dependencies; `@MockBean` for any new external clients |
| `ZoneController.java` | LOW | LOW | M2 adds cache-invalidation call to existing zone upsert; no other branch touches zone controller |

---

## Parallel Execution Protocol

### Phase 1: Prep (sequential — 1 day, reviewed by team lead)
```
main ──[ prep commit: 0a+0b+0c+0d + mvn test green + 11 M1 gates ]──►
```

### Phase 2: Parallel development (concurrent — 6–8 days)
```
main (post-prep) ──────────────────────────────────────────────────────►
       └─── feature/m2-intelligence ──────────────────────────────────►
       └─── feature/m3-engagement ────────────────────────────────────►
       └─── feature/m4-advanced ──────────────────────────────────────►
```

### Phase 3: Merge (sequential — merge order is locked)
```
1. Merge feature/m4-advanced → main    (no conflict surface; fully independent)
2. Merge feature/m2-intelligence → main  (TomorrowPlanAction + HotnessScoreService)
3. Rebase feature/m3-engagement onto main, then merge  (ReferralPromptAction + weekly report)
```
M3 rebase is the only expected friction point: test files in `src/test/java/com/gigsarathi/flow/` may need trivial re-imports after M2 adds beans. No logic conflicts expected.

**CI enforcement**: A branch protection rule (or PR template reminder) must state: "Do not merge M3 until M4 and M2 are on main." If CI cannot enforce merge order, a manual sign-off is required.

---

## M2 Hardening Backlog (tracked for M2 execution, not blocking prep or branch-cut)

Flagged during M1 Phase 4 security review:
1. **WhatsApp HMAC webhook signature verification** — `whatsapp-bot/` TypeScript layer; prevents replay from non-Meta sources; M2 priority
2. **WebClient HTTP timeouts** — 5s connect, 10s response; `WebClientConfig.java`; M2 priority
3. **`@RestControllerAdvice` global exception handler** — prevents stack trace leakage; M2 priority
4. **PII redaction in send logs** — `log.debug` in `WhatsAppMessageSender` logs `to` field; mask or suppress; M2 priority

---

## Full Acceptance Gate for Parallel Launch Decision

Must ALL be true before cutting M2/M3/M4 branches:

**Prep commit gates:**
- [ ] `PostDailySummaryAction` interface with Javadoc (ordering, STOP, composition rules) committed
- [ ] `DailyEarningsFlow` accepts `List<PostDailySummaryAction>`; `sendMessage` before chaining block
- [ ] 5 `PostDailySummaryAction` unit tests pass (empty list, single-empty, single-present, two-present first-wins, two-present second-wins)
- [ ] `SessionState.previousFlow` field present; null/non-null serialization test passes
- [ ] `FlowType` enum has all 6 values: `NONE, ONBOARDING, DAILY_EARNINGS, TOMORROW_PLAN, REFERRAL, LOAN, ACCOUNT_LINK`
- [ ] 3 STOP-chain unit tests pass (STOP during TOMORROW_PLAN, STOP during REFERRAL, START does not restore chain)
- [ ] `mvn test` green on `main` with all prep changes
- [ ] Prep commit reviewed and approved by team lead

**M1 verification gate:**
- [ ] All 11 M1 verification checks from the MVP plan pass (docker boot, zone seed, onboarding, scheduler delivery, opt-out, events, replay, outbound, admin auth, indexes)

**Scope acknowledgements (written sign-off, not verbal):**
- [ ] M2 developer: `PATCH /admin/features` is NOT M2 scope (MVP plan deliverable #5 superseded by this document)
- [ ] M2 developer: no `FlowType` enum edits on M2 branch; use `FlowType.TOMORROW_PLAN` from prep commit
- [ ] M3 developer: no `HotnessScoreService` import in M3 branch; weekly report uses raw aggregation; no v1.5 implied
- [ ] M3 developer: no `FlowType` enum edits on M3 branch; use `FlowType.REFERRAL` from prep commit
- [ ] M4 developer: `PATCH /admin/features` IS M4 scope; M4 owns `app_config` collection; no `FlowType` enum edits on M4 branch
- [ ] Merge order acknowledged by all three: **M4 → M2 → M3**

---

## Pre-mortem Scenarios (from Critic — addressed)

**Scenario 1: FlowType enum conflict on merge** → Mitigated by Step 0c. All 6 values pre-declared on `main` before branching. No branch touches `FlowType.java`.

**Scenario 2: Action ordering → M3 referral never fires** → Mitigated by `@Order(10)` for M2 and `@Order(20)` for M3, documented in interface Javadoc and enforced by unit tests in prep commit. With `@Order`, Spring's injection order is deterministic.

**Scenario 3: v1.5 follow-up never lands** → Non-issue: v1.5 is explicitly DROPPED from MVP scope. The weekly report using raw aggregation is the final MVP deliverable. No forward dependency, no deferred ticket.
