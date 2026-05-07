package com.gigsarathi.flow;

import com.gigsarathi.domain.earnings.EarningsRecord;
import com.gigsarathi.domain.earnings.EarningsRepository;
import com.gigsarathi.domain.user.User;
import com.gigsarathi.domain.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Slf4j
@Service
public class AccountLinkService {

    public static final String LINK_TOKEN_PREFIX = "link-token:";
    private static final Duration LINK_TTL = Duration.ofMinutes(10);
    private static final Random RANDOM = new Random();

    private final RedisTemplate<String, String> redisTemplate;
    private final UserRepository userRepository;
    private final EarningsRepository earningsRepository;

    public AccountLinkService(RedisTemplate<String, String> redisTemplate,
                               UserRepository userRepository,
                               EarningsRepository earningsRepository) {
        this.redisTemplate = redisTemplate;
        this.userRepository = userRepository;
        this.earningsRepository = earningsRepository;
    }

    public String generateCode(String userId, String platform) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        redisTemplate.opsForValue().set(LINK_TOKEN_PREFIX + code, platform + ":" + userId, LINK_TTL);
        return code;
    }

    public LinkResult link(String code, String secondaryUserId, String secondaryPlatform) {
        String value = redisTemplate.opsForValue().get(LINK_TOKEN_PREFIX + code);
        if (value == null) return LinkResult.notFound();

        String[] parts = value.split(":", 2);
        if (parts.length != 2) return LinkResult.invalid();
        String primaryPlatform = parts[0];
        String primaryUserId = parts[1];

        Optional<User> primaryOpt = userRepository.findByPlatformAndUserId(primaryPlatform, primaryUserId);
        Optional<User> secondaryOpt = userRepository.findByPlatformAndUserId(secondaryPlatform, secondaryUserId);
        if (primaryOpt.isEmpty() || secondaryOpt.isEmpty()) return LinkResult.notFound();

        User secondary = secondaryOpt.get();
        secondary.setStatus("MERGED");
        secondary.setLinkedUserId(primaryUserId);
        userRepository.save(secondary);

        List<EarningsRecord> records = earningsRepository
                .findByUserIdAndPlatformOrderBySubmittedAtDesc(secondaryUserId, secondaryPlatform);
        for (EarningsRecord r : records) {
            r.setUserId(primaryUserId);
            r.setPlatform(primaryPlatform);
            earningsRepository.save(r);
        }

        redisTemplate.delete(LINK_TOKEN_PREFIX + code);
        log.info("account_linked: secondary={}/{} → primary={}/{}, records={}",
                secondaryPlatform, secondaryUserId, primaryPlatform, primaryUserId, records.size());
        return LinkResult.success(primaryPlatform, primaryUserId, records.size());
    }

    public record LinkResult(boolean found, boolean valid, boolean success,
                             String primaryPlatform, String primaryUserId, int recordsMerged) {
        static LinkResult notFound() { return new LinkResult(false, false, false, null, null, 0); }
        static LinkResult invalid()  { return new LinkResult(true, false, false, null, null, 0); }
        static LinkResult success(String pp, String puid, int merged) {
            return new LinkResult(true, true, true, pp, puid, merged);
        }
    }
}
