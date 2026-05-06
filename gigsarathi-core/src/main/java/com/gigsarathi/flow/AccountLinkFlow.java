package com.gigsarathi.flow;

import com.gigsarathi.bot.MessageSenderRouter;
import com.gigsarathi.domain.event.EventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class AccountLinkFlow {

    private final AccountLinkService accountLinkService;
    private final MessageSenderRouter messageSender;
    private final EventService eventService;

    public AccountLinkFlow(AccountLinkService accountLinkService,
                           MessageSenderRouter messageSender,
                           EventService eventService) {
        this.accountLinkService = accountLinkService;
        this.messageSender = messageSender;
        this.eventService = eventService;
    }

    public void startAsPrimary(String userId, String platform) {
        try {
            String code = accountLinkService.generateCode(userId, platform);
            messageSender.sendMessage(userId, platform,
                    "Your link code is *" + code + "*. " +
                    "Share it with your other account. It expires in 10 minutes.");
            eventService.emit("account_link_code_generated", userId, platform, Map.of("code", code));
        } catch (Exception e) {
            log.warn("AccountLinkFlow.startAsPrimary failed for {}/{}: {}", platform, userId, e.getMessage());
            messageSender.sendMessage(userId, platform,
                    "Sorry, couldn't generate a link code. Please try again.");
        }
    }

    public void linkAsSecondary(String code, String userId, String platform) {
        try {
            AccountLinkService.LinkResult result = accountLinkService.link(code, userId, platform);
            if (!result.found() || !result.valid()) {
                messageSender.sendMessage(userId, platform,
                        "Invalid or expired link code. Please ask for a new code.");
                return;
            }
            messageSender.sendMessage(userId, platform,
                    "Accounts linked! Your " + result.recordsMerged() +
                    " earning record(s) are now merged under your primary account.");
            eventService.emit("account_linked", userId, platform, Map.of(
                    "primaryUserId", result.primaryUserId(),
                    "recordsMerged", String.valueOf(result.recordsMerged())
            ));
        } catch (Exception e) {
            log.warn("AccountLinkFlow.linkAsSecondary failed for code={}, {}/{}: {}",
                    code, platform, userId, e.getMessage());
            messageSender.sendMessage(userId, platform,
                    "Sorry, couldn't complete linking. Please try again.");
        }
    }

    // Called when user is already in an ACCOUNT_LINK session and sends a follow-up
    public void handle(String userId, String platform, String text, SessionState state) {
        messageSender.sendMessage(userId, platform,
                "Type *LINK* to generate a new code, or *LINK <code>* to link your accounts.");
    }
}
