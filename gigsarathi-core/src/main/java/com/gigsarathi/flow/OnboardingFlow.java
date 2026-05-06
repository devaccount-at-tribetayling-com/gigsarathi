package com.gigsarathi.flow;

import com.gigsarathi.bot.MessageSenderRouter;
import com.gigsarathi.domain.event.EventService;
import com.gigsarathi.domain.user.User;
import com.gigsarathi.domain.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OnboardingFlow {

    private static final List<String> WORK_TYPE_BUTTONS =
            List.of("Food Delivery", "Ride", "Courier", "Multiple");
    private static final List<String> APP_BUTTONS =
            List.of("Swiggy", "Zomato", "Zepto", "Uber Eats", "Other");

    private final SessionService sessionService;
    private final UserRepository userRepository;
    private final EventService eventService;
    private final MessageSenderRouter messageSender;

    public OnboardingFlow(SessionService sessionService,
                          UserRepository userRepository,
                          EventService eventService,
                          MessageSenderRouter messageSender) {
        this.sessionService = sessionService;
        this.userRepository = userRepository;
        this.eventService = eventService;
        this.messageSender = messageSender;
    }

    public void start(String userId, String platform) {
        eventService.emit("onboarding_started", userId, platform, Map.of());
        SessionState state = SessionState.builder()
                .flowType(FlowType.ONBOARDING.name())
                .stepIndex(1)
                .pendingData(new HashMap<>())
                .startedAt(Instant.now().toString())
                .build();
        sessionService.saveSession(platform, userId, state);
        messageSender.sendButtonMessage(userId, platform,
                "Welcome to Gigsarathi! 🛵 What type of gig work do you do?",
                WORK_TYPE_BUTTONS);
    }

    public void handle(String userId, String platform, String text, SessionState state) {
        switch (state.getStepIndex()) {
            case 1 -> handleWorkType(userId, platform, text, state);
            case 2 -> handleApps(userId, platform, text, state);
            case 3 -> handleCity(userId, platform, text, state);
            default -> start(userId, platform);
        }
    }

    private void handleWorkType(String userId, String platform, String text, SessionState state) {
        String workType = text == null ? "" : text.trim();
        state.getPendingData().put("workType", workType);

        if ("Food Delivery".equalsIgnoreCase(workType)) {
            state.setStepIndex(2);
            sessionService.saveSession(platform, userId, state);
            messageSender.sendButtonMessage(userId, platform,
                    "Which apps do you use? (Select all that apply)",
                    APP_BUTTONS);
        } else {
            state.setStepIndex(3);
            sessionService.saveSession(platform, userId, state);
            messageSender.sendMessage(userId, platform,
                    "Which city are you based in? (e.g. Kolhapur)");
        }
    }

    private void handleApps(String userId, String platform, String text, SessionState state) {
        String raw = text == null ? "" : text.trim();
        List<String> apps = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        state.getPendingData().put("appsUsed", apps);
        state.setStepIndex(3);
        sessionService.saveSession(platform, userId, state);
        messageSender.sendMessage(userId, platform,
                "Which city are you based in? Type your city name.");
    }

    @SuppressWarnings("unchecked")
    private void handleCity(String userId, String platform, String text, SessionState state) {
        String city = text == null ? "" : text.trim();
        state.getPendingData().put("city", city);

        Optional<User> existing = userRepository.findByPlatformAndUserId(platform, userId);
        Instant now = Instant.now();

        String workType = (String) state.getPendingData().get("workType");
        Object appsObj = state.getPendingData().get("appsUsed");
        List<String> appsUsed = appsObj instanceof List<?> l
                ? l.stream().map(Object::toString).collect(Collectors.toList())
                : List.of();

        User user = existing.orElseGet(() -> User.builder()
                .userId(userId)
                .platform(platform)
                .createdAt(now)
                .build());
        user.setWorkType(workType);
        user.setAppsUsed(appsUsed);
        user.setCity(city);
        user.setOnboardingStatus("COMPLETED");
        user.setStatus("ACTIVE");
        user.setLastActiveAt(now);
        if (user.getCreatedAt() == null) {
            user.setCreatedAt(now);
        }
        if ("whatsapp".equalsIgnoreCase(platform)) {
            user.setPhoneNumber(userId);
        }
        userRepository.save(user);

        eventService.emit("onboarding_completed", userId, platform, Map.of(
                "workType", workType == null ? "" : workType,
                "city", city
        ));

        sessionService.clearSession(platform, userId);
        messageSender.sendMessage(userId, platform,
                "✅ You're all set! I'll check in with you at 9PM to track today's earnings.");
    }
}
