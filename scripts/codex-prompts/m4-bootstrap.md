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
