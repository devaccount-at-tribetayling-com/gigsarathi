package com.gigsarathi.flow;

import com.gigsarathi.bot.MessageSenderRouter;
import com.gigsarathi.domain.earnings.EarningsRecord;
import com.gigsarathi.domain.earnings.EarningsRepository;
import com.gigsarathi.domain.event.EventService;
import com.gigsarathi.domain.user.UserRepository;
import com.gigsarathi.domain.zone.ZoneHeuristicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostDailySummaryActionTest {

    @Mock private SessionService sessionService;
    @Mock private EarningsRepository earningsRepository;
    @Mock private UserRepository userRepository;
    @Mock private ZoneHeuristicRepository zoneRepository;
    @Mock private EventService eventService;
    @Mock private MessageSenderRouter messageSender;

    @Mock private PostDailySummaryAction firstAction;
    @Mock private PostDailySummaryAction secondAction;

    private SessionState stateAtStep4;

    @BeforeEach
    void setUp() {
        stateAtStep4 = SessionState.builder()
                .flowType(FlowType.DAILY_EARNINGS.name())
                .stepIndex(4)
                .pendingData(new HashMap<>())
                .startedAt("2024-01-01T00:00:00Z")
                .build();
    }

    private DailyEarningsFlow flowWith(List<PostDailySummaryAction> actions) {
        return new DailyEarningsFlow(sessionService, earningsRepository, userRepository,
                zoneRepository, eventService, messageSender, actions);
    }

    @Test
    @DisplayName("empty actions list — session cleared, nothing saved")
    void emptyActionsList_sessionCleared() {
        flowWith(List.of()).handle("user1", "whatsapp", "TestZone", stateAtStep4);

        verify(sessionService).clearSession("whatsapp", "user1");
        verify(sessionService, never()).saveSession(any(), any(), any());
    }

    @Test
    @DisplayName("single action returning empty — session cleared")
    void singleActionReturningEmpty_sessionCleared() {
        when(firstAction.apply(any(), any(), any())).thenReturn(Optional.empty());

        flowWith(List.of(firstAction)).handle("user1", "whatsapp", "TestZone", stateAtStep4);

        verify(sessionService).clearSession("whatsapp", "user1");
        verify(sessionService, never()).saveSession(any(), any(), any());
    }

    @Test
    @DisplayName("single action returning state — session saved with that state")
    void singleActionReturningState_sessionSaved() {
        SessionState chained = SessionState.builder()
                .flowType(FlowType.TOMORROW_PLAN.name())
                .stepIndex(0)
                .pendingData(new HashMap<>())
                .startedAt("2024-01-01T00:00:00Z")
                .previousFlow(FlowType.DAILY_EARNINGS.name())
                .build();
        when(firstAction.apply(any(), any(), any())).thenReturn(Optional.of(chained));

        flowWith(List.of(firstAction)).handle("user1", "whatsapp", "TestZone", stateAtStep4);

        verify(sessionService).saveSession(eq("whatsapp"), eq("user1"), eq(chained));
        verify(sessionService, never()).clearSession(any(), any());
    }

    @Test
    @DisplayName("two actions both returning state — first wins, second never called")
    void twoActions_firstWins_secondNeverCalled() {
        SessionState chainedByFirst = SessionState.builder()
                .flowType(FlowType.TOMORROW_PLAN.name())
                .stepIndex(0)
                .pendingData(new HashMap<>())
                .startedAt("2024-01-01T00:00:00Z")
                .build();
        SessionState chainedBySecond = SessionState.builder()
                .flowType(FlowType.REFERRAL.name())
                .stepIndex(0)
                .pendingData(new HashMap<>())
                .startedAt("2024-01-01T00:00:00Z")
                .build();
        when(firstAction.apply(any(), any(), any())).thenReturn(Optional.of(chainedByFirst));

        flowWith(List.of(firstAction, secondAction))
                .handle("user1", "whatsapp", "TestZone", stateAtStep4);

        verify(sessionService).saveSession(eq("whatsapp"), eq("user1"), eq(chainedByFirst));
        verify(secondAction, never()).apply(anyString(), anyString(), any(EarningsRecord.class));
    }

    @Test
    @DisplayName("two actions, first empty then state — second wins")
    void twoActions_firstEmpty_secondWins() {
        SessionState chainedBySecond = SessionState.builder()
                .flowType(FlowType.REFERRAL.name())
                .stepIndex(0)
                .pendingData(new HashMap<>())
                .startedAt("2024-01-01T00:00:00Z")
                .previousFlow(FlowType.DAILY_EARNINGS.name())
                .build();
        when(firstAction.apply(any(), any(), any())).thenReturn(Optional.empty());
        when(secondAction.apply(any(), any(), any())).thenReturn(Optional.of(chainedBySecond));

        flowWith(List.of(firstAction, secondAction))
                .handle("user1", "whatsapp", "TestZone", stateAtStep4);

        verify(sessionService).saveSession(eq("whatsapp"), eq("user1"), eq(chainedBySecond));
        verify(sessionService, never()).clearSession(any(), any());
    }
}
