package com.gigsarathi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "gigsarathi")
public class AppProperties {

    private WhatsApp whatsapp = new WhatsApp();
    private Telegram telegram = new Telegram();
    private Admin admin = new Admin();

    @Data
    public static class WhatsApp {
        private String apiUrl;
        private String phoneNumberId;
        private String accessToken;
    }

    @Data
    public static class Telegram {
        private String botToken;
        private String botUsername;
    }

    @Data
    public static class Admin {
        private String apiKey;
    }
}
