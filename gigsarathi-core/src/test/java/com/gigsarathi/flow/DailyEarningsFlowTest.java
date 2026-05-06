package com.gigsarathi.flow;

import com.gigsarathi.bot.MessageSenderRouter;
import com.gigsarathi.domain.earnings.EarningsRepository;
import com.gigsarathi.domain.event.EventService;
import com.gigsarathi.domain.user.UserRepository;
import com.gigsarathi.domain.zone.ZoneHeuristicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * M1 gate test for DailyEarningsFlow.
 * Verifies that the sendMessage/emit ordering fix doesn't regress M1 behavior
 * and that the empty-actions path clears session correctly.
 */
@ExtendWith(MockitoExtension.class)
class DailyEarningsFlowTest {

    @Mock private SessionService sessionService;
    @Mock private EarningsRepository earningsRepository;
    @Mock private UserRepository userRepository;
    @Mock private ZoneHeuristicRepository zoneRepository;
    @Mock private EventService eventService;
    @Mock private MessageSenderRouter messageSender;

    private DailyEarningsFlow flow;

    @BeforeEach
    void setUp() {
        flow = new DailyEarningsFlow(sessionService, earningsRepository, userRepository,
                zoneRepository, eventService, messageSender, List.of());
    }

    @Test
    @DisplayName("M1 path: sendMessage fires before daily_summary_viewed event and clearSession")
    void m1Path_sendMessageBeforeEventBeforeClear() {
        SessionState state = SessionState.builder()
                .flowType(FlowType.DAILY_EARNINGS.name())
                .stepIndex(4)
                .pendingData(new HashMap<>())
                .startedAt("2024-01-01T00:00:00Z")
                .build();

        flow.handle("user1", "whatsapp", "TestZone", state);

        InOrder order = inOrder(messageSender, eventService, sessionService);
        order.verify(messageSender).sendMessage(eq("user1"), eq("whatsapp"), anyString());
        order.verify(eventService).emit(eq("daily_summary_viewed"), eq("user1"), eq("whatsapp"), any());
        order.verify(sessionService).clearSession("whatsapp", "user1");
    }

    @Test
    @DisplayName("M1 path: daily_summary_viewed event emitted exactly once")
    void m1Path_eventEmittedOnce() {
        SessionState state = SessionState.builder()
                .flowType(FlowType.DAILY_EARNINGS.name())
                .stepIndex(4)
                .pendingData(new HashMap<>())
                .startedAt("2024-01-01T00:00:00Z")
                .build();

        flow.handle("user1", "whatsapp", "TestZone", state);

        verify(eventService).emit(eq("daily_summary_viewed"), eq("user1"), eq("whatsapp"), any());
        verify(sessionService).clearSession("whatsapp", "user1");
        verify(sessionService, never()).saveSession(any(), any(), any());
    }
}
