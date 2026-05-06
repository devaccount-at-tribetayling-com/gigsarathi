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
import java.util.stream.Collectors;

@Slf4j
@Component
public class WeeklyReportScheduler {

    private final UserRepository userRepository;
    private final EarningsRepository earningsRepository;
    private final MessageSenderRouter messageSender;
    private final EventService eventService;

    public WeeklyReportScheduler(UserRepository userRepository,
                                 EarningsRepository earningsRepository,
                                 MessageSenderRouter messageSender,
                                 EventService eventService) {
        this.userRepository = userRepository;
        this.earningsRepository = earningsRepository;
        this.messageSender = messageSender;
        this.eventService = eventService;
    }

    @Scheduled(cron = "0 30 19 * * SUN", zone = "Asia/Kolkata")
    public void sendWeeklyReport() {
        Instant cutoff30d = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant cutoff7d = Instant.now().minus(7, ChronoUnit.DAYS);
        List<User> eligible = userRepository.findByStatusAndLastActiveAtAfter("ACTIVE", cutoff30d);
        log.info("WeeklyReportScheduler: reporting to {} eligible users", eligible.size());
        for (User user : eligible) {
            try {
                List<EarningsRecord> records = earningsRepository
                        .findByUserIdAndPlatformAndSubmittedAtAfter(
                                user.getUserId(), user.getPlatform(), cutoff7d);
                String message = buildReport(records);
                messageSender.sendMessage(user.getUserId(), user.getPlatform(), message);
                eventService.emit("weekly_report_sent", user.getUserId(), user.getPlatform(), Map.of());
            } catch (Exception ex) {
                log.error("Failed to send weekly report to {}/{}: {}",
                        user.getPlatform(), user.getUserId(), ex.getMessage());
            }
        }
    }

    private String buildReport(List<EarningsRecord> records) {
        if (records.size() < 2) {
            return "Track your earnings this week to see your weekly report! " +
                   "Log at least 2 shifts to unlock insights.";
        }
        double totalEarnings = records.stream().mapToDouble(EarningsRecord::getEarningsAmount).sum();
        double totalFuel = records.stream().mapToDouble(EarningsRecord::getFuelAmount).sum();
        int totalOrders = records.stream().mapToInt(EarningsRecord::getOrdersCount).sum();
        double netEarnings = totalEarnings - totalFuel;
        double avgPerShift = totalEarnings / records.size();
        String bestZone = records.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getZone() != null ? r.getZone() : "Unknown",
                        Collectors.summingDouble(EarningsRecord::getEarningsAmount)))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("N/A");

        return String.format(
                "*Your Weekly Summary*%n" +
                "1. Shifts tracked: %d%n" +
                "2. Total earnings: ₹%.0f%n" +
                "3. Fuel costs: ₹%.0f | Net: ₹%.0f%n" +
                "4. Avg per shift: ₹%.0f%n" +
                "5. Best zone: %s | Total orders: %d",
                records.size(), totalEarnings, totalFuel, netEarnings, avgPerShift, bestZone, totalOrders);
    }
}
