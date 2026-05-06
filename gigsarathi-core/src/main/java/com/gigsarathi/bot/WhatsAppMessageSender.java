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
public class WhatsAppMessageSender implements PlatformMessageSender {

    private static final String PLATFORM = "whatsapp";

    private final WebClient whatsAppWebClient;
    private final AppProperties appProperties;

    public WhatsAppMessageSender(@Qualifier("whatsAppWebClient") WebClient whatsAppWebClient,
                                 AppProperties appProperties) {
        this.whatsAppWebClient = whatsAppWebClient;
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
        body.put("messaging_product", "whatsapp");
        body.put("to", userId);
        body.put("type", "text");
        body.put("text", Map.of("body", text));
        post(body);
    }

    @Override
    public void sendButtonMessage(String userId, String platform, String text, List<String> buttons) {
        if (!supports(platform)) {
            return;
        }
        if (buttons == null || buttons.isEmpty()) {
            sendMessage(userId, platform, text);
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("to", userId);

        if (buttons.size() <= 3) {
            // interactive buttons
            List<Map<String, Object>> btns = new ArrayList<>();
            for (int i = 0; i < buttons.size(); i++) {
                String label = buttons.get(i);
                btns.add(Map.of(
                        "type", "reply",
                        "reply", Map.of(
                                "id", "btn_" + i,
                                "title", truncate(label, 20)
                        )
                ));
            }
            body.put("type", "interactive");
            body.put("interactive", Map.of(
                    "type", "button",
                    "body", Map.of("text", text),
                    "action", Map.of("buttons", btns)
            ));
        } else {
            // list message for >3 options
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = 0; i < buttons.size(); i++) {
                rows.add(Map.of(
                        "id", "row_" + i,
                        "title", truncate(buttons.get(i), 24)
                ));
            }
            List<Map<String, Object>> sections = List.of(Map.of(
                    "title", "Options",
                    "rows", rows
            ));
            body.put("type", "interactive");
            body.put("interactive", Map.of(
                    "type", "list",
                    "body", Map.of("text", text),
                    "action", Map.of(
                            "button", "Choose",
                            "sections", sections
                    )
            ));
        }

        post(body);
    }

    private void post(Map<String, Object> body) {
        String path = "/" + appProperties.getWhatsapp().getPhoneNumberId() + "/messages";
        try {
            whatsAppWebClient.post()
                    .uri(path)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.debug("WhatsApp send OK to {}", body.get("to"));
        } catch (Exception ex) {
            log.error("WhatsApp send failed: {}", ex.getMessage());
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
