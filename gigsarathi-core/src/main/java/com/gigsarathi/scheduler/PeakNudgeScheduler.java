package com.gigsarathi.scheduler;

import com.gigsarathi.bot.MessageSenderRouter;
import com.gigsarathi.domain.event.EventService;
import com.gigsarathi.domain.user.User;
import com.gigsarathi.domain.user.UserRepository;
import com.gigsarathi.domain.zone.ZoneHeuristic;
import com.gigsarathi.intelligence.HotnessScoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class PeakNudgeScheduler {

    private final UserRepository userRepository;
    private final HotnessScoreService hotnessScoreService;
    private final MessageSenderRouter messageSender;
    private final EventService eventService;

    public PeakNudgeScheduler(UserRepository userRepository,
                               HotnessScoreService hotnessScoreService,
                               MessageSenderRouter messageSender,
                               EventService eventService) {
        this.userRepository = userRepository;
        this.hotnessScoreService = hotnessScoreService;
        this.messageSender = messageSender;
        this.eventService = eventService;
    }

    @Scheduled(cron = "0 30 8 * * ?", zone = "Asia/Kolkata")
    public void sendPeakNudge() {
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        List<User> eligible = userRepository.findByStatusAndLastActiveAtAfter("ACTIVE", cutoff);
        log.info("PeakNudgeScheduler: nudging {} eligible users", eligible.size());
        LocalDate today = LocalDate.now();
        for (User user : eligible) {
            try {
                String message = buildNudgeMessage(user, today);
                messageSender.sendMessage(user.getUserId(), user.getPlatform(), message);
                eventService.emit("peak_nudge_sent", user.getUserId(), user.getPlatform(), Map.of());
            } catch (Exception ex) {
                log.error("Failed to send peak nudge to {}/{}: {}",
                        user.getPlatform(), user.getUserId(), ex.getMessage());
            }
        }
    }

    private String buildNudgeMessage(User user, LocalDate date) {
        if (user.getCity() == null || user.getCity().isBlank()) {
            return "Peak hours are coming up! Zones are in high demand. Start your shift now.";
        }
        List<ZoneHeuristic> top = hotnessScoreService.topZones(user.getCity(), date);
        if (top.isEmpty()) {
            return "Peak hours are coming up! Zones are in high demand. Start your shift now.";
        }
        String zoneNames = top.stream()
                .limit(3)
                .map(ZoneHeuristic::getZone)
                .collect(Collectors.joining(", "));
        return "Peak hours are coming up! Top zones right now: " + zoneNames + ". Start your shift now.";
    }
}
