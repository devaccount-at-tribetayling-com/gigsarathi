package com.gigsarathi.flow;

import com.gigsarathi.bot.MessageSenderRouter;
import com.gigsarathi.domain.event.EventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class LoanFlow {

    private final SessionService sessionService;
    private final MessageSenderRouter messageSender;
    private final EventService eventService;

    public LoanFlow(SessionService sessionService,
                    MessageSenderRouter messageSender,
                    EventService eventService) {
        this.sessionService = sessionService;
        this.messageSender = messageSender;
        this.eventService = eventService;
    }

    public void handle(String userId, String platform, String text, SessionState state) {
        String answer = text == null ? "" : text.trim().toUpperCase();
        switch (answer) {
            case "YES" -> {
                eventService.emit("loan_offer_clicked", userId, platform, Map.of());
                messageSender.sendMessage(userId, platform,
                        "Great! A loan partner will contact you shortly. Stay tuned!");
                sessionService.clearSession(platform, userId);
            }
            case "NO" -> {
                messageSender.sendMessage(userId, platform,
                        "No problem! Keep tracking your earnings. We'll check again next time.");
                sessionService.clearSession(platform, userId);
            }
            default -> messageSender.sendMessage(userId, platform,
                    "Please reply *YES* or *NO*.");
        }
    }
}
