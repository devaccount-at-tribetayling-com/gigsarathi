package com.gigsarathi.flow;

import com.gigsarathi.bot.MessageSenderRouter;
import com.gigsarathi.domain.earnings.EarningsRecord;
import com.gigsarathi.domain.earnings.EarningsRepository;
import com.gigsarathi.domain.event.EventService;
import com.gigsarathi.domain.user.User;
import com.gigsarathi.domain.user.UserRepository;
import com.gigsarathi.domain.zone.ZoneHeuristic;
import com.gigsarathi.domain.zone.ZoneHeuristicRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DailyEarningsFlow {

    private static final List<String> ORDER_BUTTONS =
            List.of("Less than 10", "10–15", "15–20", "20+");
    private static final List<String> EARNINGS_BUTTONS =
            List.of("₹0–500", "₹500–1000", "₹1000–1500", "₹1500+");
    private static final List<String> FUEL_BUTTONS =
            List.of("₹0–100", "₹100–200", "₹200+");

    private final SessionService sessionService;
    private final EarningsRepository earningsRepository;
    private final UserRepository userRepository;
    private final ZoneHeuristicRepository zoneRepository;
    private final EventService eventService;
    private final MessageSenderRouter messageSender;
    private final List<PostDailySummaryAction> actions;

    public DailyEarningsFlow(SessionService sessionService,
                             EarningsRepository earningsRepository,
                             UserRepository userRepository,
                             ZoneHeuristicRepository zoneRepository,
                             EventService eventService,
                             MessageSenderRouter messageSender,
                             List<PostDailySummaryAction> actions) {
        this.sessionService = sessionService;
        this.earningsRepository = earningsRepository;
        this.userRepository = userRepository;
        this.zoneRepository = zoneRepository;
        this.eventService = eventService;
        this.messageSender = messageSender;
        this.actions = actions;
    }

    /**
     * Scheduler-initiated entry: starts the flow without checking session state.
     */
    public void sendEntryPrompt(String userId, String platform) {
        startFlow(userId, platform, false);
    }

    /**
     * User-initiated entry (from FlowEngine).
     */
    public void start(String userId, String platform) {
        startFlow(userId, platform, true);
    }

    private void startFlow(String userId, String platform, boolean checkDedup) {
        Instant cutoff = Instant.now().minus(20, ChronoUnit.HOURS);
        List<EarningsRecord> recent = earningsRepository
                .findByUserIdAndPlatformAndSubmittedAtAfter(userId, platform, cutoff);

        if (checkDedup && !recent.isEmpty()) {
            Map<String, Object> pending = new HashMap<>();
            pending.put("waitingForReplace", true);
            SessionState state = SessionState.builder()
                    .flowType(FlowType.DAILY_EARNINGS.name())
                    .stepIndex(0)
                    .pendingData(pending)
                    .startedAt(Instant.now().toString())
                    .build();
            sessionService.saveSession(platform, userId, state);
            messageSender.sendMessage(userId, platform,
                    "Replace today's record? Reply YES or NO");
            return;
        }

        SessionState state = SessionState.builder()
                .flowType(FlowType.DAILY_EARNINGS.name())
                .stepIndex(1)
                .pendingData(new HashMap<>())
                .startedAt(Instant.now().toString())
                .build();
        sessionService.saveSession(platform, userId, state);
        messageSender.sendButtonMessage(userId, platform,
                "How many orders did you complete today?", ORDER_BUTTONS);
    }

    public void handle(String userId, String platform, String text, SessionState state) {
        // Step 0 = waiting for YES/NO replace decision
        if (state.getStepIndex() == 0
                && Boolean.TRUE.equals(state.getPendingData().get("waitingForReplace"))) {
            handleReplaceDecision(userId, platform, text, state);
            return;
        }

        switch (state.getStepIndex()) {
            case 1 -> handleOrders(userId, platform, text, state);
            case 2 -> handleEarnings(userId, platform, text, state);
            case 3 -> handleFuel(userId, platform, text, state);
            case 4 -> handleZone(userId, platform, text, state);
            default -> start(userId, platform);
        }
    }

    private void handleReplaceDecision(String userId, String platform, String text, SessionState state) {
        String answer = text == null ? "" : text.trim();
        if (answer.equalsIgnoreCase("YES")) {
            state.setStepIndex(1);
            state.getPendingData().remove("waitingForReplace");
            sessionService.saveSession(platform, userId, state);
            messageSender.sendButtonMessage(userId, platform,
                    "How many orders did you complete today?", ORDER_BUTTONS);
        } else if (answer.equalsIgnoreCase("NO")) {
            sessionService.clearSession(platform, userId);
            messageSender.sendMessage(userId, platform,
                    "Got it — keeping your existing record for today.");
        } else {
            messageSender.sendMessage(userId, platform,
                    "Please reply YES or NO.");
        }
    }

    private void handleOrders(String userId, String platform, String text, SessionState state) {
        String range = normalizeOrdersRange(text);
        state.getPendingData().put("ordersRange", range);
        state.setStepIndex(2);
        sessionService.saveSession(platform, userId, state);
        messageSender.sendButtonMessage(userId, platform,
                "What were your gross earnings?", EARNINGS_BUTTONS);
    }

    private void handleEarnings(String userId, String platform, String text, SessionState state) {
        String range = normalizeEarningsRange(text);
        state.getPendingData().put("earningsRange", range);
        state.setStepIndex(3);
        sessionService.saveSession(platform, userId, state);
        messageSender.sendButtonMessage(userId, platform,
                "What did you spend on fuel?", FUEL_BUTTONS);
    }

    private void handleFuel(String userId, String platform, String text, SessionState state) {
        String range = normalizeFuelRange(text);
        state.getPendingData().put("fuelRange", range);
        state.setStepIndex(4);
        sessionService.saveSession(platform, userId, state);

        // get user's city
        Optional<User> userOpt = userRepository.findByPlatformAndUserId(platform, userId);
        String city = userOpt.map(User::getCity).orElse("Kolhapur");

        List<String> zoneOptions = zoneRepository.findByCityIgnoreCaseAndActiveTrue(city).stream()
                .sorted(Comparator.comparingInt(ZoneHeuristic::getRecommendationPriority))
                .limit(3)
                .map(ZoneHeuristic::getZone)
                .collect(Collectors.toList());

        if (zoneOptions.isEmpty()) {
            messageSender.sendMessage(userId, platform,
                    "Which zone did you work in today? Type the zone name.");
        } else {
            messageSender.sendButtonMessage(userId, platform,
                    "Which zone did you work in today?", zoneOptions);
        }
    }

    private void handleZone(String userId, String platform, String text, SessionState state) {
        String zone = text == null ? "" : text.trim();
        state.getPendingData().put("zone", zone);

        String ordersRange = (String) state.getPendingData().get("ordersRange");
        String earningsRange = (String) state.getPendingData().get("earningsRange");
        String fuelRange = (String) state.getPendingData().get("fuelRange");

        int ordersCount = ordersMidpoint(ordersRange);
        double earningsAmount = earningsMidpoint(earningsRange);
        double fuelAmount = fuelMidpoint(fuelRange);

        Instant now = Instant.now();
        EarningsRecord record = EarningsRecord.builder()
                .userId(userId)
                .platform(platform)
                .ordersRange(ordersRange)
                .ordersCount(ordersCount)
                .earningsRange(earningsRange)
                .earningsAmount(earningsAmount)
                .fuelRange(fuelRange)
                .fuelAmount(fuelAmount)
                .zone(zone)
                .submittedAt(now)
                .updatedAt(now)
                .build();
        earningsRepository.save(record);

        // refresh lastActiveAt
        userRepository.findByPlatformAndUserId(platform, userId).ifPresent(u -> {
            u.setLastActiveAt(now);
            userRepository.save(u);
        });

        eventService.emit("daily_record_submitted", userId, platform, Map.of(
                "ordersRange", ordersRange == null ? "" : ordersRange,
                "earningsRange", earningsRange == null ? "" : earningsRange,
                "fuelRange", fuelRange == null ? "" : fuelRange,
                "zone", zone
        ));

        // build summary
        List<EarningsRecord> history = earningsRepository
                .findByUserIdAndPlatformOrderBySubmittedAtDesc(userId, platform);

        StringBuilder sb = new StringBuilder();
        sb.append("📊 Today's summary:\n");
        sb.append("Gross earnings: ").append(earningsRange).append("\n");
        sb.append("Fuel: ").append(fuelRange).append("\n");
        double realProfit = earningsAmount - fuelAmount;
        sb.append("Real profit (est): ₹").append(String.format("%.0f", realProfit)).append("\n");

        if (history.size() >= 3) {
            double avgProfit = history.stream()
                    .mapToDouble(r -> r.getEarningsAmount() - r.getFuelAmount())
                    .average()
                    .orElse(0.0);
            sb.append(String.format("Your average profit: ₹%.0f", avgProfit));
            if (realProfit > avgProfit) {
                sb.append(" — today is above average! 🎉");
            } else if (realProfit < avgProfit) {
                sb.append(" — today is below your average.");
            }
        }

        messageSender.sendMessage(userId, platform, sb.toString());
        eventService.emit("daily_summary_viewed", userId, platform, Map.of());

        SessionState chained = null;
        for (PostDailySummaryAction action : actions) {
            Optional<SessionState> result = action.apply(userId, platform, record);
            if (result.isPresent()) {
                chained = result.get();
                break;
            }
        }

        if (chained != null) {
            sessionService.saveSession(platform, userId, chained);
        } else {
            sessionService.clearSession(platform, userId);
        }
    }

    // ----- midpoint helpers -----

    private String normalizeOrdersRange(String text) {
        if (text == null) return "<10";
        String t = text.trim().toLowerCase();
        if (t.contains("less") || t.startsWith("<")) return "<10";
        if (t.contains("10-15") || t.contains("10–15")) return "10-15";
        if (t.contains("15-20") || t.contains("15–20")) return "15-20";
        if (t.contains("20+") || t.contains("20")) return "20+";
        return "<10";
    }

    private String normalizeEarningsRange(String text) {
        if (text == null) return "₹0-500";
        String t = text.trim();
        if (t.contains("0-500") || t.contains("0–500")) return "₹0-500";
        if (t.contains("500-1000") || t.contains("500–1000")) return "₹500-1000";
        if (t.contains("1000-1500") || t.contains("1000–1500")) return "₹1000-1500";
        if (t.contains("1500+") || t.contains("1500")) return "₹1500+";
        return "₹0-500";
    }

    private String normalizeFuelRange(String text) {
        if (text == null) return "₹0-100";
        String t = text.trim();
        if (t.contains("0-100") || t.contains("0–100")) return "₹0-100";
        if (t.contains("100-200") || t.contains("100–200")) return "₹100-200";
        if (t.contains("200+") || t.contains("200")) return "₹200+";
        return "₹0-100";
    }

    private int ordersMidpoint(String range) {
        if (range == null) return 5;
        return switch (range) {
            case "<10" -> 5;
            case "10-15" -> 12;
            case "15-20" -> 17;
            case "20+" -> 22;
            default -> 5;
        };
    }

    private double earningsMidpoint(String range) {
        if (range == null) return 250.0;
        return switch (range) {
            case "₹0-500" -> 250.0;
            case "₹500-1000" -> 750.0;
            case "₹1000-1500" -> 1250.0;
            case "₹1500+" -> 1750.0;
            default -> 250.0;
        };
    }

    private double fuelMidpoint(String range) {
        if (range == null) return 50.0;
        return switch (range) {
            case "₹0-100" -> 50.0;
            case "₹100-200" -> 150.0;
            case "₹200+" -> 250.0;
            default -> 50.0;
        };
    }
}
