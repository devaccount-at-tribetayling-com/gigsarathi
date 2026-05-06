package com.gigsarathi.domain.referral;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ReferralRepository extends MongoRepository<ReferralCode, String> {

    Optional<ReferralCode> findByUserIdAndPlatform(String userId, String platform);

    Optional<ReferralCode> findByCode(String code);
}
