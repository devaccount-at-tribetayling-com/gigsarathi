# Gigsarathi M2/M3/M4 Parallel Launch Plan (v2 — Post Architect ITERATE)

## Plan Status
- Planner: COMPLETE (v1)
- Architect: ITERATE — 4 blocking issues → incorporated below
- Critic: PENDING

---

## RALPLAN-DR Summary

### Principles
1. **Prep before branch** — any shared code that multiple branches will touch must be refactored on `main` before any branch diverges.
2. **Ownership by consumer** — a feature flag belongs to the milestone that first reads it, not the milestone that first writes it.
3. **Independence declared, not assumed** — each milestone's dependency surface is explicitly listed; unstated dependencies are forbidden scope creep.
4. **Merge order is the integration plan** — branch isolation is only safe if merge order is defined and verified before branching.
5. **Intelligence is additive** — a milestone that produces recommendations (M2) and a milestone that consumes those recommendations (M3) can decouple via a raw-data fallback, enabling safe parallelism.

### Decision Drivers
1. **Avoid merge conflicts on shared mutable code** — `DailyEarningsFlow.handleZone()` is the single highest-conflict surface; it must be refactored before branching.
2. **Enable true concurrent development** — M2, M3, M4 can proceed in parallel only if their dependency surfaces are non-overlapping after prep.
3. **Deterministic merge path** — merge order (M4 → M2 → M3) must be locked in before work starts; rebase discipline enforced at M3.

### Viable Options

**Option A: Sequential M2 → M3 → M4 (REJECTED)**
- Safest for merge conflicts; slowest for delivery
- Invalidated: 3 fully independent developers available; sequential blocks velocity by 3–4 weeks

**Option B: M2 + M4 parallel, M3 after M2 (REJECTED)**
- M3 weekly report does not need M2's HotnessScoreService (uses raw EarningsRecord aggregation)
- Invalidated: M3 is more independent than assumed; sequential is unnecessary

**Option C: All three in parallel with prep commit + merge ordering (CHOSEN)**
- Pre-branch prep commit on `main` eliminates the shared-code conflict surface before any branch diverges
- M3 weekly report uses raw `EarningsRecord` aggregation (no M2 scorer dependency in v1)
- Merge order: M4 (no conflicts) → M2 (modifies DailyEarningsFlow) → M3 (rebases onto M2)
- HotnessScoreService v1.5 enhancement (M3 weekly top-zone insight) tracked as post-merge follow-up

---

## Step 0: Pre-Branch Prep Commit on `main` (BLOCKING — before any branch is cut)

This commit must land on `main` and pass CI before M2, M3, or M4 branches are created.
No milestone branch may diverge from main until this commit is green.

### 0a — `PostDailySummaryAction` interface + refactor of `DailyEarningsFlow.handleZone()`

**Problem (Architect ITERATE item 1):** Lines 243–245 of `DailyEarningsFlow.java`:
```java
eventService.emit("daily_summary_viewed", userId, platform, Map.of());
sessionService.clearSession(platform, userId);  // ← cleared BEFORE any chaining
messageSender.sendMessage(userId, platform, sb.toString());
```
Both M2 (tomorrow plan prompt) and M3 (referral prompt) need to trigger AFTER the daily summary — but `clearSession()` is called unconditionally before any extension point exists. Without this refactor, both branches would touch the same lines and produce a guaranteed conflict.

**Solution:** Introduce `PostDailySummaryAction` as a Spring-injected strategy list:

```java
// com.gigsarathi.flow.PostDailySummaryAction
public interface PostDailySummaryAction {
    /**
     * Called after the daily summary message is sent.
     * Return Optional.of(nextState) to keep the session alive and chain a flow.
     * Return Optional.empty() to indicate no chaining — clearSession proceeds.
     */
    Optional<SessionState> apply(String userId, String platform, EarningsRecord record);
}
```

