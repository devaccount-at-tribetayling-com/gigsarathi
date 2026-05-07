package com.gigsarathi.admin;

import com.gigsarathi.config.AppConfigBootstrap;
import com.gigsarathi.domain.config.AppConfig;
import com.gigsarathi.domain.config.AppConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/admin")
public class FeaturesController {

    private final AppConfigRepository appConfigRepository;

    public FeaturesController(AppConfigRepository appConfigRepository) {
        this.appConfigRepository = appConfigRepository;
    }

    @PatchMapping("/features")
    public AppConfig updateFeatures(@RequestBody FeaturesRequest request) {
        AppConfig config = appConfigRepository.findById(AppConfigBootstrap.CONFIG_ID)
                .orElseGet(() -> AppConfig.builder().id(AppConfigBootstrap.CONFIG_ID).build());
        if (request.getLoanEnabled() != null) {
            config.setLoanEnabled(request.getLoanEnabled());
        }
        if (request.getReferralRewardEnabled() != null) {
            config.setReferralRewardEnabled(request.getReferralRewardEnabled());
        }
        AppConfig saved = appConfigRepository.save(config);
        log.info("features updated: loanEnabled={}, referralRewardEnabled={}",
                saved.isLoanEnabled(), saved.isReferralRewardEnabled());
        return saved;
    }
}
