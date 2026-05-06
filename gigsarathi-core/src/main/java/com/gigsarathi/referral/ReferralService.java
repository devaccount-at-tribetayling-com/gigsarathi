package com.gigsarathi.referral;

import com.gigsarathi.domain.referral.ReferralCode;
import com.gigsarathi.domain.referral.ReferralRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
public class ReferralService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ReferralRepository referralRepository;

    public ReferralService(ReferralRepository referralRepository) {
        this.referralRepository = referralRepository;
    }

    public ReferralCode ensureReferralCode(String userId, String platform) {
        return referralRepository.findByUserIdAndPlatform(userId, platform)
                .orElseGet(() -> createCode(userId, platform));
    }

    public Optional<ReferralCode> getReferralCode(String userId, String platform) {
        return referralRepository.findByUserIdAndPlatform(userId, platform);
    }

    private ReferralCode createCode(String userId, String platform) {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                return referralRepository.save(ReferralCode.builder()
                        .userId(userId)
                        .platform(platform)
                        .code(generateCode())
                        .rewardAmount(null)
                        .createdAt(Instant.now())
                        .build());
            } catch (DuplicateKeyException e) {
                log.warn("Referral code collision on attempt {} for {}/{}", attempt + 1, platform, userId);
            }
        }
        // Another thread may have created it during collision retries
        return referralRepository.findByUserIdAndPlatform(userId, platform)
                .orElseThrow(() -> new IllegalStateException(
                        "Failed to create referral code for " + platform + "/" + userId));
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
