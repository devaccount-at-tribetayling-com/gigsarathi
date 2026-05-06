package com.gigsarathi.flow;

import com.gigsarathi.bot.MessageSenderRouter;
import com.gigsarathi.domain.earnings.EarningsRecord;
import com.gigsarathi.domain.earnings.EarningsRepository;
import com.gigsarathi.domain.event.EventService;
import com.gigsarathi.referral.ReferralService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@Order(20)
public class ReferralPromptAction implements PostDailySummaryAction {

    private final EarningsRepository earningsRepository;
    private final ReferralService referralService;
    private final EventService eventService;
    private final MessageSenderRouter messageSender;

    public ReferralPromptAction(EarningsRepository earningsRepository,
                                ReferralService referralService,
                                EventService eventService,
                                MessageSenderRouter messageSender) {
        this.earningsRepository = earningsRepository;
        this.referralService = referralService;
        this.eventService = eventService;
        this.messageSender = messageSender;
    }

    @Override
    public Optional<SessionState> apply(String userId, String platform, EarningsRecord record) {
        try {
            long count = earningsRepository.countByUserIdAndPlatform(userId, platform);
            if (count < 3) {
                return Optional.empty();
            }
            String code = referralService.ensureReferralCode(userId, platform).getCode();
            messageSender.sendMessage(userId, platform,
                    "You've been tracking consistently! Share your referral code *" + code +
                    "* with fellow gig workers and earn rewards when they join.");
            eventService.emit("referral_prompt_sent", userId, platform, Map.of("code", code));
            SessionState next = SessionState.builder()
                    .flowType(FlowType.REFERRAL.name())
                    .stepIndex(0)
                    .pendingData(new HashMap<>())
                    .startedAt(Instant.now().toString())
                    .previousFlow(FlowType.DAILY_EARNINGS.name())
                    .build();
            return Optional.of(next);
        } catch (Exception e) {
            log.warn("ReferralPromptAction failed for {}/{}: {}", platform, userId, e.getMessage());
            return Optional.empty();
        }
    }
}
