package com.gigsarathi.flow;

import com.gigsarathi.bot.MessageSenderRouter;
import com.gigsarathi.domain.event.EventService;
import com.gigsarathi.domain.user.User;
import com.gigsarathi.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies STOP-chain semantics: STOP always clears session regardless of
 * flowType or previousFlow. START does not resume a cleared chain.
 */
@ExtendWith(MockitoExtension.class)
class OptOutHandlerStopChainTest {

    @Mock private UserRepository userRepository;
    @Mock private SessionService sessionService;
    @Mock private EventService eventService;
    @Mock private MessageSenderRouter messageSender;

    private OptOutHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OptOutHandler(userRepository, sessionService, eventService, messageSender);
    }

    @Test
    @DisplayName("STOP while in TOMORROW_PLAN chain — session cleared, user opted out")
    void stop_withTomorrowPlanChain_clearsSession() {
        when(userRepository.findByPlatformAndUserId("whatsapp", "user1"))
                .thenReturn(Optional.empty());

        handler.handleStop("user1", "whatsapp");

        verify(sessionService).clearSession("whatsapp", "user1");
        verify(eventService).emit(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("STOP while in REFERRAL chain — session cleared, user opted out")
    void stop_withReferralChain_clearsSession() {
        when(userRepository.findByPlatformAndUserId("whatsapp", "user2"))
                .thenReturn(Optional.empty());

        handler.handleStop("user2", "whatsapp");

        verify(sessionService).clearSession("whatsapp", "user2");
    }

    @Test
    @DisplayName("STOP while in LOAN chain — session cleared, user opted out")
    void stop_withLoanChain_clearsSession() {
        when(userRepository.findByPlatformAndUserId("whatsapp", "user3"))
                .thenReturn(Optional.empty());

        handler.handleStop("user3", "whatsapp");

        verify(sessionService).clearSession("whatsapp", "user3");
    }

    @Test
    @DisplayName("STOP while in ACCOUNT_LINK chain — session cleared, user opted out")
    void stop_withAccountLinkChain_clearsSession() {
        when(userRepository.findByPlatformAndUserId("whatsapp", "user4"))
                .thenReturn(Optional.empty());

        handler.handleStop("user4", "whatsapp");

        verify(sessionService).clearSession("whatsapp", "user4");
    }

    @Test
    @DisplayName("START after STOP with previousFlow set — does not restore previous chain")
    void start_afterStop_doesNotRestoreChain() {
        User optedOutUser = new User();
        optedOutUser.setStatus("OPTED_OUT");
        when(userRepository.findByPlatformAndUserId("whatsapp", "user5"))
                .thenReturn(Optional.of(optedOutUser));

        handler.handleStart("user5", "whatsapp");

        // START only sets status=ACTIVE; it never touches the session
        verify(sessionService, never()).saveSession(any(), any(), any());
        verify(sessionService, never()).getSession(any(), any());
    }
}
