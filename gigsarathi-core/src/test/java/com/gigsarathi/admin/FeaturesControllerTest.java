package com.gigsarathi.admin;

import com.gigsarathi.config.AppConfigBootstrap;
import com.gigsarathi.domain.config.AppConfig;
import com.gigsarathi.domain.config.AppConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeaturesControllerTest {

    @Mock private AppConfigRepository appConfigRepository;

    private FeaturesController controller;

    @BeforeEach
    void setUp() {
        controller = new FeaturesController(appConfigRepository);
    }

    @Test
    @DisplayName("loanEnabled set to true — saved config reflects change")
    void updateFeatures_loanEnabled_savesCorrectly() {
        when(appConfigRepository.findById(AppConfigBootstrap.CONFIG_ID))
                .thenReturn(Optional.of(AppConfig.builder().id(AppConfigBootstrap.CONFIG_ID).build()));
        when(appConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FeaturesRequest req = new FeaturesRequest();
        req.setLoanEnabled(true);

        AppConfig result = controller.updateFeatures(req);

        assertThat(result.isLoanEnabled()).isTrue();
        assertThat(result.isReferralRewardEnabled()).isFalse();
    }

    @Test
    @DisplayName("referralRewardEnabled set to true — saved config reflects change")
    void updateFeatures_referralEnabled_savesCorrectly() {
        when(appConfigRepository.findById(AppConfigBootstrap.CONFIG_ID))
                .thenReturn(Optional.of(AppConfig.builder().id(AppConfigBootstrap.CONFIG_ID).build()));
        when(appConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FeaturesRequest req = new FeaturesRequest();
        req.setReferralRewardEnabled(true);

        AppConfig result = controller.updateFeatures(req);

        assertThat(result.isLoanEnabled()).isFalse();
        assertThat(result.isReferralRewardEnabled()).isTrue();
    }

    @Test
    @DisplayName("null fields in request — existing values not overwritten")
    void updateFeatures_nullFields_existingValuesPreserved() {
        AppConfig existing = AppConfig.builder()
                .id(AppConfigBootstrap.CONFIG_ID)
                .loanEnabled(true)
                .referralRewardEnabled(true)
                .build();
        when(appConfigRepository.findById(AppConfigBootstrap.CONFIG_ID)).thenReturn(Optional.of(existing));
        when(appConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AppConfig result = controller.updateFeatures(new FeaturesRequest());

        assertThat(result.isLoanEnabled()).isTrue();
        assertThat(result.isReferralRewardEnabled()).isTrue();
    }

    @Test
    @DisplayName("no existing config — creates new document and applies updates")
    void updateFeatures_noExistingConfig_createsNew() {
        when(appConfigRepository.findById(AppConfigBootstrap.CONFIG_ID)).thenReturn(Optional.empty());
        when(appConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FeaturesRequest req = new FeaturesRequest();
        req.setLoanEnabled(true);

        AppConfig result = controller.updateFeatures(req);

        assertThat(result.getId()).isEqualTo(AppConfigBootstrap.CONFIG_ID);
        assertThat(result.isLoanEnabled()).isTrue();
    }
}
