package com.gigsarathi.scheduler;

import com.gigsarathi.domain.user.User;
import com.gigsarathi.domain.user.UserRepository;
import com.gigsarathi.flow.DailyEarningsFlow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
public class DailyEarningsScheduler {

    private final UserRepository userRepository;
    private final DailyEarningsFlow dailyEarningsFlow;

    public DailyEarningsScheduler(UserRepository userRepository, DailyEarningsFlow dailyEarningsFlow) {
        this.userRepository = userRepository;
        this.dailyEarningsFlow = dailyEarningsFlow;
    }

    @Scheduled(cron = "0 30 15 * * ?", zone = "Asia/Kolkata")
    public void sendDailyEarningsPrompt() {
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        List<User> eligible = userRepository.findByStatusAndLastActiveAtAfter("ACTIVE", cutoff);
        log.info("DailyEarningsScheduler: prompting {} eligible users", eligible.size());
        for (User user : eligible) {
            try {
                dailyEarningsFlow.sendEntryPrompt(user.getUserId(), user.getPlatform());
            } catch (Exception ex) {
                log.error("Failed to send daily prompt to {}/{}: {}",
                        user.getPlatform(), user.getUserId(), ex.getMessage());
            }
        }
    }
}
