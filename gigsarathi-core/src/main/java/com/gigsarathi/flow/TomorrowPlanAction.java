package com.gigsarathi.flow;

import com.gigsarathi.bot.MessageSenderRouter;
import com.gigsarathi.domain.earnings.EarningsRecord;
import com.gigsarathi.domain.earnings.EarningsRepository;
import com.gigsarathi.domain.event.EventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@Order(10)
public class TomorrowPlanAction implements PostDailySummaryAction {

    private final EarningsRepository earningsRepository;
    private final EventService eventService;
    private final MessageSenderRouter messageSender;

    public TomorrowPlanAction(EarningsRepository earningsRepository,
                               EventService eventService,
                               MessageSenderRouter messageSender) {
        this.earningsRepository = earningsRepository;
        this.eventService = eventService;
        this.messageSender = messageSender;
    }

    @Override
    public Optional<SessionState> apply(String userId, String platform, EarningsRecord record) {
        try {
            long count = earningsRepository.countByUserIdAndPlatform(userId, platform);
            if (count >= 3) {
                return Optional.empty();
            }
            messageSender.sendMessage(userId, platform,
                    "Great job tracking today! Would you like to plan your zones for tomorrow? Reply *Yes* to start.");
            eventService.emit("tomorrow_plan_requested", userId, platform, Map.of());
            SessionState next = SessionState.builder()
                    .flowType(FlowType.TOMORROW_PLAN.name())
                    .stepIndex(0)
                    .pendingData(new HashMap<>())
                    .startedAt(Instant.now().toString())
                    .previousFlow(FlowType.DAILY_EARNINGS.name())
                    .build();
            return Optional.of(next);
        } catch (Exception e) {
            log.warn("TomorrowPlanAction failed for {}/{}: {}", platform, userId, e.getMessage());
            return Optional.empty();
        }
    }
}