`DailyEarningsFlow.handleZone()` is refactored to:
```java
// After sending the summary message:
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

M1 ships with an empty `actions` list (no-op → session clears as before).
M2 adds `TomorrowPlanAction implements PostDailySummaryAction`.
M3 adds `ReferralPromptAction implements PostDailySummaryAction`.
Both can be committed on separate branches without touching the same lines.

**Acceptance gate for prep commit:**
- [ ] `PostDailySummaryAction` interface exists in `com.gigsarathi.flow`
- [ ] `DailyEarningsFlow` constructor accepts `List<PostDailySummaryAction>` (Spring auto-collects implementations)
- [ ] With empty list (M1 baseline): session is cleared after summary — all M1 tests pass unchanged
- [ ] `mvn test` green on `main` after this commit

### 0b — `previousFlow` field on `SessionState`

**Problem (Architect ITERATE item 4):** Flow chaining (DailyEarnings → TomorrowPlan → Referral) requires knowing which flow was active before the current one. Without this field, M2's TomorrowPlanFlow and M3's ReferralFlow cannot route back correctly after completion.

**Solution:** Add nullable `previousFlow` to `SessionState`:

```java
// Redis session schema — add one field:
{
  "flowType": "TOMORROW_PLAN",
  "stepIndex": 0,
  "pendingData": {},
  "startedAt": "<iso8601>",
  "previousFlow": "DAILY_EARNINGS"  // nullable; null for entry-point flows
}
```

Redis key schema update:

| Key | Value | TTL |
|-----|-------|-----|
| `session:{platform}:{userId}` | `{flowType, stepIndex, pendingData, startedAt, previousFlow}` | 24h |

**Acceptance gate:** `SessionState` POJO/record has `previousFlow` field (nullable String); existing M1 flows set it to `null`; serialization round-trip tested.

---

## M2 — Intelligence Slice (parallel with M3, M4)

### Branch: `feature/m2-intelligence`
### Base: `main` after prep commit

**Deliverables (unchanged from MVP plan, with two amendments):**

1. **HotnessScoreService** — formula: `(AvgEarnings × Demand) / (Supply + 1)`
   - Supply resolution: `zone.estimatedSupply ?? historicalCount(zone, timeSlot, last7d) ?? 0`
   - Tie-breaking (simplified per Architect): `score DESC, recommendationPriority ASC`
     - `baseDemandScore DESC, zone ASC` secondary tiers removed — `recommendationPriority` is admin-configurable and sufficient as the single tiebreaker after score
   - **Redis cache**: Score results cached at key `hotness:{zone}:{timeSlot}:{date}` with **1h TTL**
     - Prevents repeated aggregation queries during the post-summary chaining window
     - Cache is advisory: score is always recomputable from `EarningsRecord` if cache misses
2. **TomorrowPlanAction implements PostDailySummaryAction** — triggered after daily summary; highest hotness zone returned as ≤4 lines + tip; `tomorrow_plan_requested` event; sets `previousFlow="DAILY_EARNINGS"` in new session
3. **Flow 4: Peak Nudge** — Spring Scheduler 30min before highest-scored slot; opted-in users only; `peak_nudge_sent` event
4. ~~`PATCH /admin/features`~~ → **MOVED TO M4** (see M2 Amendment below)

### M2 Amendment — `PATCH /admin/features` ownership moves to M4

**Architect ITERATE item 2:** `PATCH /admin/features` toggles `loanEnabled` and `referralRewardEnabled`. Neither flag is read by M2. M2 does not use loan eligibility and does not implement the referral flow. Keeping this endpoint in M2 makes M2 own infrastructure for M4's features — a premature dependency inversion.

**Resolution:** `PATCH /admin/features` is removed from M2 scope and added to M4. M4 is the first milestone that reads `loanEnabled`. M2 ships without any `app_config` writes.

**Impact:** M2 acceptance criteria for `PATCH /admin/features` removed. M4 acceptance criteria gains it.

### M2 Acceptance Criteria (revised)
- [ ] HotnessScoreService implements formula exactly; supply fallback chain verified by unit test
- [ ] Tie-breaking is `score DESC, recommendationPriority ASC` — deterministic for equal scores
- [ ] Score results cached in Redis at `hotness:{zone}:{timeSlot}:{date}` with 1h TTL; cache miss falls back to fresh computation without error
- [ ] `TomorrowPlanAction` fires after daily summary when user replies "Yes"; session preserved with `previousFlow="DAILY_EARNINGS"` and `flowType="TOMORROW_PLAN"`; `tomorrow_plan_requested` event written
- [ ] Peak nudge not sent to `OPTED_OUT` or inactive >7d users
- [ ] M2 branch does NOT modify `DailyEarningsFlow.handleZone()` directly — only adds `TomorrowPlanAction` as a new class

### M2 Dependency Surface
- **Reads**: `EarningsRecord`, `ZoneHeuristic` (both M1)
- **Modifies**: adds `TomorrowPlanAction` (new file), adds HotnessScoreService (new file), adds peak nudge scheduler (new file)
- **Does NOT touch**: `DailyEarningsFlow.handleZone()`, `app_config` collection, `SessionState` schema

---

## M3 — Engagement Slice (parallel with M2, M4)

### Branch: `feature/m3-engagement`
### Base: `main` after prep commit
### Merge base: must rebase onto M2 before merging to main (merge order: M4 → M2 → M3)

**Deliverables (unchanged from MVP plan, with one amendment):**

1. **Flow 5: Inactive Nudge** — daily 7:30AM IST; no record in 2 days; references last profit; "Track Now" button
2. **Flow 7: Weekly Report** — Sunday 7:30PM IST; ≥2 records get report (total, fuel, profit, best day, insight); <2 records get reminder
3. **Flow 8: Referral** — `ReferralPromptAction implements PostDailySummaryAction`; fires after daily summary when user has ≥3 tracking days OR post-weekly-report; unique code generated at onboarding; click tracking; `rewardAmount: null`
4. **Admin additions (M3)** — `GET /admin/events/summary?from=&to=`, `GET /admin/earnings/export?from=&to=` CSV

### M3 Amendment — HotnessScoreService dependency declared as none (v1)

**Architect ITERATE item 3:** The weekly report "best zone" insight could use M2's `HotnessScoreService` to show the user which zone was hottest their best day. However, this creates a cross-milestone dependency: if M2 is delayed or its API changes, M3 is blocked.

**Resolution (explicit declaration):** M3 weekly report v1 uses **raw `EarningsRecord` aggregation only**:
- "Best day" = max gross earnings record by `submittedAt`
- "Top zone" = zone from the best `EarningsRecord` (not a hotness-scored recommendation)
- No import of or call to `HotnessScoreService`

Post-merge enhancement (v1.5, tracked as a follow-up ticket): After M2 merges to `main` and M3 has rebased, a small enhancement can add the hotness-scored zone insight to the weekly report using `HotnessScoreService`. This decouples M3 v1 delivery from M2 availability.

### M3 Acceptance Criteria (revised)
- [ ] Inactive nudge skips `OPTED_OUT` users and users active within 2 days
- [ ] Weekly report: all 5 data points included; "best day" and "top zone" derived from raw `EarningsRecord` aggregation — no call to `HotnessScoreService`
- [ ] `ReferralPromptAction` fires after daily summary when user has ≥3 tracking days; sets `previousFlow="DAILY_EARNINGS"` in new session; `referral_prompt_sent` event written
- [ ] Referral code unique per user; `rewardAmount: null`; no reward messaging shown
- [ ] `GET /admin/events/summary` returns correct funnel counts
- [ ] M3 branch does NOT modify `DailyEarningsFlow.handleZone()` directly — only adds `ReferralPromptAction` as a new class
- [ ] M3 branch does NOT import `HotnessScoreService`

### M3 Dependency Surface
- **Reads**: `EarningsRecord`, `User` (both M1), `SessionState` with `previousFlow` (prep commit)
- **Modifies**: adds `ReferralPromptAction` (new file), adds inactive nudge scheduler (new file), adds weekly report scheduler (new file), adds admin event/export controllers (new files)
- **Does NOT touch**: `DailyEarningsFlow.handleZone()`, `HotnessScoreService`, `app_config`

---

## M4 — Advanced Slice (parallel with M2, M3)

### Branch: `feature/m4-advanced`
### Base: `main` after prep commit
### Merge first (before M2 and M3) — fully non-overlapping with both

**Deliverables (amended to include `PATCH /admin/features`):**

1. **Flow 6: Loan Entry Point** — eligibility: ≥5 records AND ≥3 in last 7d AND avg gross ≥₹700; Redis `loan-offered:{userId}` (30d TTL); `loanEnabled` feature-flag gated (reads from `app_config`)
2. **Account linking** — 6-digit code; Redis `link-token:{code}` (10min TTL); earnings merge to primary; secondary `MERGED`
3. **`PATCH /admin/features`** — toggles `loanEnabled`, `referralRewardEnabled` in `app_config` collection; `X-Admin-Key` auth; **moved from M2 to M4** — M4 is the first milestone that reads these flags
4. **Datadog full wiring** — JMX + APM on Java; log forwarding; scheduler job health dashboard
5. **Hardening** — load test M1 flows; review idempotency record accumulation; tune MongoDB indexes

### M4 Acceptance Criteria (amended)
- [ ] Loan prompt only to eligible users; `loan-offered:{userId}` Redis key prevents re-send within 30d
- [ ] Loan prompt only shown when `loanEnabled=true` in `app_config`
- [ ] `PATCH /admin/features` toggles `loanEnabled`, `referralRewardEnabled`; requires valid `X-Admin-Key`; updates `app_config` document in MongoDB
- [ ] `loan_offer_shown`, `loan_offer_clicked` events written; no approval-implied language
- [ ] 6-digit link code expires 10min (Redis TTL verified); after linking: secondary `status=MERGED`; earnings re-pointed to primary
- [ ] Datadog dashboard shows scheduler success/failure counts

### M4 Dependency Surface
- **Reads**: `EarningsRecord`, `User` (both M1); `app_config` collection (M4 creates and owns)
- **Modifies**: adds loan flow (new file), adds account linking flow (new file), adds `PATCH /admin/features` controller (new file), adds Datadog config
- **Does NOT touch**: `DailyEarningsFlow`, `HotnessScoreService`, `PostDailySummaryAction`, flow chaining

---

## Parallel Execution Protocol

### Phase 1: Prep (sequential — 1 day)
```
main ──[ prep commit: PostDailySummaryAction + previousFlow ]──►
```
Gate: `mvn test` green. All M1 acceptance criteria still pass. Only then cut branches.

### Phase 2: Parallel development (concurrent — 6–8 days)
```
main ──────────────────────────────────────────────────────────────►
       └─── feature/m2-intelligence ──────────────────────────────►
       └─── feature/m3-engagement ───────────────────────────────►
       └─── feature/m4-advanced ─────────────────────────────────►
