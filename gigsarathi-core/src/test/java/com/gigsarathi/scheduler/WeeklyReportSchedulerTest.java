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
class WeeklyReportSchedulerTest {

    @Mock private UserRepository userRepository;
    @Mock private EarningsRepository earningsRepository;
    @Mock private MessageSenderRouter messageSender;
    @Mock private EventService eventService;

    private WeeklyReportScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new WeeklyReportScheduler(userRepository, earningsRepository, messageSender, eventService);
    }

    @Test
    @DisplayName("user with >= 2 records receives full 5-point weekly summary")
    void sendWeeklyReport_sufficientRecords_fullReport() {
        User u = user("user1", "whatsapp");
        when(userRepository.findByStatusAndLastActiveAtAfter(eq("ACTIVE"), any(Instant.class)))
                .thenReturn(List.of(u));
        when(earningsRepository.findByUserIdAndPlatformAndSubmittedAtAfter(
                eq("user1"), eq("whatsapp"), any(Instant.class)))
                .thenReturn(List.of(record(500, 50, 20, "Andheri"),
                                    record(600, 60, 25, "Bandra")));

        scheduler.sendWeeklyReport();

        verify(messageSender).sendMessage(eq("user1"), eq("whatsapp"),
                argThat(msg -> msg.contains("Weekly Summary")
                        && msg.contains("2")
                        && msg.contains("1100")
                        && msg.contains("Bandra")));
        verify(eventService).emit(eq("weekly_report_sent"), eq("user1"), eq("whatsapp"), any());
    }

    @Test
    @DisplayName("user with < 2 records receives reminder message")
    void sendWeeklyReport_insufficientRecords_reminderMessage() {
        User u = user("user2", "whatsapp");
        when(userRepository.findByStatusAndLastActiveAtAfter(eq("ACTIVE"), any(Instant.class)))
                .thenReturn(List.of(u));
        when(earningsRepository.findByUserIdAndPlatformAndSubmittedAtAfter(
                eq("user2"), eq("whatsapp"), any(Instant.class)))
                .thenReturn(List.of(record(400, 40, 15, "Dadar")));

        scheduler.sendWeeklyReport();

        verify(messageSender).sendMessage(eq("user2"), eq("whatsapp"),
                argThat(msg -> msg.contains("at least 2 shifts")));
        verify(eventService).emit(eq("weekly_report_sent"), eq("user2"), eq("whatsapp"), any());
    }

    @Test
    @DisplayName("user with 0 records receives reminder message")
    void sendWeeklyReport_zeroRecords_reminderMessage() {
        User u = user("user3", "whatsapp");
        when(userRepository.findByStatusAndLastActiveAtAfter(eq("ACTIVE"), any(Instant.class)))
                .thenReturn(List.of(u));
        when(earningsRepository.findByUserIdAndPlatformAndSubmittedAtAfter(
                eq("user3"), eq("whatsapp"), any(Instant.class)))
                .thenReturn(List.of());

        scheduler.sendWeeklyReport();

        verify(messageSender).sendMessage(eq("user3"), eq("whatsapp"),
                argThat(msg -> msg.contains("at least 2 shifts")));
    }

    @Test
    @DisplayName("best zone is the one with highest total earnings across shifts")
    void sendWeeklyReport_bestZoneCalculation_highestEarningsZone() {
        User u = user("user4", "whatsapp");
        when(userRepository.findByStatusAndLastActiveAtAfter(eq("ACTIVE"), any(Instant.class)))
                .thenReturn(List.of(u));
        when(earningsRepository.findByUserIdAndPlatformAndSubmittedAtAfter(
                eq("user4"), eq("whatsapp"), any(Instant.class)))
                .thenReturn(List.of(
                        record(100, 10, 5, "Andheri"),
                        record(800, 80, 30, "Kurla"),
                        record(200, 20, 10, "Andheri")));

        scheduler.sendWeeklyReport();

        verify(messageSender).sendMessage(eq("user4"), eq("whatsapp"),
                argThat(msg -> msg.contains("Kurla")));
    }

    @Test
    @DisplayName("exception for one user does not stop processing remaining users")
    void sendWeeklyReport_oneUserFails_othersStillReported() {
        User u1 = user("user5", "whatsapp");
        User u2 = user("user6", "whatsapp");
        when(userRepository.findByStatusAndLastActiveAtAfter(eq("ACTIVE"), any(Instant.class)))
                .thenReturn(List.of(u1, u2));
        when(earningsRepository.findByUserIdAndPlatformAndSubmittedAtAfter(
                any(), any(), any(Instant.class))).thenReturn(List.of());
        doThrow(new RuntimeException("send failure"))
                .when(messageSender).sendMessage(eq("user5"), any(), any());

        scheduler.sendWeeklyReport();

        verify(messageSender).sendMessage(eq("user6"), eq("whatsapp"), any());
        verify(eventService, never()).emit(any(), eq("user5"), any(), any());
        verify(eventService).emit(eq("weekly_report_sent"), eq("user6"), any(), any());
    }

    @Test
    @DisplayName("no eligible users: nothing sent")
    void sendWeeklyReport_noEligibleUsers_nothingSent() {
        when(userRepository.findByStatusAndLastActiveAtAfter(eq("ACTIVE"), any(Instant.class)))
                .thenReturn(List.of());

        scheduler.sendWeeklyReport();

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

    private EarningsRecord record(double earnings, double fuel, int orders, String zone) {
        EarningsRecord r = new EarningsRecord();
        r.setEarningsAmount(earnings);
        r.setFuelAmount(fuel);
        r.setOrdersCount(orders);
        r.setZone(zone);
        r.setSubmittedAt(Instant.now());
        return r;
    }
}
