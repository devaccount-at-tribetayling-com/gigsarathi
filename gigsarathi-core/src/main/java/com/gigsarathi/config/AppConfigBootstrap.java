package com.gigsarathi.config;

import com.gigsarathi.domain.config.AppConfig;
import com.gigsarathi.domain.config.AppConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.bootstrap.enabled", havingValue = "true", matchIfMissing = true)
public class AppConfigBootstrap {

    public static final String CONFIG_ID = "singleton";

    private final AppConfigRepository appConfigRepository;

    public AppConfigBootstrap(AppConfigRepository appConfigRepository) {
        this.appConfigRepository = appConfigRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrapDefaults() {
        if (appConfigRepository.findById(CONFIG_ID).isEmpty()) {
            AppConfig defaults = AppConfig.builder()
                    .id(CONFIG_ID)
                    .loanEnabled(false)
                    .referralRewardEnabled(false)
                    .build();
            appConfigRepository.save(defaults);
            log.info("app_config bootstrapped with defaults");
        }
    }
}
