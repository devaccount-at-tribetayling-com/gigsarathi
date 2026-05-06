package com.gigsarathi.bot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class MessageSenderRouter {

    private final List<PlatformMessageSender> senders;

    public MessageSenderRouter(List<PlatformMessageSender> senders) {
        this.senders = senders;
    }

    public void sendMessage(String userId, String platform, String text) {
        PlatformMessageSender sender = resolve(platform);
        if (sender == null) {
            log.warn("No sender registered for platform={}", platform);
            return;
        }
        sender.sendMessage(userId, platform, text);
    }

    public void sendButtonMessage(String userId, String platform, String text, List<String> buttons) {
        PlatformMessageSender sender = resolve(platform);
        if (sender == null) {
            log.warn("No sender registered for platform={}", platform);
            return;
        }
        sender.sendButtonMessage(userId, platform, text, buttons);
    }

    private PlatformMessageSender resolve(String platform) {
        for (PlatformMessageSender s : senders) {
            if (s.supports(platform)) {
                return s;
            }
        }
        return null;
    }
}
