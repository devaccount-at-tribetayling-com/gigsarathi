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
