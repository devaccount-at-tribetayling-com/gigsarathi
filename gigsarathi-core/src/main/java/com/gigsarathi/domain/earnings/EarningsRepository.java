package com.gigsarathi.domain.earnings;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface EarningsRepository extends MongoRepository<EarningsRecord, String> {

    List<EarningsRecord> findByUserIdAndPlatformAndSubmittedAtAfter(
            String userId, String platform, Instant after);

    List<EarningsRecord> findByUserIdAndPlatformOrderBySubmittedAtDesc(
            String userId, String platform);
}
