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
