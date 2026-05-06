package com.gigsarathi.flow;

import com.gigsarathi.domain.user.User;
import com.gigsarathi.domain.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class FlowEngine {

    private final SessionService sessionService;
    private final UserRepository userRepository;
    private final OnboardingFlow onboardingFlow;
    private final DailyEarningsFlow dailyEarningsFlow;
    private final OptOutHandler optOutHandler;

    public FlowEngine(SessionService sessionService,
                      UserRepository userRepository,
                      OnboardingFlow onboardingFlow,
                      DailyEarningsFlow dailyEarningsFlow,
                      OptOutHandler optOutHandler) {
        this.sessionService = sessionService;
        this.userRepository = userRepository;
        this.onboardingFlow = onboardingFlow;
        this.dailyEarningsFlow = dailyEarningsFlow;
        this.optOutHandler = optOutHandler;
    }

    public void handle(String userId, String platform, String text, String messageType,
                       Map<String, Object> payload) {
        // 1. STOP / START opt-out commands
        if (optOutHandler.isStopCommand(text)) {
            optOutHandler.handleStop(userId, platform);
            return;
        }
        if (optOutHandler.isStartCommand(text)) {
            optOutHandler.handleStart(userId, platform);
            return;
        }

        // 2. Get session
        Optional<SessionState> sessionOpt = sessionService.getSession(platform, userId);

        if (sessionOpt.isPresent()) {
            SessionState state = sessionOpt.get();
            FlowType flow = parseFlow(state.getFlowType());
            switch (flow) {
                case ONBOARDING -> onboardingFlow.handle(userId, platform, text, state);
                case DAILY_EARNINGS -> dailyEarningsFlow.handle(userId, platform, text, state);
                default -> {
                    sessionService.clearSession(platform, userId);
                    routeForNewUser(userId, platform);
                }
            }
            touchUser(userId, platform);
            return;
        }

        // 3. No session: check user existence
        Optional<User> existing = userRepository.findByPlatformAndUserId(platform, userId);
        if (existing.isEmpty() || !"COMPLETED".equals(existing.get().getOnboardingStatus())) {
            onboardingFlow.start(userId, platform);
        } else {
            dailyEarningsFlow.start(userId, platform);
        }
        touchUser(userId, platform);
    }

    private void routeForNewUser(String userId, String platform) {
        Optional<User> existing = userRepository.findByPlatformAndUserId(platform, userId);
        if (existing.isEmpty() || !"COMPLETED".equals(existing.get().getOnboardingStatus())) {
            onboardingFlow.start(userId, platform);
        } else {
            dailyEarningsFlow.start(userId, platform);
        }
    }

    private FlowType parseFlow(String name) {
        if (name == null) return FlowType.NONE;
        try {
            return FlowType.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return FlowType.NONE;
        }
    }

    private void touchUser(String userId, String platform) {
        userRepository.findByPlatformAndUserId(platform, userId).ifPresent(u -> {
            u.setLastActiveAt(Instant.now());
            userRepository.save(u);
        });
    }
}
