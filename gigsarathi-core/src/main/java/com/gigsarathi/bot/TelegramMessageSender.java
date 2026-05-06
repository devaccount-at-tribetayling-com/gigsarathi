package com.gigsarathi.bot;

import com.gigsarathi.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class TelegramMessageSender implements PlatformMessageSender {

    private static final String PLATFORM = "telegram";

    private final WebClient telegramWebClient;
    private final AppProperties appProperties;

    public TelegramMessageSender(@Qualifier("telegramWebClient") WebClient telegramWebClient,
                                 AppProperties appProperties) {
        this.telegramWebClient = telegramWebClient;
        this.appProperties = appProperties;
    }

    @Override
    public boolean supports(String platform) {
        return PLATFORM.equalsIgnoreCase(platform);
    }

    @Override
    public void sendMessage(String userId, String platform, String text) {
        if (!supports(platform)) {
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", userId);
        body.put("text", text);
        post("sendMessage", body);
    }

    @Override
    public void sendButtonMessage(String userId, String platform, String text, List<String> buttons) {
        if (!supports(platform)) {
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", userId);
        body.put("text", text);

        if (buttons != null && !buttons.isEmpty()) {
            List<List<Map<String, String>>> keyboard = new ArrayList<>();
            for (String label : buttons) {
                keyboard.add(List.of(Map.of("text", label)));
            }
            Map<String, Object> replyMarkup = new HashMap<>();
            replyMarkup.put("keyboard", keyboard);
            replyMarkup.put("one_time_keyboard", true);
            replyMarkup.put("resize_keyboard", true);
            body.put("reply_markup", replyMarkup);
        }

        post("sendMessage", body);
    }

    private void post(String method, Map<String, Object> body) {
        String path = "/bot" + appProperties.getTelegram().getBotToken() + "/" + method;
        try {
            telegramWebClient.post()
                    .uri(path)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.debug("Telegram send OK to {}", body.get("chat_id"));
        } catch (Exception ex) {
            log.error("Telegram send failed: {}", ex.getMessage());
        }
    }
}
