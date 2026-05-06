package com.gigsarathi.flow;

public enum FlowType {
    NONE,
    ONBOARDING,
    DAILY_EARNINGS,
    TOMORROW_PLAN,    // M2: chained off daily summary "Yes" response
    REFERRAL,         // M3: chained off daily summary after ≥3 tracking days
    LOAN,             // M4: loan eligibility entry point
    ACCOUNT_LINK      // M4: 6-digit account linking
}
