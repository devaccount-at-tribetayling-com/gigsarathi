package com.gigsarathi.flow;

import com.gigsarathi.bot.MessageSenderRouter;
import com.gigsarathi.domain.event.EventService;
import com.gigsarathi.domain.user.User;
import com.gigsarathi.domain.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class OptOutHandler {

    private final UserRepository userRepository;
    private final SessionService sessionService;
    private final EventService eventService;
    private final MessageSenderRouter messageSender;

    public OptOutHandler(UserRepository userRepository,
                         SessionService sessionService,
                         EventService eventService,
                         MessageSenderRouter messageSender) {
        this.userRepository = userRepository;
        this.sessionService = sessionService;
        this.eventService = eventService;
        this.messageSender = messageSender;
    }

    public boolean isStopCommand(String text) {
        return text != null && text.trim().equalsIgnoreCase("STOP");
    }

    public boolean isStartCommand(String text) {
        return text != null && text.trim().equalsIgnoreCase("START");
    }

    public void handleStop(String userId, String platform) {
        Optional<User> existing = userRepository.findByPlatformAndUserId(platform, userId);
        existing.ifPresent(u -> {
            u.setStatus("OPTED_OUT");
            u.setLastActiveAt(Instant.now());
            userRepository.save(u);
        });
        sessionService.clearSession(platform, userId);
        eventService.emit("opted_out", userId, platform, Map.of());
        messageSender.sendMessage(userId, platform,
                "You've been unsubscribed. Reply START to re-activate.");
    }

    public void handleStart(String userId, String platform) {
        Optional<User> existing = userRepository.findByPlatformAndUserId(platform, userId);
        existing.ifPresent(u -> {
            u.setStatus("ACTIVE");
            u.setLastActiveAt(Instant.now());
            userRepository.save(u);
        });
        eventService.emit("opted_in", userId, platform, Map.of());
        messageSender.sendMessage(userId, platform,
                "Welcome back! You'll receive your daily earnings prompt at 9PM.");
    }
}
