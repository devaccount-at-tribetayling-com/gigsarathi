package com.gigsarathi.flow;

import com.gigsarathi.domain.earnings.EarningsRecord;
import com.gigsarathi.domain.earnings.EarningsRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class LoanEligibilityService {

    static final int MIN_TOTAL_RECORDS = 5;
    static final int MIN_RECENT_RECORDS = 3;
    static final double MIN_AVG_GROSS = 700.0;

    private final EarningsRepository earningsRepository;

    public LoanEligibilityService(EarningsRepository earningsRepository) {
        this.earningsRepository = earningsRepository;
    }

    public boolean isEligible(String userId, String platform) {
        long total = earningsRepository.countByUserIdAndPlatform(userId, platform);
        if (total < MIN_TOTAL_RECORDS) return false;

        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        List<EarningsRecord> recent = earningsRepository
                .findByUserIdAndPlatformAndSubmittedAtAfter(userId, platform, cutoff);
        if (recent.size() < MIN_RECENT_RECORDS) return false;

        List<EarningsRecord> all = earningsRepository
                .findByUserIdAndPlatformOrderBySubmittedAtDesc(userId, platform);
        double avgGross = all.stream()
                .mapToDouble(EarningsRecord::getEarningsAmount)
                .average()
                .orElse(0.0);
        return avgGross >= MIN_AVG_GROSS;
    }
}
