# Gigsarathi M2/M3/M4 Execution Plan — Codex-Gated Parallel Build

## Plan Status
- Source of truth (architecture): `.omc/plans/gigsarathi-m234-parallel-plan-v6.md` (Critic v3 ITERATE → v4 APPROVE)
- This document: EXECUTION layer only — operationalizes v6 with Codex CLI as the review/quality engine
- Mode: ralplan consensus, SHORT (no `--deliberate` flag) — but pre-mortem and ADR included due to multi-branch risk
- **Consensus status: APPROVED** — Architect v9 PASS + Critic v2 APPROVE (2026-05-05); ready for execution

---

## Scope of This Plan

This is **not** a redesign. v6 is the architectural source of truth and is treated as immutable here. This plan answers exactly three questions:

1. How does Codex CLI run as the review engine for the prep commit and each branch?
2. What does Codex check, when, and with what prompts?
3. Where does Codex sit in the merge gate flow (M4 → M2 → M3)?

If anything in this plan conflicts with v6, v6 wins.

---

## Phase 0 — Codex CI Wiring (before any code is written)

### 0.1 Codex invocation pattern (canonical form)

All Codex review calls in this plan use a single shape so reviewers and CI behave identically:

```bash
codex exec --model gpt-5.3-codex \
  --sandbox read-only \
  --skip-git-repo-check \
  "$(cat <<'PROMPT'
<PROMPT_BODY>
PROMPT
)" 2>&1 | tee .omc/logs/codex-reviews/<gate>-<file>.log
```

