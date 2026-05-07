package com.gigsarathi.admin;

import com.gigsarathi.domain.earnings.EarningsRecord;
import com.gigsarathi.domain.earnings.EarningsRepository;
import com.gigsarathi.domain.event.Event;
import com.gigsarathi.domain.event.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminReportControllerTest {

    @Mock private EventRepository eventRepository;
    @Mock private EarningsRepository earningsRepository;

    private AdminReportController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminReportController(eventRepository, earningsRepository);
    }

    @Test
    @DisplayName("eventsSummary groups events by type with correct counts")
    void eventsSummary_multipleEvents_groupedByType() {
        Instant from = Instant.now().minusSeconds(3600);
        Instant to = Instant.now();
        when(eventRepository.findByCreatedAtBetween(from, to)).thenReturn(List.of(
                event("peak_nudge_sent"),
                event("peak_nudge_sent"),
                event("inactive_nudge_sent"),
                event("weekly_report_sent")
        ));

        Map<String, Long> result = controller.eventsSummary(from, to);

        assertThat(result).containsEntry("peak_nudge_sent", 2L);
        assertThat(result).containsEntry("inactive_nudge_sent", 1L);
        assertThat(result).containsEntry("weekly_report_sent", 1L);
        assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("eventsSummary returns empty map when no events in range")
    void eventsSummary_noEvents_emptyMap() {
        when(eventRepository.findByCreatedAtBetween(any(), any())).thenReturn(List.of());

        Map<String, Long> result = controller.eventsSummary(Instant.now(), Instant.now());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("earningsExport returns CSV with header row and one data row per record")
    void earningsExport_withRecords_csvWithCorrectRows() {
        Instant from = Instant.now().minusSeconds(3600);
        Instant to = Instant.now();
        EarningsRecord r1 = earningsRecord("user1", "whatsapp", "Andheri", 20, 500.0, 50.0);
        EarningsRecord r2 = earningsRecord("user2", "telegram", "Bandra", 15, 400.0, 40.0);
        when(earningsRepository.findBySubmittedAtBetween(from, to)).thenReturn(List.of(r1, r2));

        ResponseEntity<String> response = controller.earningsExport(from, to);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        String body = response.getBody();
        assertThat(body).startsWith("userId,platform,zone,ordersCount,earningsAmount,fuelAmount,submittedAt");
        assertThat(body).contains("user1");
        assertThat(body).contains("Andheri");
        assertThat(body).contains("500.0");
        assertThat(body).contains("user2");
        assertThat(body).contains("Bandra");
        String[] lines = body.split("\n");
        assertThat(lines).hasSize(3); // header + 2 data rows
    }

    @Test
    @DisplayName("earningsExport returns just header when no records in range")
    void earningsExport_noRecords_justHeader() {
        when(earningsRepository.findBySubmittedAtBetween(any(), any())).thenReturn(List.of());

        ResponseEntity<String> response = controller.earningsExport(Instant.now(), Instant.now());

        assertThat(response.getBody()).isEqualTo(
                "userId,platform,zone,ordersCount,earningsAmount,fuelAmount,submittedAt\n");
    }

    @Test
    @DisplayName("earningsExport content-disposition header is set for file download")
    void earningsExport_responseHeaders_contentDispositionSet() {
        when(earningsRepository.findBySubmittedAtBetween(any(), any())).thenReturn(List.of());

        ResponseEntity<String> response = controller.earningsExport(Instant.now(), Instant.now());

        assertThat(response.getHeaders().getFirst("Content-Disposition"))
                .contains("attachment")
                .contains("earnings.csv");
    }

    @Test
    @DisplayName("earningsExport escapes CSV values containing commas")
    void earningsExport_valueWithComma_escaped() {
        EarningsRecord r = earningsRecord("user,1", "whatsapp", "Zone,A", 5, 100.0, 10.0);
        when(earningsRepository.findBySubmittedAtBetween(any(), any())).thenReturn(List.of(r));

        ResponseEntity<String> response = controller.earningsExport(Instant.now(), Instant.now());

        assertThat(response.getBody()).contains("\"user,1\"");
        assertThat(response.getBody()).contains("\"Zone,A\"");
    }

    private Event event(String type) {
        Event e = new Event();
        e.setEventType(type);
        e.setCreatedAt(Instant.now());
        return e;
    }

    private EarningsRecord earningsRecord(String userId, String platform, String zone,
                                          int orders, double earnings, double fuel) {
        EarningsRecord r = new EarningsRecord();
        r.setUserId(userId);
        r.setPlatform(platform);
        r.setZone(zone);
        r.setOrdersCount(orders);
        r.setEarningsAmount(earnings);
        r.setFuelAmount(fuel);
        r.setSubmittedAt(Instant.now());
        return r;
    }
}
