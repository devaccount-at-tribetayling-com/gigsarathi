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
