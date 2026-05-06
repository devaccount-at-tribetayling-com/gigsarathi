package com.gigsarathi.admin;

import com.gigsarathi.domain.earnings.EarningsRecord;
import com.gigsarathi.domain.earnings.EarningsRepository;
import com.gigsarathi.domain.event.Event;
import com.gigsarathi.domain.event.EventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/admin")
public class AdminReportController {

    private final EventRepository eventRepository;
    private final EarningsRepository earningsRepository;

    public AdminReportController(EventRepository eventRepository,
                                 EarningsRepository earningsRepository) {
        this.eventRepository = eventRepository;
        this.earningsRepository = earningsRepository;
    }

    @GetMapping("/events/summary")
    public Map<String, Long> eventsSummary(
            @RequestParam Instant from,
            @RequestParam Instant to) {
        return eventRepository.findByCreatedAtBetween(from, to).stream()
                .collect(Collectors.groupingBy(Event::getEventType, Collectors.counting()));
    }

    @GetMapping("/earnings/export")
    public ResponseEntity<String> earningsExport(
            @RequestParam Instant from,
            @RequestParam Instant to) {
        List<EarningsRecord> records = earningsRepository.findBySubmittedAtBetween(from, to);
        String csv = buildCsv(records);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"earnings.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    private String buildCsv(List<EarningsRecord> records) {
        StringBuilder sb = new StringBuilder();
        sb.append("userId,platform,zone,ordersCount,earningsAmount,fuelAmount,submittedAt\n");
        for (EarningsRecord r : records) {
            sb.append(escapeCsv(r.getUserId())).append(',')
              .append(escapeCsv(r.getPlatform())).append(',')
              .append(escapeCsv(r.getZone())).append(',')
              .append(r.getOrdersCount()).append(',')
              .append(r.getEarningsAmount()).append(',')
              .append(r.getFuelAmount()).append(',')
              .append(r.getSubmittedAt()).append('\n');
        }
        return sb.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
