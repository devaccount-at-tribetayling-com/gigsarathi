package com.gigsarathi.stub;

// CODEX-FAIL-STUB: intentionally violates arch rules for FAIL→PASS CI round-trip.
// This file must NOT be merged to main. Remove before merge.
//
// Violations present:
// 1. Imports forbidden HotnessScoreService (M2/M3 boundary rule)
// 2. Missing @Component — PostDailySummaryAction implementors must be @Component
// 3. Missing @Order(N) annotation

import com.gigsarathi.intelligence.HotnessScoreService;

public class ArchViolationStub {

    private HotnessScoreService hotnessScoreService;

    public void doSomething() {
        // intentionally empty — arch violation is the import above
    }
}
