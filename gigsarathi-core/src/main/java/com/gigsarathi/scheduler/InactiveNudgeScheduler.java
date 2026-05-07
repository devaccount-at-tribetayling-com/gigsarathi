package com.gigsarathi.scheduler;

import com.gigsarathi.bot.MessageSenderRouter;
import com.gigsarathi.domain.earnings.EarningsRecord;
import com.gigsarathi.domain.earnings.EarningsRepository;
import com.gigsarathi.domain.event.EventService;
import com.gigsarathi.domain.user.User;
import com.gigsarathi.domain.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class InactiveNudgeScheduler {

    private final UserRepository userRepository;
    private final EarningsRepository earningsRepository;
    private final MessageSenderRouter messageSender;
    private final EventService eventService;

    public InactiveNudgeScheduler(UserRepository userRepository,
                                  EarningsRepository earningsRepository,
                                  MessageSenderRouter messageSender,
                                  EventService eventService) {
        this.userRepository = userRepository;
        this.earningsRepository = earningsRepository;
        this.messageSender = messageSender;
        this.eventService = eventService;
    }

    @Scheduled(cron = "0 30 7 * * ?", zone = "Asia/Kolkata")
    public void sendInactiveNudge() {
        Instant cutoff14d = Instant.now().minus(14, ChronoUnit.DAYS);
        Instant cutoff2d = Instant.now().minus(2, ChronoUnit.DAYS);
        List<User> candidates = userRepository.findByStatusAndLastActiveAtAfter("ACTIVE", cutoff14d);
        log.info("InactiveNudgeScheduler: checking {} candidates", candidates.size());
        for (User user : candidates) {
            try {
                long recentCount = earningsRepository.countByUserIdAndPlatformAndSubmittedAtAfter(
                        user.getUserId(), user.getPlatform(), cutoff2d);
                if (recentCount > 0) {
                    continue;
                }
                String message = buildNudgeMessage(user);
                messageSender.sendMessage(user.getUserId(), user.getPlatform(), message);
                eventService.emit("inactive_nudge_sent", user.getUserId(), user.getPlatform(), Map.of());
            } catch (Exception ex) {
                log.error("Failed to send inactive nudge to {}/{}: {}",
                        user.getPlatform(), user.getUserId(), ex.getMessage());
            }
        }
    }

    private String buildNudgeMessage(User user) {
        List<EarningsRecord> recent = earningsRepository
                .findByUserIdAndPlatformOrderBySubmittedAtDesc(user.getUserId(), user.getPlatform());
        if (!recent.isEmpty()) {
            EarningsRecord last = recent.get(0);
            return "Hey! You haven't tracked earnings in a while. Last time you earned " +
                    last.getEarningsRange() + " — log today's shift and keep your streak going!";
        }
        return "Hey! You haven't tracked earnings recently. Log today's shift and start building your income record!";
    }
}
