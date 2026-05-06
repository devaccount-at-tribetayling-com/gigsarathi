package com.gigsarathi.flow;

import com.gigsarathi.bot.MessageSenderRouter;
import com.gigsarathi.config.AppConfigBootstrap;
import com.gigsarathi.domain.earnings.EarningsRecord;
import com.gigsarathi.domain.event.EventService;
import com.gigsarathi.domain.config.AppConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@Order(30)
public class LoanFlowAction implements PostDailySummaryAction {

    static final String LOAN_OFFERED_KEY_PREFIX = "loan-offered:";
    private static final Duration DEDUP_TTL = Duration.ofDays(30);

    private final AppConfigRepository appConfigRepository;
    private final LoanEligibilityService eligibilityService;
    private final RedisTemplate<String, String> redisTemplate;
    private final MessageSenderRouter messageSender;
    private final EventService eventService;

    public LoanFlowAction(AppConfigRepository appConfigRepository,
                          LoanEligibilityService eligibilityService,
                          RedisTemplate<String, String> redisTemplate,
                          MessageSenderRouter messageSender,
                          EventService eventService) {
        this.appConfigRepository = appConfigRepository;
        this.eligibilityService = eligibilityService;
        this.redisTemplate = redisTemplate;
        this.messageSender = messageSender;
        this.eventService = eventService;
    }

    @Override
    public Optional<SessionState> apply(String userId, String platform, EarningsRecord record) {
        try {
            boolean loanEnabled = appConfigRepository.findById(AppConfigBootstrap.CONFIG_ID)
                    .map(c -> c.isLoanEnabled())
                    .orElse(false);
            if (!loanEnabled) return Optional.empty();

            if (!eligibilityService.isEligible(userId, platform)) return Optional.empty();

            String dedupKey = LOAN_OFFERED_KEY_PREFIX + userId;
            if (Boolean.TRUE.equals(redisTemplate.hasKey(dedupKey))) return Optional.empty();

            redisTemplate.opsForValue().set(dedupKey, "1", DEDUP_TTL);

            messageSender.sendMessage(userId, platform,
                    "You qualify for a quick loan based on your earnings! " +
                    "Would you like to know more? Reply *YES* or *NO*.");
            eventService.emit("loan_offer_shown", userId, platform, Map.of());

            SessionState next = SessionState.builder()
                    .flowType(FlowType.LOAN.name())
                    .stepIndex(0)
                    .pendingData(new HashMap<>())
                    .startedAt(Instant.now().toString())
                    .previousFlow(FlowType.DAILY_EARNINGS.name())
                    .build();
            return Optional.of(next);
        } catch (Exception e) {
            log.warn("LoanFlowAction failed for {}/{}: {}", platform, userId, e.getMessage());
            return Optional.empty();
        }
    }
}