```
Each branch's dependency surface is non-overlapping (verified above).
No branch touches `DailyEarningsFlow.handleZone()` directly.

### Phase 3: Merge (sequential — merge order is fixed)
```
1. Merge feature/m4-advanced → main   (no conflict surface with M2/M3)
2. Merge feature/m2-intelligence → main  (modifies PostDailySummaryAction consumers)
3. Rebase feature/m3-engagement onto main, then merge
```
**Why this order:** M4 is cleanest (no shared surfaces with M2/M3). M2 adds `TomorrowPlanAction` first. M3 rebases last and resolves any test-file conflicts from M2's changes to the Spring context (the only realistic conflict zone after the prep commit).

### Conflict Hotspots (explicit)
| File | Risk | Mitigation |
|------|------|-----------|
| `DailyEarningsFlow.java` | HIGH before prep; NONE after prep | Prep commit is the gate |
| Spring `@SpringBootApplication` context | LOW | M2/M3 both add beans via `@Component`; no manual wiring |
| `application.yml` | LOW | M2 adds `hotness.cache.ttl`; M4 adds nothing new; no overlap |
| `SessionState.java` | NONE | `previousFlow` field added in prep; no branch touches it again |

---

## M2/M3/M4 Backlog Items (not blocking parallel execution)

These were flagged during M1 Phase 4 security review and are tracked for M2 execution:

1. **WhatsApp HMAC webhook signature verification** — M2 priority; prevents replay from non-Meta sources
2. **WebClient HTTP timeouts** — 5s connect, 10s response; add to `WebClientConfig` in M2
3. **`@RestControllerAdvice` global exception handler** — prevents stack trace leakage in 500s; M2
4. **PII redaction in send logs** — `log.debug` in `WhatsAppMessageSender` logs `to` field; mask in M2

---

## Acceptance Gate for Parallel Launch Decision

Before cutting M2/M3/M4 branches, all of the following must be true:

- [ ] `PostDailySummaryAction` interface committed to `main`
- [ ] `DailyEarningsFlow` refactored to use `List<PostDailySummaryAction>` with empty-list baseline
- [ ] `SessionState` has `previousFlow` (nullable String) field
- [ ] `mvn test` passes on `main` with the prep changes
- [ ] M1 verification gate (all 11 checks from the MVP plan) complete
- [ ] Merge order documented and acknowledged by all three developers: M4 → M2 → M3
- [ ] M3 developer acknowledges: no `HotnessScoreService` import in M3 v1
- [ ] M2 developer acknowledges: `PATCH /admin/features` is not M2 scope
- [ ] M4 developer acknowledges: `PATCH /admin/features` is M4 scope, owns `app_config`
