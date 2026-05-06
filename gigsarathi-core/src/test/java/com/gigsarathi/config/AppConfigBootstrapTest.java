package com.gigsarathi.config;

import com.gigsarathi.domain.config.AppConfig;
import com.gigsarathi.domain.config.AppConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppConfigBootstrapTest {

    @Mock private AppConfigRepository appConfigRepository;

    private AppConfigBootstrap bootstrap;

    @BeforeEach
    void setUp() {
        bootstrap = new AppConfigBootstrap(appConfigRepository);
    }

    @Test
    @DisplayName("no existing config — saves defaults with id=singleton, both flags false")
    void bootstrapDefaults_noExistingConfig_savesDefaults() {
        when(appConfigRepository.findById(AppConfigBootstrap.CONFIG_ID)).thenReturn(Optional.empty());

        bootstrap.bootstrapDefaults();

        ArgumentCaptor<AppConfig> captor = ArgumentCaptor.forClass(AppConfig.class);
        verify(appConfigRepository).save(captor.capture());
        AppConfig saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(AppConfigBootstrap.CONFIG_ID);
        assertThat(saved.isLoanEnabled()).isFalse();
        assertThat(saved.isReferralRewardEnabled()).isFalse();
    }

    @Test
    @DisplayName("config already exists — save never called")
    void bootstrapDefaults_existingConfig_saveNeverCalled() {
        AppConfig existing = AppConfig.builder()
                .id(AppConfigBootstrap.CONFIG_ID)
                .loanEnabled(true)
                .referralRewardEnabled(true)
                .build();
        when(appConfigRepository.findById(AppConfigBootstrap.CONFIG_ID)).thenReturn(Optional.of(existing));

        bootstrap.bootstrapDefaults();

        verify(appConfigRepository, never()).save(any());
    }
}