- `codex exec` is non-interactive (CI-safe). Interactive `codex` is reserved for developer reproduction of a failure.
- `--sandbox read-only` is mandatory: Codex must never edit files during review. Reviews are advisory artifacts; humans/Claude apply fixes.
- Review logs are written to `.omc/logs/codex-reviews/` so the merge gate can grep them. Directory is created in Phase 0.2.
- File contents are passed inline in the prompt (cat'd into a heredoc inside the prompt body). Codex's read-only sandbox can also resolve relative paths, but inline payload makes the review reproducible from the log alone.

### 0.2 Repository scaffolding (one-time, before prep commit)

Create on a throwaway branch `chore/codex-review-tooling` that lands on `main` BEFORE the prep commit (Step 0a–0d). Files:

| Path | Purpose |
|------|---------|
| `scripts/codex-review.sh` | Wrapper that takes a prompt template name + file path and runs Codex non-interactively |
| `scripts/codex-prompts/arch-consistency.md` | Prompt template (a) — interface/architecture review |
| `scripts/codex-prompts/test-coverage.md` | Prompt template (b) — test coverage review |
| `scripts/codex-prompts/acceptance-criteria.md` | Prompt template (c) — AC verification |
| `scripts/codex-prompts/m2-hotness.md` | M2-specific risk prompt |
| `scripts/codex-prompts/m3-referral.md` | M3-specific risk prompt |
| `scripts/codex-prompts/m4-bootstrap.md` | M4-specific risk prompt |
| `scripts/codex-prompts/merge-conflict-scan.md` | Cross-branch conflict detection prompt |
| `.github/workflows/codex-review.yml` | GitHub Actions workflow that runs Codex on PRs targeting `main` |
| `.omc/logs/codex-reviews/.gitkeep` | Log directory tracked in git (empty) |

`scripts/codex-review.sh` (signature):

```
./scripts/codex-review.sh <template> <file-or-glob> [<gate-label>]
# example:
./scripts/codex-review.sh arch-consistency \
  gigsarathi-core/src/main/java/com/gigsarathi/flow/PostDailySummaryAction.java \
  prep-0a
```

Wrapper responsibilities (fail-closed contract):
- Read the named template from `scripts/codex-prompts/<template>.md`
- Inline the target file(s) into the prompt under `# FILE: <path>` headings
- Run `codex exec --model gpt-5.3-codex --sandbox read-only --skip-git-repo-check`; **capture `${PIPESTATUS[0]}`** so the Codex exit code is not masked by `tee`'s exit 0
- Tee output to `.omc/logs/codex-reviews/<gate-label>-<basename>.log`
- If Codex exits nonzero → emit "ERROR: codex exec failed (exit $codex_exit)" and **exit 2** (infra failure — distinct from review failure)
- Extract the last non-empty line from the log. If it is NOT exactly `VERDICT: PASS` or `VERDICT: FAIL` → emit "ERROR: no valid VERDICT in review output" and **exit 2**
- If last line is `VERDICT: FAIL` → **exit 1** (review failure — merge gate hard blocks)
- If last line is `VERDICT: PASS` → **exit 0**

Concrete wrapper skeleton (for reference in script creation):

```bash
#!/usr/bin/env bash
set -euo pipefail

TEMPLATE="$1"; FILE="$2"; LABEL="${3:-review}"
LOG_DIR=".omc/logs/codex-reviews"
mkdir -p "$LOG_DIR"
# Label+template uniquely identifies every invocation; avoids broken basename when $FILE is multi-path
LOG="$LOG_DIR/${LABEL}-${TEMPLATE}.log"

# $FILE may be a space-separated list for multi-file reviews (e.g., test-coverage template).
# Invariant: all file paths in this repo are Java package paths (no embedded spaces).
# Each path gets its own # FILE: heading block inlined into the prompt.
FILE_PAYLOAD=""
for f in $FILE; do
  [[ -f "$f" ]] || { echo "ERROR: file not found: $f" >&2; exit 2; }
  FILE_PAYLOAD+=$'\n\n'"# FILE: ${f}"$'\n'"$(cat "$f")"
done
PROMPT_BODY="$(cat "scripts/codex-prompts/${TEMPLATE}.md")${FILE_PAYLOAD}"

# set +e disables exit-on-error so the pipeline can fail without killing the script.
# This is the correct pattern for capturing ${PIPESTATUS[0]} under set -euo pipefail:
# `|| true` must NOT be used because it replaces PIPESTATUS with true's exit codes (all 0).
set +e
codex exec --model gpt-5.3-codex --sandbox read-only --skip-git-repo-check "$PROMPT_BODY" \
  2>&1 | tee "$LOG"
codex_exit="${PIPESTATUS[0]}"
set -e

if [[ "$codex_exit" -ne 0 ]]; then
  echo "ERROR: codex exec failed (exit ${codex_exit})" >&2; exit 2
fi

last_line="$(grep -v '^[[:space:]]*$' "$LOG" | tail -1 | tr -d '\r')"
# tr -d '\r' strips Windows-style CRLF that Codex may emit; without it the
# string comparison below silently mismatches ("VERDICT: PASS\r" != "VERDICT: PASS").
if [[ "$last_line" != "VERDICT: PASS" && "$last_line" != "VERDICT: FAIL" ]]; then
  echo "ERROR: no valid VERDICT in review output — last line: ${last_line}" >&2; exit 2
fi
if [[ "$last_line" == "VERDICT: FAIL" ]]; then exit 1; fi
exit 0
```

Exit-code contract: 0 = VERDICT PASS, 1 = VERDICT FAIL (blocks merge), 2 = infra/malformed (also blocks; on-call distinguishes from review failure via exit code).

### 0.3 Prompt templates

#### Template (a) — `arch-consistency.md` (architecture / interface consistency)

```
You are reviewing Java Spring Boot code for the Gigsarathi M2/M3/M4 parallel
build. Authoritative architecture spec is .omc/plans/gigsarathi-m234-parallel-plan-v6.md.
You must NOT propose architectural changes. Verify ONLY that the file under review
conforms to the v6 spec. For each finding emit a bullet under one of:

- BLOCKING (violates a "MUST" / "MUST NOT" / acceptance criterion in v6)
- ADVISORY (style/clarity, not a v6 violation)

Specifically check:
1. Interface signatures match v6 verbatim (PostDailySummaryAction, SessionState fields, FlowType values)
2. Spring annotations: @Component, @Order(N) where required, @ConditionalOnProperty where required
3. Forbidden imports per v6 (e.g., M3 must NOT import HotnessScoreService)
4. Forbidden file edits per the v6 conflict hotspot table
5. Try/catch placement around external calls (DB, Redis, downstream)

End with a single line: "VERDICT: PASS" or "VERDICT: FAIL".

# FILE: <path>
<file contents inlined here>
```

#### Template (b) — `test-coverage.md`

```
You are reviewing test coverage for the Gigsarathi M2/M3/M4 build against
.omc/plans/gigsarathi-m234-parallel-plan-v6.md. Confirm the test file(s) under
review cover EVERY acceptance-criteria checkbox listed for the relevant gate.
Cross-reference each AC to a specific @Test method. List uncovered ACs as
BLOCKING. List flaky-prone patterns (Thread.sleep, real network, real Redis
without Testcontainers, time-sensitive without Clock injection) as ADVISORY.

Output format:
- AC -> Test method (covered) | (UNCOVERED — BLOCKING)
- Flakiness risks: ...

End with: "VERDICT: PASS" or "VERDICT: FAIL".

# GATE: <gate label, e.g. prep-0a, m2, m3, m4>
# FILES:
<inlined test files>
```

#### Template (c) — `acceptance-criteria.md`

Note: the wrapper passes only the git diff as the inlined file. The AC checklist is NOT a
second file argument. Instead, the template instructs Codex to read v6 directly — this is
safe because Codex runs with `--sandbox read-only` which gives it local file access (same
mechanism used by `arch-consistency.md`).

```
You are verifying acceptance criteria from
.omc/plans/gigsarathi-m234-parallel-plan-v6.md against the diff under review.

Step 1: Read .omc/plans/gigsarathi-m234-parallel-plan-v6.md. Identify which milestone
gate (prep-commit, M2, M3, or M4) this diff belongs to by examining the filenames in
the diff header. Read the complete AC checklist for that gate from v6.

Step 2: For each AC line, mark MET / NOT MET / NOT APPLICABLE with a one-line
justification grounded ONLY in the diff. Do not infer behavior — only what the diff
demonstrably contains counts as MET.

End with: "VERDICT: PASS" (all MET or NOT APPLICABLE) or "VERDICT: FAIL".

# DIFF:
<inlined diff>
```

Invocation shape (diff file only — no checklist file argument):
```bash
git diff origin/main... > /tmp/<gate>-diff.patch
./scripts/codex-review.sh acceptance-criteria /tmp/<gate>-diff.patch <gate>-ac
```

Java-specific note for all templates: When inlining files, include both the
target `.java` file and any companion `package-info.java` / `application.yml`
fragment Codex needs to evaluate the annotation/property contract.

### 0.4 GitHub Actions workflow (`.github/workflows/codex-review.yml`)

Trigger: `pull_request` opened/synchronized against `main`.

Steps (sequential):
1. Detect **head branch** (`${{ github.head_ref }}`): `chore/codex-review-tooling`, `chore/prep-commit`, `feature/m2-intelligence`, `feature/m3-engagement`, or `feature/m4-advanced`. (Base branch is always `main` for all PRs — do NOT use `github.base_ref` for template selection.) The `chore/codex-review-tooling` branch is the Phase 0 scaffolding branch; its presence in the matrix allows the Phase 0.7 FAIL→PASS validation to be driven by the PR trigger end-to-end.
2. Run `mvn -B -pl gigsarathi-core test` first — Codex never reviews red builds.
3. Compute changed files: `git diff --name-only origin/main... | grep -E '\.(java|yml|yaml)$'` (includes `application-test.yml` and Datadog configs, not only `.java`).
4. For each changed file, run the appropriate Codex template (matrix selection by head branch + path — table in 0.5). **M4 exception**: always run `m4-bootstrap` on both `AppConfigBootstrap.java` AND `application-test.yml` regardless of whether those files appear in the changed-file list — `application-test.yml` must be reviewed even if it was not modified by the M4 PR, because its `app.bootstrap.enabled=false` guard is a hard AC requirement that could be absent from the diff but still missing from the file.
5. Upload `.omc/logs/codex-reviews/` as a workflow artifact.
6. Fail the job if any `scripts/codex-review.sh` call exits nonzero (exit 1 = VERDICT FAIL, exit 2 = infra error — both are CI failures).

Codex CLI is provisioned in CI via the official install one-liner; auth via `OPENAI_API_KEY` repo secret. (Wrapper script must `command -v codex` and exit 2 with a clear message if Codex is not on PATH — prevents silent skips.)

### 0.5 Branch → template selection matrix

| Branch | Templates run on changed files |
|--------|--------------------------------|
| `chore/codex-review-tooling` | (a) on a designated validation stub `.java` file (for FAIL→PASS round-trip proof per Section 0.7); no test or AC templates required on this branch |
| `chore/prep-commit` | (a) on every changed `.java`; (b) on `*Test.java`; (c) on the prep-commit AC checklist |
| `feature/m2-intelligence` | (a) on every changed `.java`; (b) on `*Test.java`; m2-hotness on `HotnessScoreService.java`, `ZoneController.java`, `TomorrowPlanAction.java`; (c) on M2 AC |
| `feature/m3-engagement` | (a) on every changed `.java`; (b) on `*Test.java`; m3-referral on `ReferralPromptAction.java`, `ReferralService.java`, `MessageController.java`, `FlowEngine.java`; (c) on M3 AC |
| `feature/m4-advanced` | (a) on every changed `.java`; (b) on `*Test.java`; m4-bootstrap on `AppConfigBootstrap.java` AND `application-test.yml` (non-Java, always included for M4 regardless of extension filter); (c) on M4 AC |
| Post-merge to `main` (separate `push` trigger, not `pull_request`) | merge-conflict-scan on the conflict-hotspot files from v6 — triggered by the `push` event on `main` after each milestone merge, not by the PR workflow |

### 0.6 Local pre-PR checklist (developer-facing)

Developers run before pushing:

```bash
# 1. Build & test
mvn -B -pl gigsarathi-core test

# 2. Codex arch review on every changed Java file
git diff --name-only origin/main... | grep '\.java$' | while read f; do
  ./scripts/codex-review.sh arch-consistency "$f" "local-pre-pr"
done

# 3. Codex test-coverage on test files
git diff --name-only origin/main... | grep 'Test\.java$' | while read f; do
  ./scripts/codex-review.sh test-coverage "$f" "local-pre-pr"
done

# 4. AC verification
./scripts/codex-review.sh acceptance-criteria \
  $(git diff origin/main... > /tmp/diff && echo /tmp/diff) "local-pre-pr-ac"
```

Logs land in `.omc/logs/codex-reviews/`; any `VERDICT: FAIL` blocks the PR.

### 0.7 Phase 0 done signal (CI proof gate)

**Execution model**: Section 0.2 defines `chore/codex-review-tooling` as the branch that adds all scripts and the workflow file and merges to `main` first. Section 0.7 uses that **same branch** as the validation vehicle — there is no separate throwaway branch. The sequence is:

1. Create `chore/codex-review-tooling` with all files listed in Section 0.2.
2. Open a PR against `main`.
3. Run the FAIL→PASS round-trip (see below) on this branch.
4. Verify all three done-signal conditions below.
5. Merge `chore/codex-review-tooling` to `main`.

Only after step 5 may Phase 1 (`chore/prep-commit`) begin. There is no circular dependency: `chore/codex-review-tooling` is not Phase 1's deliverable — it is Phase 0's scaffolding branch.

Phase 0 is complete only when **all three** of the following are true:

1. **Tooling PR green on CI**: The `chore/codex-review-tooling` PR must show at least one successful `codex-review.yml` workflow run with at least one live Codex invocation recorded in CI artifacts (step 5 upload). A green mvn build alone is insufficient — Codex must actually execute and produce a log.
2. **At least one forced FAIL → PASS round-trip**: Add a commit to `chore/codex-review-tooling` that introduces a syntactically valid but architecturally violating `.java` stub (e.g., an interface method with the wrong signature — compile-valid but Codex-verdict-failing). Confirm the CI job exits 1 (`VERDICT: FAIL`). Then add a revert commit so the stub is removed, confirm the CI job exits 0. **Important**: the FAIL stub must produce a Codex `VERDICT: FAIL` result, not a compile error (which would fail the mvn step in CI step 2 before Codex runs and would not prove gate behavior). Both rounds are captured from CI artifact logs as evidence.
3. **`OPENAI_API_KEY` secret confirmed**: A missing key causes exit 2 (infra error), not exit 1 (VERDICT FAIL). If the CI run in condition 2 exits 1 on the bad stub, the API key is implicitly confirmed.

Only after all three conditions are satisfied may Phase 1 begin.

**Pre-merge stub-absent check**: Before merging `chore/codex-review-tooling` to `main`, verify the injected FAIL stub is fully removed: `git diff origin/main... | grep -c 'CODEX-FAIL-STUB'` must return 0. Add the string `// CODEX-FAIL-STUB` as a marker comment in the injected bad stub so this grep is unambiguous. If the count is nonzero, the revert commit was incomplete — do not merge until clean.

---

## Phase 1 — Prep Commit Execution + Codex Review

### 1.1 Implementation order (must be a single PR `chore/prep-commit` against `main`)

Sequence inside the commit (one PR, atomic):

1. **0a — `PostDailySummaryAction` interface + `DailyEarningsFlow` refactor**
   - Add `PostDailySummaryAction.java` with the v6 Javadoc verbatim
   - Refactor `DailyEarningsFlow.handleZone()` tail to the explicit `for`-`break` loop with `sendMessage` + `eventService.emit` BEFORE chaining
   - Add `DailyEarningsFlowTest.java` (M1 path)
   - Add `PostDailySummaryActionTest.java` (Mockito tests 1–5)
   - Add `PostDailySummaryActionSpringOrderTest.java` (Spring slice test 6)

2. **0b — `SessionState.previousFlow` + `@JsonIgnoreProperties`**
   - Add field `private String previousFlow;`
   - Add class-level `@JsonIgnoreProperties(ignoreUnknown = true)`
   - Add comment in `SessionService.getSession()` catch block per v6 §0b
   - Tests: forward-compat, rollback-behavior, roundtrip integration via `SessionService`

3. **0c — `FlowType` enum**
   - Add `TOMORROW_PLAN`, `REFERRAL`, `LOAN`, `ACCOUNT_LINK` (total 7 values)
   - Run M1 test suite — all green with new values unused

4. **0d — `OptOutHandler` STOP-chain semantics**
   - Add the v6 comment block above `sessionService.clearSession(...)`
   - Add 5 unit tests (TOMORROW_PLAN, REFERRAL, LOAN, ACCOUNT_LINK, START-no-resume)

5. **MVP plan banner update**
   - Edit `.omc/plans/gigsarathi-mvp-build-plan.md` supersession banner to point to v6
   - Cross out M2 deliverable #5 (moved to M4)

Order is sequential within the commit because 0a depends on the interface existing before the flow refactor compiles, and 0d's tests depend on `FlowType` values from 0c. They land as a single squashed commit so `main` is never half-prepped.

### 1.2 Codex review gate on the prep commit

Run on the prep PR before merge to `main`:

| Check | Template | Target | Pass criterion |
|-------|----------|--------|----------------|
| Interface contract | `arch-consistency` | `PostDailySummaryAction.java` | Javadoc contains all four sections (ORDERING, STOP, COMPOSITION, EXCEPTION SAFETY); single-method signature matches v6 verbatim |
| Flow refactor | `arch-consistency` | `DailyEarningsFlow.java` | Explicit `for`-`break` (not stream); `sendMessage` and `eventService.emit` precede chaining loop; constructor takes `List<PostDailySummaryAction>` |
| SessionState | `arch-consistency` | `SessionState.java` | `previousFlow` field present and nullable; `@JsonIgnoreProperties(ignoreUnknown=true)` at class level |
| FlowType | `arch-consistency` | `FlowType.java` | Exactly 7 values in v6 order |
| OptOutHandler | `arch-consistency` | `OptOutHandler.java` | Comment present verbatim per v6 §0d; no guard on `flowType` or `previousFlow` |
| Test coverage | `test-coverage` | All 4 new test files | Every prep-commit AC bullet maps to a `@Test` method; Spring slice test asserts `index 0 == @Order(10)` |
| AC verification | `acceptance-criteria` | The prep-commit checklist + full diff | All AC items MET or NOT APPLICABLE |

Specific Codex invocations:

```bash
./scripts/codex-review.sh arch-consistency \
  gigsarathi-core/src/main/java/com/gigsarathi/flow/PostDailySummaryAction.java prep-0a-iface

./scripts/codex-review.sh arch-consistency \
  gigsarathi-core/src/main/java/com/gigsarathi/flow/DailyEarningsFlow.java prep-0a-flow

./scripts/codex-review.sh arch-consistency \
  gigsarathi-core/src/main/java/com/gigsarathi/session/SessionState.java prep-0b

./scripts/codex-review.sh arch-consistency \
  gigsarathi-core/src/main/java/com/gigsarathi/flow/FlowType.java prep-0c

./scripts/codex-review.sh arch-consistency \
  gigsarathi-core/src/main/java/com/gigsarathi/flow/OptOutHandler.java prep-0d

./scripts/codex-review.sh test-coverage \
  "gigsarathi-core/src/test/java/com/gigsarathi/flow/PostDailySummaryActionTest.java \
   gigsarathi-core/src/test/java/com/gigsarathi/flow/PostDailySummaryActionSpringOrderTest.java \
   gigsarathi-core/src/test/java/com/gigsarathi/flow/DailyEarningsFlowTest.java" \
   prep-tests

git diff origin/main... > /tmp/prep-diff.patch
./scripts/codex-review.sh acceptance-criteria /tmp/prep-diff.patch prep-ac
```

### 1.3 Acceptance verified by Codex (from v6 §"Prep Commit: Full Acceptance Gate Summary")

Codex `acceptance-criteria` template receives the entire v6 prep-commit checklist and must mark each item MET. Failure of any single item is `VERDICT: FAIL` and blocks merge:

- 0a interface + Javadoc + refactor + 6 unit tests + `DailyEarningsFlowTest`
- 0b field + annotation + 3 tests + comment in `SessionService`
- 0c enum has all 7 values
- 0d STOP-chain comment + 5 tests
- `mvn test` green
- MVP plan banner updated

Human team-lead approval still required after Codex `VERDICT: PASS` (Codex is gate, not the final approver).

---

## Phase 2 — Parallel Branch Execution (M2 ∥ M3 ∥ M4)

Cut all three branches from `main` AFTER prep commit is merged and `mvn test` is green on `main`. Each branch runs Codex independently; no cross-branch coordination needed during development.

### 2.1 M2 — `feature/m2-intelligence`

#### Per-deliverable Codex review schedule

| Deliverable (v6 §M2) | When Codex runs | Template(s) | Specific check |
|----------------------|-----------------|-------------|----------------|
| `HotnessScoreService` | After file created, before next deliverable | `arch-consistency`, `m2-hotness` | Formula `(AvgEarnings × Demand) / (Supply + 1)`; supply fallback chain; tie-break `score DESC, recommendationPriority ASC`; cache key `hotness:{zone}:{timeSlot}:{date}`; 1h TTL via `TimeUnit.HOURS` |
| Cache invalidation in `ZoneController.upsert()` | After edit | `arch-consistency`, `m2-hotness` | `redisTemplate.keys("hotness:" + zoneId + ":*")` + null-guard `keys != null && !keys.isEmpty()` + try/catch + WARN log; API returns 200 even when invalidation throws |
| `TomorrowPlanAction` | After file created | `arch-consistency`, `m2-hotness` | `@Component`, `@Order(10)`, `implements PostDailySummaryAction`; sets `previousFlow="DAILY_EARNINGS"`, `flowType=TOMORROW_PLAN`; emits `tomorrow_plan_requested`; try/catch returns `Optional.empty()` |
| Peak Nudge scheduler | After file created | `arch-consistency` | `ACTIVE` users only; `lastActiveAt` within 7d; emits `peak_nudge_sent`; skips `OPTED_OUT` |
| All `*Test.java` | After test file created | `test-coverage` | Every M2 AC checkbox traceable to a test method, including null-key cache test |

#### M2 risk-area prompt (`scripts/codex-prompts/m2-hotness.md`)

```
You are reviewing M2 (intelligence slice) for Gigsarathi. v6 spec at
.omc/plans/gigsarathi-m234-parallel-plan-v6.md §M2.

Mark BLOCKING if any of these are violated:
1. Hotness formula is NOT exactly (AvgEarnings * Demand) / (Supply + 1)
2. Supply resolution does NOT use the fallback chain
   zone.estimatedSupply ?? historicalCount(zone, timeSlot, last7d) ?? 0
3. Cache key pattern does not match hotness:{zoneId}:{timeSlot}:{date}
4. TTL is not 1 hour (must use TimeUnit.HOURS)
5. ZoneController.upsert() does NOT call redisTemplate.keys("hotness:"+id+":*")
   AND/OR does NOT null-guard the result before delete
6. Cache invalidation is NOT wrapped in try/catch
7. TomorrowPlanAction is missing @Order(10) or @Component
8. TomorrowPlanAction does NOT set previousFlow="DAILY_EARNINGS"
9. M2 branch modifies DailyEarningsFlow.java, FlowType.java, or SessionState.java
10. M2 branch references PATCH /admin/features

End with: VERDICT: PASS or VERDICT: FAIL

# FILE: <path>
<file contents>
```

#### M2 pre-PR command

```bash
./scripts/codex-review.sh m2-hotness \
  gigsarathi-core/src/main/java/com/gigsarathi/intelligence/HotnessScoreService.java m2-hotness
./scripts/codex-review.sh m2-hotness \
  gigsarathi-core/src/main/java/com/gigsarathi/admin/ZoneController.java m2-zone-cache
./scripts/codex-review.sh m2-hotness \
  gigsarathi-core/src/main/java/com/gigsarathi/flow/TomorrowPlanAction.java m2-action
```

### 2.2 M3 — `feature/m3-engagement`

#### Per-deliverable Codex review schedule

| Deliverable (v6 §M3) | When Codex runs | Template(s) | Specific check |
|----------------------|-----------------|-------------|----------------|
| Inactive Nudge scheduler | After file created | `arch-consistency` | Skips `OPTED_OUT`; skips users with record within 2d |
| Weekly Report scheduler | After file created | `arch-consistency`, `m3-referral` | Raw `EarningsRecord` aggregation; `HotnessScoreService` NOT imported; <2 records → reminder only |
| `ReferralPromptAction` | After file created | `arch-consistency`, `m3-referral` | `@Order(20)`, `implements PostDailySummaryAction`; fires when count ≥ 3; sets `flowType=REFERRAL`; try/catch returns `Optional.empty()` |
| `ReferralService.ensureReferralCode()` call sites | After edit | `m3-referral` | Site 1: `MessageController.ingest()` AFTER idempotency check, BEFORE `flowEngine.handle()`; try/catch with WARN; Site 2: top of `FlowEngine.handle()`; both idempotent |
| Admin event/export | After file created | `arch-consistency` | `GET /admin/events/summary?from=&to=` and `GET /admin/earnings/export?from=&to=` (CSV); auth via existing `AdminKeyInterceptor` |
| All `*Test.java` | After test file created | `test-coverage` | Every M3 AC checkbox covered |

#### M3 risk-area prompt (`scripts/codex-prompts/m3-referral.md`)

```
You are reviewing M3 (engagement slice) for Gigsarathi. v6 spec at
.omc/plans/gigsarathi-m234-parallel-plan-v6.md §M3.

Mark BLOCKING if any of these are violated:
1. M3 branch imports com.gigsarathi.intelligence.HotnessScoreService anywhere
2. ReferralPromptAction is missing @Order(20) or @Component
3. ReferralPromptAction does NOT implement PostDailySummaryAction
4. ReferralPromptAction fires when count(EarningsRecord for user) < 3
5. ReferralService.ensureReferralCode() call in MessageController.ingest() is:
   - NOT wrapped in try/catch, OR
   - placed BEFORE the idempotency check (idempotencyRepository.existsByPlatformAndMessageId), OR
   - placed AFTER flowEngine.handle()
6. ReferralService.ensureReferralCode() call in FlowEngine.handle() is missing
   (forward-defensive site is required even though MVP has one caller)
7. Weekly report uses HotnessScoreService for "top zone" — must be raw
   max-by-gross-earnings aggregation
8. M3 branch modifies DailyEarningsFlow.java, FlowType.java, or SessionState.java
9. M3 branch references PATCH /admin/features or app_config

Verify the EXACT call-site placement in MessageController.ingest() with line
context: it must sit AFTER `idempotencyRepository.existsByPlatformAndMessageId(...)`
returns false (i.e. message is new) and BEFORE the call to `flowEngine.handle(...)`.

End with: VERDICT: PASS or VERDICT: FAIL

# FILE: <path>
<file contents>
```

#### M3 pre-PR command

```bash
./scripts/codex-review.sh m3-referral \
  gigsarathi-core/src/main/java/com/gigsarathi/flow/ReferralPromptAction.java m3-action
./scripts/codex-review.sh m3-referral \
  gigsarathi-core/src/main/java/com/gigsarathi/referral/ReferralService.java m3-service
./scripts/codex-review.sh m3-referral \
  gigsarathi-core/src/main/java/com/gigsarathi/adapter/MessageController.java m3-mc-ingest
./scripts/codex-review.sh m3-referral \
  gigsarathi-core/src/main/java/com/gigsarathi/flow/FlowEngine.java m3-flow-engine
./scripts/codex-review.sh m3-referral \
  gigsarathi-core/src/main/java/com/gigsarathi/scheduler/WeeklyReportScheduler.java m3-weekly
```

### 2.3 M4 — `feature/m4-advanced`

#### Per-deliverable Codex review schedule

| Deliverable (v6 §M4) | When Codex runs | Template(s) | Specific check |
|----------------------|-----------------|-------------|----------------|
| Loan flow | After file created | `arch-consistency` | Eligibility ≥5 records AND ≥3 in 7d AND avg gross ≥₹700; Redis `loan-offered:{userId}` 30d TTL; `loanEnabled` flag gated |
| Account linking | After file created | `arch-consistency` | 6-digit code; `link-token:{code}` 10min TTL; secondary user `MERGED` |
| `PATCH /admin/features` | After file created | `arch-consistency`, `m4-bootstrap` | Toggles `loanEnabled`, `referralRewardEnabled`; `X-Admin-Key` (auto via `AdminKeyInterceptor`) |
| `AppConfigBootstrap` + `application-test.yml` | After both edits | `m4-bootstrap` | `@ConditionalOnProperty(name="app.bootstrap.enabled", havingValue="true", matchIfMissing=true)`; `app.bootstrap.enabled: false` added to `src/test/resources/application-test.yml` in SAME commit |
| Datadog config | After files created | `arch-consistency` | JMX + APM + log forwarding wired; no secrets in repo |
| All `*Test.java` | After test file created | `test-coverage` | Every M4 AC checkbox covered, including `GigsarathiApplicationTests` |

#### M4 risk-area prompt (`scripts/codex-prompts/m4-bootstrap.md`)

```
You are reviewing M4 (advanced slice) for Gigsarathi. v6 spec at
.omc/plans/gigsarathi-m234-parallel-plan-v6.md §M4.

Mark BLOCKING if any of these are violated:
1. AppConfigBootstrap is NOT annotated with @ConditionalOnProperty using the
   exact form: @ConditionalOnProperty(name="app.bootstrap.enabled",
   havingValue="true", matchIfMissing=true)
2. src/test/resources/application-test.yml does NOT contain
   `app.bootstrap.enabled: false`
3. The application-test.yml change is NOT in the same commit as
   AppConfigBootstrap.java
4. M4 branch modifies FlowType.java (LOAN and ACCOUNT_LINK are pre-declared
   in the prep commit)
5. PATCH /admin/features endpoint is missing X-Admin-Key auth (note this is
   automatic via AdminKeyInterceptor + WebMvcConfig.addPathPatterns("/admin/**"))
6. Loan eligibility predicate does NOT match: count(records) >= 5 AND
   count(records last 7d) >= 3 AND avg(gross) >= 700
7. Loan Redis dedup key is NOT loan-offered:{userId} or TTL is not 30 days
8. Link token Redis key is NOT link-token:{code} or TTL is not 10 minutes
9. Datadog config commits any secret (api key, app key) in plaintext

End with: VERDICT: PASS or VERDICT: FAIL

# FILE: <path>
<file contents>
```

#### M4 pre-PR command

```bash
./scripts/codex-review.sh m4-bootstrap \
  gigsarathi-core/src/main/java/com/gigsarathi/config/AppConfigBootstrap.java m4-bootstrap
./scripts/codex-review.sh m4-bootstrap \
  gigsarathi-core/src/test/resources/application-test.yml m4-test-yml
./scripts/codex-review.sh m4-bootstrap \
  gigsarathi-core/src/main/java/com/gigsarathi/admin/FeaturesController.java m4-features
./scripts/codex-review.sh m4-bootstrap \
  gigsarathi-core/src/main/java/com/gigsarathi/loan/LoanFlow.java m4-loan
./scripts/codex-review.sh m4-bootstrap \
  gigsarathi-core/src/main/java/com/gigsarathi/account/AccountLinkingFlow.java m4-link
```

### 2.4 PR readiness criteria (each branch, identical)

A branch may open a PR to `main` only when ALL of:

- `mvn -B -pl gigsarathi-core test` green locally
- All Codex review logs in `.omc/logs/codex-reviews/` for this branch end in `VERDICT: PASS`
- The branch has not modified any file marked `NONE` risk in v6's Conflict Hotspot Table after prep
- PR description copy-pastes the milestone's AC checklist with checkbox state

---

## Phase 3 — Merge Gate Validation with Codex

Merge order is locked: **M4 → M2 → M3** (per v6 §"Phase 3").

### 3.1 Pre-merge Codex validation checklist (per PR)

Run `merge-conflict-scan` against each PR's diff. The wrapper computes merge-order booleans deterministically via `git cat-file -e` before invoking Codex — Codex never infers file existence from a truncated `ls-tree` list:

```bash
# Compute booleans (deterministic — exit 0 = file exists on origin/main)
BOOTSTRAP_PATH="gigsarathi-core/src/main/java/com/gigsarathi/config/AppConfigBootstrap.java"
TOMORROW_PATH="gigsarathi-core/src/main/java/com/gigsarathi/flow/TomorrowPlanAction.java"
REFERRAL_PATH="gigsarathi-core/src/main/java/com/gigsarathi/flow/ReferralPromptAction.java"

git cat-file -e "origin/main:${BOOTSTRAP_PATH}" 2>/dev/null && BOOTSTRAP_ON_MAIN=true || BOOTSTRAP_ON_MAIN=false
git cat-file -e "origin/main:${TOMORROW_PATH}"  2>/dev/null && TOMORROW_PLAN_ON_MAIN=true  || TOMORROW_PLAN_ON_MAIN=false
git cat-file -e "origin/main:${REFERRAL_PATH}"  2>/dev/null && REFERRAL_ON_MAIN=true || REFERRAL_ON_MAIN=false

# Pass diff + booleans into the template (merge-conflict-scan is a special case
# that takes a diff file and env vars rather than a source file)
MERGE_BOOLEANS="BOOTSTRAP_ON_MAIN=${BOOTSTRAP_ON_MAIN}
TOMORROW_PLAN_ON_MAIN=${TOMORROW_PLAN_ON_MAIN}
REFERRAL_ON_MAIN=${REFERRAL_ON_MAIN}"

./scripts/codex-review.sh merge-conflict-scan /tmp/<branch>-diff.patch <branch>-merge-gate \
  --extra-context "$MERGE_BOOLEANS"
```

The `--extra-context` flag appends the boolean block under `# MERGE ORDER BOOLEANS` in the prompt (add this optional flag to the wrapper script). Codex reads only the injected booleans, not raw `git ls-tree` output.

`scripts/codex-prompts/merge-conflict-scan.md`:

```
You are gating a merge into main for the Gigsarathi M2/M3/M4 build. Authoritative
spec: .omc/plans/gigsarathi-m234-parallel-plan-v6.md, especially the Conflict
Hotspot Table.

Mark BLOCKING if the diff under review modifies ANY of these "NONE risk after
prep" files:
- gigsarathi-core/src/main/java/com/gigsarathi/flow/DailyEarningsFlow.java
- gigsarathi-core/src/main/java/com/gigsarathi/flow/FlowType.java
- gigsarathi-core/src/main/java/com/gigsarathi/session/SessionState.java
- gigsarathi-core/src/main/java/com/gigsarathi/flow/OptOutHandler.java

Mark BLOCKING if a file marked LOW risk is modified by a branch other than
the owner from v6:
- MessageController.java owner: M3 only
- FlowEngine.java owner: M3 only
- ZoneController.java owner: M2 only
- application-test.yml owner: M4 only

Mark ADVISORY if the diff overlaps with another branch's likely territory but
does not violate ownership.

Also verify the merge order claim in the PR using the boolean values provided
below (derived deterministically by the wrapper before invoking Codex):
- M4 PR (first merge): TOMORROW_PLAN_ON_MAIN must be false AND REFERRAL_ON_MAIN must be false
- M2 PR (after M4): BOOTSTRAP_ON_MAIN must be true
- M3 PR (after M2): TOMORROW_PLAN_ON_MAIN must be true AND BOOTSTRAP_ON_MAIN must be true

If any merge-order boolean contradicts the expected state, mark BLOCKING.

End with: VERDICT: PASS or VERDICT: FAIL

# DIFF:
<diff>
# MERGE ORDER BOOLEANS (set by wrapper via git cat-file -e, not inferred):
BOOTSTRAP_ON_MAIN=<true|false>
TOMORROW_PLAN_ON_MAIN=<true|false>
REFERRAL_ON_MAIN=<true|false>
```

### 3.2 Cross-branch conflict detection (after each merge)

After merging M4 into `main`:

1. Rebase `feature/m2-intelligence` onto updated `main` locally.
2. Run `merge-conflict-scan` on the rebased diff.
3. Re-run `mvn test` on rebased branch.
4. If `VERDICT: PASS` and tests green → open M2 PR.

After merging M2 into `main`:

1. Rebase `feature/m3-engagement` onto updated `main` (per v6 §"Merge base").
2. Run `merge-conflict-scan` on the rebased diff.
3. Re-run `mvn test` on rebased branch.
4. Run M3 risk prompt one final time on `MessageController.ingest()` — the
   most-likely conflict surface.
5. If green → open M3 PR.

### 3.3 Post-merge smoke test integration

After EACH merge to `main`:

```bash
# 1. Build & full test suite
mvn -B -pl gigsarathi-core verify

# 2. Docker boot smoke
docker-compose down -v && docker-compose up -d
./scripts/wait-for-services.sh   # existing M1 helper, if present; else mvn dependency

# 3. M1 verification gate (all 11 checks per v6 §"M1 verification gate")
./scripts/m1-verify.sh

# 4. Codex post-merge review of the merge commit itself
git diff HEAD^..HEAD > /tmp/merge-diff.patch
./scripts/codex-review.sh acceptance-criteria /tmp/merge-diff.patch post-merge-<milestone>
```

The post-merge `acceptance-criteria` run loads the merged milestone's full AC
list and confirms the merge commit (which equals the squashed PR diff) still
satisfies every checkbox.

### 3.4 Merge gate sign-off table

| Step | Owner | Codex artifact required | Pass criterion |
|------|-------|-------------------------|----------------|
| M4 PR opened | M4 author | All Phase 2.3 logs `PASS` | mvn green; CI Codex job `PASS` |
| M4 merged | Team lead | `merge-conflict-scan` `PASS` | Post-merge smoke + M1 verify green |
| M2 rebased | M2 author | `merge-conflict-scan` on rebased branch | mvn green |
| M2 PR opened | M2 author | All Phase 2.1 logs `PASS` post-rebase | CI Codex job `PASS` |
| M2 merged | Team lead | `merge-conflict-scan` `PASS` | Post-merge smoke + M1 verify green |
| M3 rebased | M3 author | `merge-conflict-scan` on rebased branch | mvn green |
| M3 PR opened | M3 author | All Phase 2.2 logs `PASS` post-rebase | CI Codex job `PASS` |
| M3 merged | Team lead | `merge-conflict-scan` `PASS` + final M3 risk prompt on `MessageController.java` | Post-merge smoke + M1 verify green |

### 3.5 Branch protection requirement (bypass-proof gate)

The merge gate is only enforceable if `main` has branch protection configured to require the `codex-review` status check before merge. This is a hard acceptance gate for Phase 0:

**Required GitHub branch protection settings for `main`:**
- `Require status checks to pass before merging`: enabled
- Required status check name: `codex-review` (must match the `name:` field in `.github/workflows/codex-review.yml` exactly)
- `Require branches to be up to date before merging`: enabled (forces rebase before the gate runs)
- `Do not allow bypassing the above settings`: enabled (prevents admin force-merge)

**Acceptance criterion**: no PR can be merged into `main` without a passing `codex-review` CI run. If a team member needs an emergency bypass (e.g., hotfix during Codex CLI outage), the protocol is:

1. Team lead confirms (in writing, e.g., Slack thread) that no other PRs are in "ready to merge" state and assigns a second team member to watch GitHub for concurrent merge attempts during the window.
2. Team lead temporarily disables branch protection (documents the timestamp in the Slack thread).
3. Merge the single emergency PR immediately — the window must be under 2 minutes.
4. Team lead re-enables branch protection and posts the re-enable timestamp to the Slack thread.
5. The second watcher confirms no other merges occurred during the window (check GitHub audit log: `gh api repos/{owner}/{repo}/events | jq '.[] | select(.type=="PushEvent")'`).
6. File a post-merge Codex review manually within 24 hours; output appended to `.omc/logs/codex-reviews/` with label `emergency-bypass-<PR number>`.

**Note**: This protocol does not eliminate the bypass window entirely — it narrows it to under 2 minutes with a second-person witness and audit trail. If the project later grows to a pace where 2-minute windows are risky, migrate to GitHub Merge Queue instead.

**Outage circuit-breaker**: If the emergency bypass protocol is invoked more than 3 times within any rolling 7-day period, OR Codex CLI is continuously unavailable for more than 5 consecutive days, the team lead must freeze all non-critical merges (feature PRs from M2/M3/M4) until the outage is resolved. Only security hotfixes may proceed under the bypass protocol during a freeze. This hard limit prevents repeated lead-approved bypasses from de facto disabling enforcement. Document each bypass invocation with timestamp and justification in `.omc/logs/codex-reviews/bypass-log.md`.

**Circuit-breaker reset**: The "3 bypasses in 7 days" trigger resets automatically as the rolling window advances — no explicit sign-off needed once the oldest bypass event is more than 7 days old. The "5 consecutive days of outage" freeze requires explicit team-lead sign-off to unfreeze: confirm Codex CLI is operational with a successful local `codex exec` invocation and post confirmation to the Slack thread before merges resume.

Document the branch protection configuration as a screenshot or `gh api` output in the Phase 0 done evidence (Section 0.7).

---

## Pre-Mortem (executive summary — 3 scenarios specific to Codex gating)

1. **Codex hallucinates a `VERDICT: PASS` despite missing AC** — Mitigation: every Codex run is artifacted; humans (team lead) spot-check at least 30% of `acceptance-criteria` runs against the diff. **Auditable selector rule**: review every PR where `(PR number % 3 == 0)` — deterministic, no manual curation needed. Owner: team lead. Artifact path: `.omc/logs/codex-reviews/<label>-acceptance-criteria.log` (uploaded to CI artifacts by step 5 of the workflow). Review checklist: for each AC line in v6, confirm the log shows it enumerated and evaluated; flag any line absent from the log as a miss. Escalation: three misses in one milestone trigger a full-batch re-review of that milestone's PRs before merge. Templates require Codex to enumerate each AC line individually so omission is auditable.
2. **Codex CLI unavailable in CI (network, key, install)** — Mitigation: wrapper script exits 2 with explicit "codex CLI missing" error rather than silently passing. CI job marks `failure` distinct from `VERDICT: FAIL` so on-call can distinguish infra outage from review failure.
3. **Cost spike from re-running Codex on every push** — Mitigation: (a) GitHub Actions concurrency group `codex-review-${{ github.ref }}` cancels in-flight runs on new pushes to the same branch; Codex only runs on changed files; local pre-PR script encouraged so most reviews run once on the developer machine. **Important**: `max-parallel` in a job matrix limits parallel jobs within a single workflow run, not across independently triggered runs from different branches — M2 and M3 pushing simultaneously each start separate workflow runs and each can have full concurrency. The effective repo-wide cost guard is therefore (b) + (c) below, not a matrix cap. (b) **Per-ref cancellation is the primary cost guard**: the `codex-review-${{ github.ref }}` concurrency group means rapid pushes to the same branch cancel previous runs; cross-branch cost is naturally bounded by the 3-milestone scope (M2, M3, M4 — at most 3 simultaneous workflow runs). (c) **Monthly budget guard**: configure an OpenAI usage alert at a team-chosen threshold at Phase 0 kickoff; if the alert fires, notify the team lead who decides whether to pause non-critical PR activity for the remainder of the billing cycle. **Do not auto-downgrade `acceptance-criteria` to advisory-only** — this would contradict the plan's explicit rejection of Option D (advisory-only gating) in the ADR. The team-lead escalation path preserves human control without weakening the enforcement guarantee. Document the alert threshold in `scripts/codex-review.sh` header comment.

---

## ADR — Codex as the Review Engine

**Decision:** Use Codex CLI (`codex exec`) as the automated review/quality gate at PR open and at each merge step (Option C: PR + merge gate), with developer-side local pre-PR runs encouraged.

**Decision Drivers:**
1. Each milestone's risk surface differs (M2: formula correctness; M3: import boundary + call-site placement; M4: bootstrap conditional). Codex prompts can encode milestone-specific risks that a generic linter cannot.
2. Parallel branches need a deterministic, reproducible review signal. Codex `exec` with templated prompts produces inspectable logs that a human reviewer (or a later Critic agent run) can audit.
3. Merge order is the integration plan (v6 Principle #4). The merge gate must run a non-trivial cross-file check ("does main contain AppConfigBootstrap before merging M2?") that benefits from LLM reasoning over `git ls-tree` output.

**Alternatives Considered:**

- **Option A — Codex as pre-PR gate only.** Pros: lowest CI cost; fastest iteration. Cons: nothing prevents a developer from pushing without running it; no enforcement at merge; cross-branch conflicts post-rebase are undetected.
- **Option B — Codex at every commit.** Pros: maximum coverage. Cons: cost and latency explode for branches with many small commits; no value over per-PR runs because the merge unit is the squashed PR.
- **Option C — Codex at PR + merge gate (CHOSEN).** Pros: enforced via GitHub Actions; runs once per PR sync; runs again at merge gate so cross-branch conflicts caught after rebase; reasonable cost. Cons: requires CI wiring; depends on `OPENAI_API_KEY` secret and Codex CLI install reliability.
- **Option D — Codex advisory-only (never blocking).** Pros: zero CI risk; Codex output is visible but cannot block a developer. Cons: advisory-only gates are routinely ignored under deadline pressure — experience from similar projects shows advisory CI jobs are disabled or bypassed within weeks when they produce false positives. This defeats the purpose of gating at the merge step where cross-branch conflicts actually appear. Rejected: enforcement is the core value proposition; an unenforceable gate provides no safety guarantee for the locked merge order (v6 Principle #4).

**Why Chosen:** Option C aligns with v6's locked merge order (M4 → M2 → M3) by adding a real check at each merge step where conflicts can actually surface. It avoids the wasted runs of Option B and the lack of enforcement of Option A. It also keeps a developer-side local path so most failures are caught before PR is opened.

**Consequences:**
- New CI dependency on Codex CLI and `OPENAI_API_KEY` secret.
- Adds ~3–7 min to PR CI per branch (Codex runs in parallel with `mvn test` after build is green).
- Requires curated prompt templates living in `scripts/codex-prompts/` — these become a maintained artifact and must be updated if v6 architecture is amended via the Prep-Commit Revision Protocol.
- Logs accumulate in `.omc/logs/codex-reviews/`; add to `.gitignore` for non-CI runs (CI uploads as artifact).

**Follow-ups:**
1. After M3 merges, archive `.omc/logs/codex-reviews/*` for the build into release notes.
2. If Codex false-positive rate exceeds 10% on `arch-consistency`, refine the template (track via a tally appended to merge gate sign-off).
3. Codex CLI version pin in `scripts/codex-review.sh` once a stable channel is selected.
4. Decide whether to keep the GitHub Actions workflow post-MVP or move to a lightweight pre-merge check only.

---

## RALPLAN-DR Summary

### Principles
1. **Codex is the review engine, not the author** — Codex runs read-only, advisory; humans/Claude apply fixes.
2. **One template per concern** — arch consistency, test coverage, AC verification, plus per-milestone risk prompts. Templates are versioned in repo.
3. **Logs are evidence** — every Codex review produces an artifacted log with explicit `VERDICT: PASS|FAIL` last line; CI fails on FAIL.
4. **Gate where conflict actually happens** — pre-PR (developer surface) and at merge (cross-branch surface). Skip per-commit (no marginal value).
5. **v6 is immutable** — Codex prompts cite v6 sections verbatim; if v6 must change, Prep-Commit Revision Protocol applies and templates are updated in lockstep.

### Decision Drivers
1. Milestone-specific risks demand milestone-specific review prompts (single generic linter is insufficient).
2. Parallel branches require a deterministic, reproducible review signal that survives rebase against M4-then-M2-merged `main`.
3. Cost and latency must remain bounded — daily developer use cannot be punished by review tooling.

### Viable Options
- **Option A — Codex as pre-PR gate only.** Pros: cheap, fast. Cons: no merge-time enforcement; no cross-branch conflict scan; rebase drift undetected.
- **Option B — Codex at every commit.** Pros: maximum coverage. Cons: cost/latency, redundant on PR squash; little marginal value.
- **Option C — Codex at PR + merge gate (CHOSEN).** Pros: enforces milestone risks at PR; enforces ownership and merge order at gate; balanced cost. Cons: requires CI wiring and a CLI/secret dependency.

### Why Chosen
Option C is the only option that catches cross-branch conflicts AFTER the M4-into-main and M2-into-main rebases, which is exactly where the v6 plan locates the highest residual risk (`MessageController.ingest()`, `application-test.yml` ownership, FlowType non-edits). Option A is insufficient; Option B is wasteful. The local pre-PR path covered in Phase 0.6 keeps Option C's cost manageable for developers.

### Consequences
- New runtime dependency: Codex CLI in CI + `OPENAI_API_KEY` secret.
- Six new prompt templates checked into `scripts/codex-prompts/` and maintained alongside v6.
- New artifact directory `.omc/logs/codex-reviews/` (gitkept, runtime contents are CI artifacts).
- `mvn test` precedes Codex always — Codex never reviews red code.

### Follow-ups
1. Pin Codex CLI version in `scripts/codex-review.sh` once stable channel selected.
2. Track Codex FP rate per template; refine after first full M4 → M2 → M3 cycle.
3. Decide post-MVP whether merge-gate Codex run is retained or downgraded to advisory-only.
4. If `redisTemplate.keys("hotness:*")` migrates to SCAN-cursor (M2 hardening backlog item 5), update `m2-hotness.md` to allow either pattern.
