package com.gigsarathi.scheduler;

import com.gigsarathi.bot.MessageSenderRouter;
import com.gigsarathi.domain.event.EventService;
import com.gigsarathi.domain.user.User;
import com.gigsarathi.domain.user.UserRepository;
import com.gigsarathi.domain.zone.ZoneHeuristic;
import com.gigsarathi.intelligence.HotnessScoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PeakNudgeSchedulerTest {

    @Mock private UserRepository userRepository;
    @Mock private HotnessScoreService hotnessScoreService;
    @Mock private MessageSenderRouter messageSender;
    @Mock private EventService eventService;

    private PeakNudgeScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PeakNudgeScheduler(userRepository, hotnessScoreService, messageSender, eventService);
    }

    @Test
    @DisplayName("eligible users each receive nudge and peak_nudge_sent event")
    void sendPeakNudge_eligibleUsers_nudgedWithEvent() {
        User u1 = user("user1", "whatsapp", null);
        User u2 = user("user2", "whatsapp", null);
        when(userRepository.findByStatusAndLastActiveAtAfter(eq("ACTIVE"), any(Instant.class)))
                .thenReturn(List.of(u1, u2));

        scheduler.sendPeakNudge();

        verify(messageSender, times(2)).sendMessage(any(), eq("whatsapp"), any());
        verify(eventService, times(2)).emit(eq("peak_nudge_sent"), any(), any(), any());
    }

    @Test
    @DisplayName("user with city: message includes top zone names")
    void sendPeakNudge_userWithCity_personalizedMessage() {
        User u = user("user1", "whatsapp", "Mumbai");
        when(userRepository.findByStatusAndLastActiveAtAfter(eq("ACTIVE"), any(Instant.class)))
                .thenReturn(List.of(u));
        ZoneHeuristic z1 = zone("Andheri");
        ZoneHeuristic z2 = zone("Bandra");
        when(hotnessScoreService.topZones(eq("Mumbai"), any(LocalDate.class)))
                .thenReturn(List.of(z1, z2));

        scheduler.sendPeakNudge();

        verify(messageSender).sendMessage(
                eq("user1"), eq("whatsapp"),
                argThat(msg -> msg.contains("Andheri") && msg.contains("Bandra")));
    }

    @Test
    @DisplayName("user with null city: falls back to generic message")
    void sendPeakNudge_nullCity_genericMessage() {
        User u = user("user1", "whatsapp", null);
        when(userRepository.findByStatusAndLastActiveAtAfter(eq("ACTIVE"), any(Instant.class)))
                .thenReturn(List.of(u));

        scheduler.sendPeakNudge();

        verify(messageSender).sendMessage(
                eq("user1"), eq("whatsapp"),
                argThat(msg -> msg.contains("Peak hours")));
        verify(hotnessScoreService, never()).topZones(any(), any());
    }

    @Test
    @DisplayName("exception for one user does not stop processing remaining users")
    void sendPeakNudge_oneUserFails_othersStillNudged() {
        User u1 = user("user1", "whatsapp", null);
        User u2 = user("user2", "whatsapp", null);
        when(userRepository.findByStatusAndLastActiveAtAfter(eq("ACTIVE"), any(Instant.class)))
                .thenReturn(List.of(u1, u2));
        doThrow(new RuntimeException("send failure"))
                .when(messageSender).sendMessage(eq("user1"), any(), any());

        scheduler.sendPeakNudge();

        verify(messageSender).sendMessage(eq("user2"), eq("whatsapp"), any());
        verify(eventService, never()).emit(any(), eq("user1"), any(), any());
        verify(eventService).emit(eq("peak_nudge_sent"), eq("user2"), any(), any());
    }

    @Test
    @DisplayName("no eligible users: nothing sent")
    void sendPeakNudge_noEligibleUsers_nothingSent() {
        when(userRepository.findByStatusAndLastActiveAtAfter(eq("ACTIVE"), any(Instant.class)))
                .thenReturn(List.of());

        scheduler.sendPeakNudge();

        verify(messageSender, never()).sendMessage(any(), any(), any());
        verify(eventService, never()).emit(any(), any(), any(), any());
    }

    private User user(String userId, String platform, String city) {
        User u = new User();
        u.setUserId(userId);
        u.setPlatform(platform);
        u.setCity(city);
        u.setStatus("ACTIVE");
        u.setLastActiveAt(Instant.now());
        return u;
    }

    private ZoneHeuristic zone(String zoneName) {
        ZoneHeuristic z = new ZoneHeuristic();
        z.setZone(zoneName);
        return z;
    }
}
