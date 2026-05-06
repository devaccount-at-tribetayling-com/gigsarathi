package com.gigsarathi.scheduler;

import com.gigsarathi.bot.MessageSenderRouter;
import com.gigsarathi.domain.earnings.EarningsRecord;
import com.gigsarathi.domain.earnings.EarningsRepository;
import com.gigsarathi.domain.event.EventService;
import com.gigsarathi.domain.user.User;
import com.gigsarathi.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InactiveNudgeSchedulerTest {

    @Mock private UserRepository userRepository;
    @Mock private EarningsRepository earningsRepository;
    @Mock private MessageSenderRouter messageSender;
    @Mock private EventService eventService;

    private InactiveNudgeScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new InactiveNudgeScheduler(userRepository, earningsRepository, messageSender, eventService);
    }

    @Test
    @DisplayName("user with recent earnings (< 2 days) is skipped")
    void sendInactiveNudge_activeUser_skipped() {
        User u = user("user1", "whatsapp");
        when(userRepository.findByStatusAndLastActiveAtAfter(eq("ACTIVE"), any(Instant.class)))
                .thenReturn(List.of(u));
        when(earningsRepository.countByUserIdAndPlatformAndSubmittedAtAfter(
                eq("user1"), eq("whatsapp"), any(Instant.class))).thenReturn(2L);

        scheduler.sendInactiveNudge();

        verify(messageSender, never()).sendMessage(any(), any(), any());
        verify(eventService, never()).emit(any(), any(), any(), any());
    }

    @Test
    @DisplayName("inactive user with prior earnings gets personalized nudge referencing last earnings")
    void sendInactiveNudge_inactiveWithHistory_personalizedMessage() {
        User u = user("user2", "whatsapp");
        when(userRepository.findByStatusAndLastActiveAtAfter(eq("ACTIVE"), any(Instant.class)))
                .thenReturn(List.of(u));
        when(earningsRepository.countByUserIdAndPlatformAndSubmittedAtAfter(
                eq("user2"), eq("whatsapp"), any(Instant.class))).thenReturn(0L);
        EarningsRecord last = new EarningsRecord();
        last.setEarningsRange("₹500-₹700");
        when(earningsRepository.findByUserIdAndPlatformOrderBySubmittedAtDesc("user2", "whatsapp"))
                .thenReturn(List.of(last));

        scheduler.sendInactiveNudge();

        verify(messageSender).sendMessage(eq("user2"), eq("whatsapp"),
                argThat(msg -> msg.contains("₹500-₹700")));
        verify(eventService).emit(eq("inactive_nudge_sent"), eq("user2"), eq("whatsapp"), any());
    }

    @Test
    @DisplayName("inactive user with no prior earnings gets generic nudge")
    void sendInactiveNudge_inactiveNoHistory_genericMessage() {
        User u = user("user3", "whatsapp");
        when(userRepository.findByStatusAndLastActiveAtAfter(eq("ACTIVE"), any(Instant.class)))
                .thenReturn(List.of(u));
        when(earningsRepository.countByUserIdAndPlatformAndSubmittedAtAfter(
                eq("user3"), eq("whatsapp"), any(Instant.class))).thenReturn(0L);
        when(earningsRepository.findByUserIdAndPlatformOrderBySubmittedAtDesc("user3", "whatsapp"))
                .thenReturn(List.of());

        scheduler.sendInactiveNudge();

        verify(messageSender).sendMessage(eq("user3"), eq("whatsapp"),
                argThat(msg -> msg.contains("Log today's shift")));
        verify(eventService).emit(eq("inactive_nudge_sent"), eq("user3"), eq("whatsapp"), any());
    }

    @Test
    @DisplayName("exception for one user does not stop processing remaining users")
    void sendInactiveNudge_oneUserFails_othersStillNudged() {
        User u1 = user("user4", "whatsapp");
        User u2 = user("user5", "whatsapp");
        when(userRepository.findByStatusAndLastActiveAtAfter(eq("ACTIVE"), any(Instant.class)))
                .thenReturn(List.of(u1, u2));
        when(earningsRepository.countByUserIdAndPlatformAndSubmittedAtAfter(
                any(), any(), any(Instant.class))).thenReturn(0L);
        when(earningsRepository.findByUserIdAndPlatformOrderBySubmittedAtDesc(any(), any()))
                .thenReturn(List.of());
        doThrow(new RuntimeException("send failure"))
                .when(messageSender).sendMessage(eq("user4"), any(), any());

        scheduler.sendInactiveNudge();

        verify(messageSender).sendMessage(eq("user5"), eq("whatsapp"), any());
        verify(eventService, never()).emit(any(), eq("user4"), any(), any());
        verify(eventService).emit(eq("inactive_nudge_sent"), eq("user5"), any(), any());
    }

    @Test
    @DisplayName("no candidates: nothing sent")
    void sendInactiveNudge_noCandidates_nothingSent() {
        when(userRepository.findByStatusAndLastActiveAtAfter(eq("ACTIVE"), any(Instant.class)))
                .thenReturn(List.of());

        scheduler.sendInactiveNudge();

        verify(messageSender, never()).sendMessage(any(), any(), any());
        verify(eventService, never()).emit(any(), any(), any(), any());
    }

    private User user(String userId, String platform) {
        User u = new User();
        u.setUserId(userId);
        u.setPlatform(platform);
        u.setStatus("ACTIVE");
        u.setLastActiveAt(Instant.now());
        return u;
    }
}
