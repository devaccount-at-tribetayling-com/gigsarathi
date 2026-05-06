package com.gigsarathi.flow;

import com.gigsarathi.bot.MessageSenderRouter;
import com.gigsarathi.domain.earnings.EarningsRecord;
import com.gigsarathi.domain.earnings.EarningsRepository;
import com.gigsarathi.domain.event.EventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TomorrowPlanActionTest {

    @Mock private EarningsRepository earningsRepository;
    @Mock private EventService eventService;
    @Mock private MessageSenderRouter messageSender;

    private TomorrowPlanAction action;
    private EarningsRecord dummyRecord;

    @BeforeEach
    void setUp() {
        action = new TomorrowPlanAction(earningsRepository, eventService, messageSender);
        dummyRecord = new EarningsRecord();
    }

    @Test
    @DisplayName("count < 3: sends message, emits event, returns TOMORROW_PLAN session")
    void apply_newUser_returnsTomorrowPlanSession() {
        when(earningsRepository.countByUserIdAndPlatform("user1", "whatsapp")).thenReturn(2L);

        Optional<SessionState> result = action.apply("user1", "whatsapp", dummyRecord);

        assertThat(result).isPresent();
        assertThat(result.get().getFlowType()).isEqualTo(FlowType.TOMORROW_PLAN.name());
        assertThat(result.get().getStepIndex()).isEqualTo(0);
        assertThat(result.get().getPreviousFlow()).isEqualTo(FlowType.DAILY_EARNINGS.name());
        verify(messageSender).sendMessage(eq("user1"), eq("whatsapp"), anyString());
        verify(eventService).emit(eq("tomorrow_plan_requested"), eq("user1"), eq("whatsapp"), any());
    }

    @Test
    @DisplayName("count == 0: first-time user qualifies")
    void apply_firstTimeUser_qualifies() {
        when(earningsRepository.countByUserIdAndPlatform("user2", "whatsapp")).thenReturn(0L);

        Optional<SessionState> result = action.apply("user2", "whatsapp", dummyRecord);

        assertThat(result).isPresent();
        assertThat(result.get().getFlowType()).isEqualTo(FlowType.TOMORROW_PLAN.name());
    }

    @Test
    @DisplayName("count == 3: defers to M3 ReferralPromptAction — returns empty")
    void apply_thresholdReached_returnsEmpty() {
        when(earningsRepository.countByUserIdAndPlatform("user3", "whatsapp")).thenReturn(3L);

        Optional<SessionState> result = action.apply("user3", "whatsapp", dummyRecord);

        assertThat(result).isEmpty();
        verify(messageSender, never()).sendMessage(any(), any(), any());
        verify(eventService, never()).emit(any(), any(), any(), any());
    }

    @Test
    @DisplayName("count > 3: experienced user defers to M3")
    void apply_experiencedUser_returnsEmpty() {
        when(earningsRepository.countByUserIdAndPlatform("user4", "whatsapp")).thenReturn(10L);

        Optional<SessionState> result = action.apply("user4", "whatsapp", dummyRecord);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("exception safety: repository failure returns empty, no re-throw")
    void apply_repositoryException_returnsEmpty() {
        when(earningsRepository.countByUserIdAndPlatform(any(), any()))
                .thenThrow(new RuntimeException("Redis down"));

        Optional<SessionState> result = action.apply("user5", "whatsapp", dummyRecord);

        assertThat(result).isEmpty();
    }
}
