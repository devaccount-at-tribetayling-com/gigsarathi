package com.gigsarathi.flow;

import com.gigsarathi.domain.earnings.EarningsRecord;

import java.util.Optional;

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
