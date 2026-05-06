package com.gigsarathi.flow;

import com.gigsarathi.bot.MessageSenderRouter;
import com.gigsarathi.config.AppConfigBootstrap;
import com.gigsarathi.domain.config.AppConfig;
import com.gigsarathi.domain.config.AppConfigRepository;
import com.gigsarathi.domain.event.EventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanFlowActionTest {

    @Mock private AppConfigRepository appConfigRepository;
    @Mock private LoanEligibilityService eligibilityService;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private MessageSenderRouter messageSender;
    @Mock private EventService eventService;
    @Mock private ValueOperations<String, String> valueOps;

    private LoanFlowAction action;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        action = new LoanFlowAction(appConfigRepository, eligibilityService, redisTemplate, messageSender, eventService);
    }

    private AppConfig loanEnabledConfig() {
        return AppConfig.builder().id(AppConfigBootstrap.CONFIG_ID).loanEnabled(true).build();
    }

    @Test
    @DisplayName("loan feature disabled — returns empty, no message sent")
    void apply_loanDisabled_returnsEmpty() {
        when(appConfigRepository.findById(AppConfigBootstrap.CONFIG_ID))
                .thenReturn(Optional.of(AppConfig.builder().id(AppConfigBootstrap.CONFIG_ID).loanEnabled(false).build()));

        Optional<SessionState> result = action.apply("user1", "whatsapp", null);

        assertThat(result).isEmpty();
        verifyNoInteractions(messageSender);
    }

    @Test
    @DisplayName("loan enabled but user not eligible — returns empty")
    void apply_notEligible_returnsEmpty() {
        when(appConfigRepository.findById(AppConfigBootstrap.CONFIG_ID)).thenReturn(Optional.of(loanEnabledConfig()));
        when(eligibilityService.isEligible("user1", "whatsapp")).thenReturn(false);

        Optional<SessionState> result = action.apply("user1", "whatsapp", null);

        assertThat(result).isEmpty();
        verifyNoInteractions(messageSender);
    }

    @Test
    @DisplayName("eligible but already offered within 30-day window — returns empty")
    void apply_alreadyOffered_returnsEmpty() {
        when(appConfigRepository.findById(AppConfigBootstrap.CONFIG_ID)).thenReturn(Optional.of(loanEnabledConfig()));
        when(eligibilityService.isEligible("user1", "whatsapp")).thenReturn(true);
        when(redisTemplate.hasKey(LoanFlowAction.LOAN_OFFERED_KEY_PREFIX + "whatsapp:user1")).thenReturn(true);

        Optional<SessionState> result = action.apply("user1", "whatsapp", null);

        assertThat(result).isEmpty();
        verifyNoInteractions(messageSender);
    }

    @Test
    @DisplayName("eligible and not yet offered — sends message, sets dedup key, returns LOAN session")
    void apply_eligible_sendsMessageAndReturnsLoanSession() {
        when(appConfigRepository.findById(AppConfigBootstrap.CONFIG_ID)).thenReturn(Optional.of(loanEnabledConfig()));
        when(eligibilityService.isEligible("user1", "whatsapp")).thenReturn(true);
        when(redisTemplate.hasKey(LoanFlowAction.LOAN_OFFERED_KEY_PREFIX + "whatsapp:user1")).thenReturn(false);

        Optional<SessionState> result = action.apply("user1", "whatsapp", null);

        assertThat(result).isPresent();
        assertThat(result.get().getFlowType()).isEqualTo(FlowType.LOAN.name());
        assertThat(result.get().getPreviousFlow()).isEqualTo(FlowType.DAILY_EARNINGS.name());
        assertThat(result.get().getStepIndex()).isZero();
        verify(messageSender).sendMessage(eq("user1"), eq("whatsapp"), anyString());
        verify(eventService).emit(eq("loan_offer_shown"), eq("user1"), eq("whatsapp"), any());
    }

    @Test
    @DisplayName("exception during eligibility check — returns empty (resilient)")
    void apply_eligibilityException_returnsEmpty() {
        when(appConfigRepository.findById(AppConfigBootstrap.CONFIG_ID)).thenReturn(Optional.of(loanEnabledConfig()));
        when(eligibilityService.isEligible(any(), any())).thenThrow(new RuntimeException("DB unavailable"));

        Optional<SessionState> result = action.apply("user1", "whatsapp", null);

        assertThat(result).isEmpty();
    }
}
