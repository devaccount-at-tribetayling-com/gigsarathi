package com.gigsarathi.flow;

import com.gigsarathi.bot.MessageSenderRouter;
import com.gigsarathi.domain.earnings.EarningsRecord;
import com.gigsarathi.domain.earnings.EarningsRepository;
import com.gigsarathi.domain.event.EventService;
import com.gigsarathi.domain.referral.ReferralCode;
import com.gigsarathi.referral.ReferralService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferralPromptActionTest {

    @Mock private EarningsRepository earningsRepository;
    @Mock private ReferralService referralService;
    @Mock private EventService eventService;
    @Mock private MessageSenderRouter messageSender;

    private ReferralPromptAction action;
    private EarningsRecord dummyRecord;

    @BeforeEach
    void setUp() {
        action = new ReferralPromptAction(earningsRepository, referralService, eventService, messageSender);
        dummyRecord = new EarningsRecord();
    }

    @Test
    @DisplayName("count >= 3: sends message with code, emits event, returns REFERRAL session")
    void apply_experiencedUser_returnsReferralSession() {
        when(earningsRepository.countByUserIdAndPlatform("user1", "whatsapp")).thenReturn(3L);
        ReferralCode code = new ReferralCode();
        code.setCode("ABCD1234");
        when(referralService.ensureReferralCode("user1", "whatsapp")).thenReturn(code);

        Optional<SessionState> result = action.apply("user1", "whatsapp", dummyRecord);

        assertThat(result).isPresent();
        assertThat(result.get().getFlowType()).isEqualTo(FlowType.REFERRAL.name());
        assertThat(result.get().getStepIndex()).isEqualTo(0);
        assertThat(result.get().getPreviousFlow()).isEqualTo(FlowType.DAILY_EARNINGS.name());
        verify(messageSender).sendMessage(eq("user1"), eq("whatsapp"),
                argThat(msg -> msg.contains("ABCD1234")));
        verify(eventService).emit(eq("referral_prompt_sent"), eq("user1"), eq("whatsapp"), any());
    }

    @Test
    @DisplayName("count > 3: still fires referral prompt")
    void apply_heavyUser_returnsReferralSession() {
        when(earningsRepository.countByUserIdAndPlatform("user2", "whatsapp")).thenReturn(10L);
        ReferralCode code = new ReferralCode();
        code.setCode("XYZ99999");
        when(referralService.ensureReferralCode("user2", "whatsapp")).thenReturn(code);

        Optional<SessionState> result = action.apply("user2", "whatsapp", dummyRecord);

        assertThat(result).isPresent();
        assertThat(result.get().getFlowType()).isEqualTo(FlowType.REFERRAL.name());
    }

    @Test
    @DisplayName("count < 3: defers to next action — returns empty")
    void apply_newUser_returnsEmpty() {
        when(earningsRepository.countByUserIdAndPlatform("user3", "whatsapp")).thenReturn(2L);

        Optional<SessionState> result = action.apply("user3", "whatsapp", dummyRecord);

        assertThat(result).isEmpty();
        verify(messageSender, never()).sendMessage(any(), any(), any());
        verify(eventService, never()).emit(any(), any(), any(), any());
        verify(referralService, never()).ensureReferralCode(any(), any());
    }

    @Test
    @DisplayName("count == 0: first-time user defers to TomorrowPlanAction")
    void apply_firstTimeUser_returnsEmpty() {
        when(earningsRepository.countByUserIdAndPlatform("user4", "whatsapp")).thenReturn(0L);

        Optional<SessionState> result = action.apply("user4", "whatsapp", dummyRecord);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("exception safety: repository failure returns empty, no re-throw")
    void apply_repositoryException_returnsEmpty() {
        when(earningsRepository.countByUserIdAndPlatform(any(), any()))
                .thenThrow(new RuntimeException("DB down"));

        Optional<SessionState> result = action.apply("user5", "whatsapp", dummyRecord);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("exception safety: referralService failure returns empty, no re-throw")
    void apply_referralServiceException_returnsEmpty() {
        when(earningsRepository.countByUserIdAndPlatform("user6", "whatsapp")).thenReturn(5L);
        when(referralService.ensureReferralCode(any(), any()))
                .thenThrow(new RuntimeException("Referral DB down"));

        Optional<SessionState> result = action.apply("user6", "whatsapp", dummyRecord);

        assertThat(result).isEmpty();
        verify(messageSender, never()).sendMessage(any(), any(), any());
    }
}
