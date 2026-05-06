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
