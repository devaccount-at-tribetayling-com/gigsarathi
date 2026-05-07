package com.gigsarathi.flow;

import com.gigsarathi.bot.MessageSenderRouter;
import com.gigsarathi.domain.event.EventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class LoanFlowTest {

    @Mock private SessionService sessionService;
    @Mock private MessageSenderRouter messageSender;
    @Mock private EventService eventService;

    private LoanFlow loanFlow;

    @BeforeEach
    void setUp() {
        loanFlow = new LoanFlow(sessionService, messageSender, eventService);
    }

    @Test
    @DisplayName("YES reply — emits loan_offer_clicked, sends confirmation, clears session")
    void handle_yesReply_emitsEventAndClearsSession() {
        loanFlow.handle("user1", "whatsapp", "YES", null);

        verify(eventService).emit(eq("loan_offer_clicked"), eq("user1"), eq("whatsapp"), any());
        verify(messageSender).sendMessage(eq("user1"), eq("whatsapp"), anyString());
        verify(sessionService).clearSession("whatsapp", "user1");
    }

    @Test
    @DisplayName("NO reply — sends decline message, clears session, no event emitted")
    void handle_noReply_clearsSessionNoEvent() {
        loanFlow.handle("user1", "whatsapp", "NO", null);

        verify(messageSender).sendMessage(eq("user1"), eq("whatsapp"), anyString());
        verify(sessionService).clearSession("whatsapp", "user1");
        verifyNoInteractions(eventService);
    }

    @Test
    @DisplayName("unknown reply — sends prompt, session kept, no event emitted")
    void handle_unknownReply_sendsPromptNoSessionClear() {
        loanFlow.handle("user1", "whatsapp", "MAYBE", null);

        verify(messageSender).sendMessage(eq("user1"), eq("whatsapp"), anyString());
        verifyNoInteractions(sessionService);
        verifyNoInteractions(eventService);
    }

    @Test
    @DisplayName("lowercase yes — normalised and handled as YES")
    void handle_lowercaseYes_treatedAsYes() {
        loanFlow.handle("user1", "whatsapp", "yes", null);

        verify(eventService).emit(eq("loan_offer_clicked"), eq("user1"), eq("whatsapp"), any());
        verify(sessionService).clearSession("whatsapp", "user1");
    }
}
