# Gigsarathi M2/M3/M4 Parallel Launch Plan (v5 — Post Architect v3 ITERATE)

## Plan Status
- Planner: v1, v2, v3, v4, v5
- Architect: v1 ITERATE → v2 APPROVE (3 non-blocking tensions) → v3 ITERATE (2 blocking items) → v4 PENDING
- Critic: v1 ITERATE → v2 ITERATE → v3 PENDING

---

## Changes from v4 (Architect v3 blocking items — all addressed)

1. **Cache invalidation infrastructure fix (Blocking #1)**: Removed `cacheManager.getCache("hotness").clear()`. The repo has no `spring-boot-starter-cache`, no `@EnableCaching`, no `CacheManager` bean. Replaced with `redisTemplate.keys("hotness:" + zoneId + ":*")` + `redisTemplate.delete(keys)` — uses the existing `RedisTemplate<String,String>` bean already configured in `RedisConfig.java`. `KEYS pattern` is wildcard-aware; `delete(key)` is not. Cache clear wrapped in try/catch so admin zone API returns 200 even if invalidation fails (logged, non-fatal).
2. **SessionState deserialization safety (Blocking #2)**: Added `@JsonIgnoreProperties(ignoreUnknown=true)` to the Step 0b AC for `SessionState`. Added rollback-direction deserialization test to 0b: a session written with `previousFlow` (by new code) must not cause `redisTemplate.delete(key)` when read by old code after rollback — deserialization failure behavior in `SessionService` is explicitly accepted as "session destroyed on parse failure; 24h TTL bounds the blast radius."
3. **FlowType count fixed**: "all 6 values" corrected to "all 7 values" throughout (NONE + ONBOARDING + DAILY_EARNINGS = 3 existing; TOMORROW_PLAN + REFERRAL + LOAN + ACCOUNT_LINK = 4 new; total = 7).
4. **`handleZone()` refactored to explicit loop**: `findFirst()` stream replaced with explicit `for`-`break` loop so "subsequent actions are NOT called once a winner is found" is a structural guarantee, not a stream-laziness assumption.
5. **`DailyEarningsFlowTest.java` added to M1 gate**: "M1 tests pass" gate was empty for the file being refactored; now explicitly requires a `DailyEarningsFlowTest` covering the empty-actions M1 path and event-emission ordering.
6. **Prep Commit Infrastructure Inventory section added**: Explicit checklist of every Spring bean, dependency, and config property that M2/M3/M4 reference but do not own — enforces "Prep before branch" by checklist rather than judgment.

---

## RALPLAN-DR Summary

### Principles
1. **Prep before branch** — any shared code (logic OR vocabulary OR infrastructure) that multiple branches will touch must be resolved on `main` before any branch diverges.
2. **Ownership by consumer** — a feature flag belongs to the milestone that first reads it, not the milestone that first writes it.
3. **Independence declared, not assumed** — each milestone's dependency surface is explicitly listed; unstated dependencies are forbidden scope creep.
4. **Merge order is the integration plan** — merge order M4 → M2 → M3 is locked before work starts; rebase discipline enforced at M3.
5. **Intelligence is additive, not deferred** — M3 weekly report uses raw `EarningsRecord` aggregation; no v1.5 follow-up implied.

### Decision Drivers
1. Eliminate shared surfaces before branching (logic, vocabulary, infrastructure)
2. Enable true concurrent development with non-overlapping dependency surfaces
3. Deterministic merge path

### Viable Options
- **Option A: Sequential M2→M3→M4 (REJECTED)** — wastes ~3 developer-weeks; shared surfaces are resolvable in prep
- **Option B: M2+M4 parallel, M3 sequential (REJECTED)** — M3 is independent in v1; unnecessary serialization
- **Option C: All three parallel with prep commit (CHOSEN)** — prep commit resolves all shared surfaces; M3 weekly report uses raw aggregation; merge order M4→M2→M3

---

## Step 0: Pre-Branch Prep Commit on `main` (BLOCKING)

This commit must land on `main` and pass CI before M2, M3, or M4 branches are created.
**The prep commit is the gate.** Reviewed and approved by team lead before branches are cut.

### Prep-Commit Revision Protocol

If a scope gap is discovered after branches have been cut:
1. Fix on `main` as a new commit (not on any milestone branch)
2. All three milestone branches rebase onto the updated `main` before continuing
3. CI must be green on `main` before any branch resumes work
4. Scope change logged in this document under a new "Prep Commit Revision" section

No milestone branch may carry prep-commit-level changes.

---

### Prep Commit Infrastructure Inventory

Every Spring bean, dependency, and configuration that M2/M3/M4 reference but do not own must be present in the prep commit. Milestone branches must not add to this list.

| Item | Owner | AC |
|------|-------|-----|
| `PostDailySummaryAction` interface | Step 0a | Interface file committed; Javadoc present |
| `DailyEarningsFlow` `List<PostDailySummaryAction>` wiring | Step 0a | Constructor accepts the list; Spring auto-collects `@Component` impls |
| `SessionState.previousFlow` field | Step 0b | Field present; `@JsonIgnoreProperties(ignoreUnknown=true)` on class |
| `FlowType` enum with all 7 values | Step 0c | All values pre-declared; M1 tests pass |
| STOP-chain semantics comment in `OptOutHandler` | Step 0d | Comment present; 5 unit tests pass |
| `RedisTemplate<String,String>` bean | Already in `RedisConfig.java` | No change needed; verified available for M2 cache invalidation |

---

### 0a — `PostDailySummaryAction` interface + refactor of `DailyEarningsFlow.handleZone()`

**Interface contract (Javadoc must include the following):**

```java
/**
 * Extension point for flows that chain off the daily earnings summary.
 *
 * ORDERING CONTRACT: Implementations MUST declare @Order(N) where N is
 * reserved by milestone: M2=10, M3=20, M4=30. Lower N fires first.
 * Once a non-empty Optional<SessionState> is returned, iteration stops
 * (explicit break — see DailyEarningsFlow.handleZone()). Subsequent
 * actions are NOT called.
 *
 * STOP SEMANTICS: If the user sends STOP while in any chained flow,
 * OptOutHandler clears the entire session chain. No resume on START.
 * Implementations must NOT prevent STOP from propagating.
 *
 * COMPOSITION RULE: Return Optional.empty() if the user does not qualify.
 * Return Optional.of(SessionState) only when definitively taking ownership.
 *
 * EXCEPTION SAFETY: Wrap all external calls (DB, Redis, downstream) in
 * try/catch and return Optional.empty() on failure. The caller does NOT
 * wrap the iteration in try/catch; an uncaught exception propagates and
 * aborts delivery for that user.
 */
public interface PostDailySummaryAction {
    Optional<SessionState> apply(String userId, String platform, EarningsRecord record);
}
```

**`DailyEarningsFlow` refactored tail of `handleZone()` (explicit loop — not stream):**

```java
messageSender.sendMessage(userId, platform, sb.toString());
eventService.emit("daily_summary_viewed", userId, platform, Map.of());

SessionState chained = null;
for (PostDailySummaryAction action : actions) {
    Optional<SessionState> result = action.apply(userId, platform, record);
    if (result.isPresent()) {
        chained = result.get();
        break;
    }
}

if (chained != null) {
    sessionService.saveSession(platform, userId, chained);
} else {
    sessionService.clearSession(platform, userId);
}
```

Note: `sendMessage` and `eventService.emit` moved BEFORE chaining logic (corrects pre-existing M1 ordering where `clearSession` preceded `sendMessage`). The explicit `for`-`break` loop guarantees short-circuit by structure, not by stream-laziness assumption.

M1 ships with empty `actions` list → `clearSession` as before. All M1 tests pass unchanged.

**Unit tests required in prep commit (6 total):**
1. Empty `actions` list → session cleared
2. Single action returning `Optional.empty()` → session cleared
3. Single action returning `Optional.of(state)` → session saved with that state
4. Two actions both returning non-empty → first wins, second action's `apply()` is never called (verified by mock `verify(secondAction, never()).apply(...)`)
5. Two actions, first returns `Optional.empty()`, second returns non-empty → second wins
6. **Spring slice test** (`@SpringJUnitConfig`): define two `@Component PostDailySummaryAction` beans with `@Order(10)` and `@Order(20)`; inject `List<PostDailySummaryAction>`; assert index 0 is the `@Order(10)` implementation. This is the only test that catches a forgotten `@Order` annotation — tests 1–5 use hand-constructed lists.

**`DailyEarningsFlowTest.java` (new, required for M1 gate):**
- Empty actions list path: `sendMessage` called before chaining block; `daily_summary_viewed` event emitted once; session cleared
- This test must exist before prep commit is merged — it validates the sendMessage/emit/clearSession reordering doesn't regress M1 behavior

**Acceptance gate items for 0a:**
- [ ] `PostDailySummaryAction` interface with Javadoc (ordering, STOP, composition, exception-safety) committed
- [ ] `DailyEarningsFlow` constructor accepts `List<PostDailySummaryAction>`; explicit `for`-`break` loop replaces stream
- [ ] `sendMessage` and `eventService.emit` called BEFORE chaining block (verified by `DailyEarningsFlowTest`)
- [ ] `DailyEarningsFlowTest.java` exists and passes M1 path (empty actions, event-emission order)
- [ ] All 6 unit tests pass (5 Mockito + 1 Spring slice)
- [ ] M1 tests pass with empty actions list

---

### 0b — `SessionState.previousFlow` field + deserialization safety

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)   // ← ADDED IN PREP COMMIT
public class SessionState {
    // ... existing fields ...
    private String previousFlow; // nullable; null for ONBOARDING, DAILY_EARNINGS
}
```

Redis session schema update:

| Key | Value | TTL |
|-----|-------|-----|
| `session:{platform}:{userId}` | `{flowType, stepIndex, pendingData, startedAt, previousFlow}` | 24h |

**Deserialization contract (explicitly accepted):**
- **Forward direction** (old session → new code): JSON without `previousFlow` → `previousFlow = null`. Works because `@JsonIgnoreProperties` skips unknown fields, not missing ones; Jackson maps missing fields to null for nullable types. Verified by test.
- **Rollback direction** (new session → old code after rollback): JSON with `previousFlow` → old `SessionState` without that field fails to deserialize (no `@JsonIgnoreProperties` on old code). `SessionService.getSession()` catches `JsonProcessingException` and calls `redisTemplate.delete(key)`, destroying the in-flight session. **This is the accepted rollback behavior**: session destruction is bounded by 24h TTL; users who experience a mid-deployment rollback within 24h will be asked to re-onboard for that session. This is acceptable for MVP. Document this explicitly in `SessionService` as a comment.

**Acceptance gate items for 0b:**
- [ ] `SessionState` has `previousFlow` field (nullable String)
- [ ] `@JsonIgnoreProperties(ignoreUnknown=true)` present on `SessionState` class
- [ ] Forward-compat test: JSON string without `previousFlow` deserializes to `previousFlow=null`, no exception
- [ ] Rollback-behavior test: confirms `SessionService.getSession()` returns `Optional.empty()` and deletes key when JSON has unknown field that causes parse failure on old-code-style `SessionState` (simulated by registering a strict mapper without `ignoreUnknown=true`)
- [ ] Comment in `SessionService.getSession()` catch block: "Session deserialized with unrecognized fields after rollback — key deleted. Users affected within 24h TTL window must re-interact."

---

### 0c — `FlowType` enum pre-declaration

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

Total: 7 values (3 existing + 4 new). Values are unused at prep-commit time.

**Acceptance gate items for 0c:**
- [ ] `FlowType` enum has all 7 values on `main` before any branch is cut
- [ ] `FlowEngine` compiles and all M1 tests pass (new values unused but present)

---

### 0d — STOP-during-chain semantics in `OptOutHandler`

**Fix:** Add explicit comment to `OptOutHandler.handleStop()`:

```java
// STOP always clears the entire session regardless of flowType or previousFlow.
// This applies to all chained flows (TOMORROW_PLAN, REFERRAL, LOAN, ACCOUNT_LINK).
// START returns the user to the ACTIVE scheduler pool; it does NOT resume
// a previously interrupted chain.
sessionService.clearSession(platform, userId);
```

**Unit tests required in prep commit (5 total):**
1. STOP while `flowType=TOMORROW_PLAN` → session cleared, `status=OPTED_OUT`, no resume
2. STOP while `flowType=REFERRAL` → same
3. STOP while `flowType=LOAN` → same
4. STOP while `flowType=ACCOUNT_LINK` → same
5. START after STOP with `previousFlow` set → session does NOT restore previous flow

**Acceptance gate items for 0d:**
- [ ] STOP-chain unit tests (5 cases above) pass
- [ ] No milestone branch adds a guard on STOP that checks `flowType` or `previousFlow`

---

### Prep Commit: Full Acceptance Gate Summary

All of the following must be green on `main` before any M2/M3/M4 branch is cut:

- [ ] 0a: `PostDailySummaryAction` interface with Javadoc + explicit `for`-`break` refactor in `DailyEarningsFlow` + `DailyEarningsFlowTest.java` (M1 path) + 6 unit tests (5 Mockito + 1 Spring slice)
- [ ] 0b: `SessionState.previousFlow` + `@JsonIgnoreProperties(ignoreUnknown=true)` + forward-compat test + rollback-behavior test + `SessionService` comment
- [ ] 0c: `FlowType` enum has all 7 values; M1 tests pass
- [ ] 0d: STOP-chain semantics in `OptOutHandler`; 5 unit tests pass
- [ ] `mvn test` green on `main` with all prep changes
- [ ] Prep commit reviewed and approved by team lead

**M1 verification gate:**
- [ ] All 11 M1 verification checks from the MVP plan pass (docker boot, zone seed, onboarding, scheduler delivery, opt-out, events, replay, outbound, admin auth, indexes)

---

## M2 — Intelligence Slice (parallel with M3, M4)

### Branch: `feature/m2-intelligence`
### Base: `main` after prep commit

**Deliverables:**

1. **HotnessScoreService** — formula: `(AvgEarnings × Demand) / (Supply + 1)`
   - Supply resolution: `zone.estimatedSupply ?? historicalCount(zone, timeSlot, last7d) ?? 0`
   - Tie-breaking: `score DESC, recommendationPriority ASC`
   - **Redis cache**: `hotness:{zone}:{timeSlot}:{date}` with **1h TTL** (set via `redisTemplate.opsForValue().set(key, value, 1, TimeUnit.HOURS)`)

2. **Cache invalidation on zone write** — `ZoneController.upsert()` (and any future zone-mutating endpoint) calls the following after a successful MongoDB write:

   ```java
   // Cache invalidation — non-fatal if it fails
   try {
       Set<String> keys = redisTemplate.keys("hotness:" + zone.getId() + ":*");
       if (keys != null && !keys.isEmpty()) {
           redisTemplate.delete(keys);
       }
   } catch (Exception e) {
       log.warn("Hotness cache invalidation failed for zone {}; stale scores may persist up to 1h", zone.getId(), e);
   }
   ```

   Uses the existing `RedisTemplate<String,String>` from `RedisConfig.java`. `redisTemplate.keys(pattern)` executes Redis `KEYS pattern`, which IS wildcard-aware (unlike `redisTemplate.delete(key)` which requires an exact key). Wrapped in try/catch so zone write API returns 200 even if Redis is temporarily unavailable.

   > **MVP scale note**: `KEYS pattern` blocks Redis while scanning. For the expected MVP dataset (< 100 zone-timeSlot-date combinations), the block duration is sub-millisecond. Replace with SCAN-cursor approach when Redis key count exceeds ~10k or when Redis is shared with latency-sensitive services.

3. **`TomorrowPlanAction implements PostDailySummaryAction`**
   - `@Order(10)` — M2 fires before M3 (M3=20)
   - Triggers after daily summary when user replies "Yes"
   - Sets `previousFlow = "DAILY_EARNINGS"`, `flowType = TOMORROW_PLAN`
   - `tomorrow_plan_requested` event written
   - Exception safety: wrap all DB/Redis calls; return `Optional.empty()` on failure

4. **Flow 4: Peak Nudge** — 30min before highest-scored slot; `ACTIVE` users with `lastActiveAt` within 7 days only; `peak_nudge_sent` event

5. ~~`PATCH /admin/features`~~ → **MOVED TO M4** (supersedes MVP plan deliverable #5; authoritative source: this document and the supersession banner in the MVP build plan)

### M2 Acceptance Criteria
- [ ] `HotnessScoreService` implements formula; supply fallback chain verified by unit test
- [ ] Tie-breaking: `score DESC, recommendationPriority ASC` — deterministic unit test for equal scores
- [ ] Score results cached at `hotness:{zone}:{timeSlot}:{date}` with 1h TTL
- [ ] `POST /admin/zones` zone upsert calls `redisTemplate.keys("hotness:{zoneId}:*")` + `redisTemplate.delete(keys)`; wrapped in try/catch; API returns 200 even if cache clear throws
- [ ] Cache invalidation unit test: after zone write, matching `hotness:*` keys are absent; non-matching keys are unaffected
- [ ] `TomorrowPlanAction` annotated `@Order(10)`, implements `PostDailySummaryAction`; fires on "Yes"; `tomorrow_plan_requested` event; session has `flowType=TOMORROW_PLAN`, `previousFlow=DAILY_EARNINGS`
- [ ] `TomorrowPlanAction.apply()` returns `Optional.empty()` (no throw) on downstream failure
- [ ] Peak nudge not sent to `OPTED_OUT` or `lastActiveAt` > 7 days users
- [ ] M2 branch does NOT modify `DailyEarningsFlow.handleZone()`
- [ ] M2 branch does NOT add entries to `FlowType` enum
- [ ] M2 branch does NOT implement or reference `PATCH /admin/features`

### M2 Dependency Surface
- **Reads**: `EarningsRecord`, `ZoneHeuristic` (M1); `FlowType.TOMORROW_PLAN` (prep commit); `RedisTemplate<String,String>` (already in `RedisConfig.java`)
- **Adds**: `TomorrowPlanAction` (new file), `HotnessScoreService` (new file), `PeakNudgeScheduler` (new file), cache-invalidation call in `ZoneController.upsert()`
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
   - **No v1.5 follow-up**: final MVP weekly report; hotness-scored insight is post-MVP, unscoped
3. **`ReferralPromptAction implements PostDailySummaryAction`** — `@Order(20)`; fires after daily summary when `count(EarningsRecord for user) >= 3`; `previousFlow="DAILY_EARNINGS"`, `flowType=REFERRAL`; `referral_prompt_sent` event; exception safety: `Optional.empty()` on failure
4. **Referral code lazy migration** — `ReferralService.ensureReferralCode(userId)` called from two sites:
   - `BotMessageController` — on every inbound message, before routing
   - `FlowEngine.handle()` — at the top of every flow dispatch, before state transition

   Both sites are idempotent (no-op if code already exists). Covers all pre-M3 user entry paths.
5. **Admin additions (M3)** — `GET /admin/events/summary?from=&to=`, `GET /admin/earnings/export?from=&to=` CSV

### M3 Acceptance Criteria
- [ ] Inactive nudge skips `OPTED_OUT` users and users with a record within 2 days
- [ ] Weekly report: 5 data points (total, fuel, profit, best day, top zone) from raw `EarningsRecord` aggregation — `HotnessScoreService` not imported
- [ ] Weekly report: users with <2 records receive reminder only
- [ ] `ReferralPromptAction` annotated `@Order(20)`, implements `PostDailySummaryAction`; fires when count ≥ 3; sets `previousFlow=DAILY_EARNINGS`, `flowType=REFERRAL`; event written
- [ ] `ReferralPromptAction.apply()` returns `Optional.empty()` on failure
- [ ] `ReferralService.ensureReferralCode(userId)` called from both `BotMessageController` and `FlowEngine.handle()`; idempotent
- [ ] Referral code unique per user; `rewardAmount: null`; no reward messaging
- [ ] `GET /admin/events/summary` returns correct funnel counts
- [ ] M3 branch does NOT modify `DailyEarningsFlow.handleZone()`
- [ ] M3 branch does NOT add `FlowType` enum entries
- [ ] M3 branch does NOT import `HotnessScoreService`

### M3 Dependency Surface
- **Reads**: `EarningsRecord`, `User` (M1); `SessionState.previousFlow` (prep commit); `FlowType.REFERRAL` (prep commit)
- **Adds**: `ReferralPromptAction`, `InactiveNudgeScheduler`, `WeeklyReportScheduler`, admin event/export controllers (all new files)
- **Does NOT touch**: `DailyEarningsFlow.handleZone()`, `FlowType.java`, `HotnessScoreService`, `app_config`

---

## M4 — Advanced Slice (parallel with M2, M3)

### Branch: `feature/m4-advanced`
### Base: `main` after prep commit
### Merge first (before M2 and M3) — fully non-overlapping

**Deliverables:**

1. **Flow 6: Loan Entry Point** — eligibility: ≥5 records AND ≥3 in last 7d AND avg gross ≥₹700; Redis `loan-offered:{userId}` (30d TTL); `loanEnabled` feature-flag gated
2. **Account linking** — 6-digit code; Redis `link-token:{code}` (10min TTL); earnings merge to primary; secondary `MERGED`
3. **`PATCH /admin/features`** — toggles `loanEnabled`, `referralRewardEnabled` in `app_config`; `X-Admin-Key` auth
4. **`app_config` bootstrap** — `@EventListener(ApplicationReadyEvent.class)` upserts defaults on startup if absent. Bean guarded by `@ConditionalOnProperty` so tests can opt out:

   ```java
   @Component
   @ConditionalOnProperty(name = "app.bootstrap.enabled", havingValue = "true", matchIfMissing = true)
   public class AppConfigBootstrap {
       @EventListener(ApplicationReadyEvent.class)
       public void bootstrapDefaults() {
           // idempotent upsert: {loanEnabled: false, referralRewardEnabled: false}
       }
   }
   ```

   `application-test.yml` sets `app.bootstrap.enabled=false`. `matchIfMissing=true` means production requires no property.

5. **Datadog full wiring** — JMX + APM; log forwarding; scheduler health dashboard
6. **Hardening** — load test M1 flows; MongoDB index review; idempotency record accumulation review

### M4 Acceptance Criteria
- [ ] `app_config` document upserted with defaults on startup; verified by `docker-compose down -v && up`
- [ ] `AppConfigBootstrap` guarded by `@ConditionalOnProperty`; `GigsarathiApplicationTests` passes with `app.bootstrap.enabled=false` (no MongoDB write during test context load)
- [ ] `PATCH /admin/features` toggles flags; returns 401 without valid `X-Admin-Key`
- [ ] Loan prompt shown only to eligible users; `loanEnabled=true` required; 30d Redis dedup
- [ ] `loan_offer_shown`, `loan_offer_clicked` events written; no approval-implied language
- [ ] 6-digit link code expires 10min; after linking: secondary `status=MERGED`; earnings re-pointed
- [ ] Datadog dashboard shows scheduler success/failure counts
- [ ] M4 branch does NOT add `FlowType` enum entries (`LOAN`, `ACCOUNT_LINK` pre-declared in prep commit)

### M4 Dependency Surface
- **Reads**: `EarningsRecord`, `User` (M1); `FlowType.LOAN`, `FlowType.ACCOUNT_LINK` (prep commit)
- **Creates**: `app_config` collection; loan flow; account linking flow; `PATCH /admin/features` controller; Datadog config
- **Does NOT touch**: `DailyEarningsFlow`, `HotnessScoreService`, `PostDailySummaryAction`, `FlowType.java`

---

## Conflict Hotspot Table

| File | Risk (before prep) | Risk (after prep) | Mitigation |
|------|-------------------|-------------------|-----------|
| `DailyEarningsFlow.java` | HIGH | NONE | Step 0a: `for`-`break` loop refactor; no branch touches it |
| `FlowType.java` | HIGH | NONE | Step 0c: all 7 values pre-declared |
| `SessionState.java` | MEDIUM | NONE | Step 0b: `previousFlow` + `@JsonIgnoreProperties`; no branch touches it |
| `OptOutHandler.java` | MEDIUM | NONE | Step 0d: STOP comment; no branch adds guards |
| `ZoneController.java` | LOW | LOW | M2 adds cache-invalidation call in try/catch; no other branch touches it |
| `AppConfigBootstrap` (M4 new) | NONE | LOW | `@ConditionalOnProperty` isolates from test context |
| `GigsarathiApplicationTests.java` | LOW | LOW | M4 bootstrap disabled via `app.bootstrap.enabled=false`; M2 Redis-dependent beans need `@MockBean` in CI |
| `application.yml` | LOW | LOW | M2 adds `hotness.cache.ttl`; M4 adds `datadog.*`; no overlap |

---

## Parallel Execution Protocol

### Phase 1: Prep (sequential — 1 day, reviewed by team lead)
```
main ──[ 0a+0b+0c+0d + DailyEarningsFlowTest + mvn test green + 11 M1 gates ]──►
```

### Phase 2: Parallel development (concurrent — 6–8 days)
```
main (post-prep) ──────────────────────────────────────────────────────►
       └─── feature/m2-intelligence ──────────────────────────────────►
       └─── feature/m3-engagement ────────────────────────────────────►
       └─── feature/m4-advanced ──────────────────────────────────────►
```

### Phase 3: Merge (sequential — order locked)
```
1. Merge feature/m4-advanced → main
2. Merge feature/m2-intelligence → main
3. Rebase feature/m3-engagement onto main, then merge
```

**CI enforcement**: PR template must state "Do not merge M3 until M4 and M2 are on main." Manual sign-off if CI cannot enforce merge order.

---

## M2 Hardening Backlog

1. WhatsApp HMAC webhook signature verification
2. WebClient HTTP timeouts (5s connect, 10s response)
3. `@RestControllerAdvice` global exception handler
4. PII redaction in send logs (`to` field in `WhatsAppMessageSender`)

---

## Full Acceptance Gate for Parallel Launch Decision

**Prep commit gates:**
- [ ] `PostDailySummaryAction` interface with Javadoc (ordering, STOP, composition, exception-safety)
- [ ] `DailyEarningsFlow` explicit `for`-`break` loop; `sendMessage` before chaining; `DailyEarningsFlowTest.java` passing
- [ ] 6 unit tests pass (5 Mockito + 1 Spring `@Order` slice test)
- [ ] `SessionState.previousFlow` + `@JsonIgnoreProperties(ignoreUnknown=true)` + forward-compat test + rollback-behavior test + `SessionService` comment
- [ ] `FlowType` enum has all 7 values
- [ ] 5 STOP-chain tests pass (TOMORROW_PLAN, REFERRAL, LOAN, ACCOUNT_LINK, START-no-resume)
- [ ] `mvn test` green; prep commit team-lead approved

**M1 verification gate:**
- [ ] All 11 M1 checks pass (docker boot, zone seed, onboarding, scheduler delivery, opt-out, events, replay, outbound, admin auth, indexes)

**Product Owner sign-off (written, not verbal):**
- [ ] **PO acknowledgement**: users eligible for BOTH tomorrow plan (M2 `@Order(10)`) AND referral (M3 `@Order(20)`) receive ONLY tomorrow plan in a single daily-summary chain. Referral prompt is silently skipped for that interaction. If both should fire in sequence, the `PostDailySummaryAction` interface contract (single winner) must change before M2 or M3 code is written.

**Scope acknowledgements (written):**
- [ ] M2: `PATCH /admin/features` is NOT M2 scope; no `FlowType` enum edits on branch
- [ ] M3: no `HotnessScoreService` import; no `FlowType` enum edits; no v1.5 implied
- [ ] M4: `PATCH /admin/features` IS M4 scope; owns `app_config`; no `FlowType` enum edits
- [ ] All three: merge order M4 → M2 → M3 acknowledged

---

## Pre-mortem Scenarios

**Scenario 1: FlowType enum conflict** → Mitigated by Step 0c. All 7 values pre-declared on `main`; no branch touches `FlowType.java`.

**Scenario 2: Action ordering → M3 referral never fires** → Mitigated by `@Order(10)`/`@Order(20)` Javadoc contract + Spring slice test (test 6) that catches forgotten `@Order` before branching.

**Scenario 3: v1.5 follow-up never lands** → Non-issue. v1.5 is explicitly DROPPED. Weekly report with raw aggregation is the final MVP deliverable.

**Scenario 4: M4 `@EventListener` corrupts test context** → `@ConditionalOnProperty` + `app.bootstrap.enabled=false` in test properties. `GigsarathiApplicationTests` passes without MongoDB write.

**Scenario 5: Cache invalidation takes Redis down** → try/catch around `redisTemplate.keys()`+`delete()`. Zone write API returns 200; stale hotness scores persist up to 1h TTL; logged as WARN.
