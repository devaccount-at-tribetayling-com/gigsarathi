package com.gigsarathi.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gigsarathi.domain.zone.ZoneHeuristic;
import com.gigsarathi.domain.zone.ZoneHeuristicRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Slf4j
@Component
public class ZoneDataSeeder implements ApplicationListener<ApplicationReadyEvent> {

    private final ZoneHeuristicRepository zoneRepository;
    private final ObjectMapper objectMapper;

    public ZoneDataSeeder(ZoneHeuristicRepository zoneRepository, ObjectMapper objectMapper) {
        this.zoneRepository = zoneRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            ClassPathResource resource = new ClassPathResource("fixtures/kolhapur-zones.json");
            if (!resource.exists()) {
                log.warn("kolhapur-zones.json fixture not found on classpath");
                return;
            }
            try (InputStream is = resource.getInputStream()) {
                List<ZoneHeuristic> zones = objectMapper.readValue(is,
                        new TypeReference<List<ZoneHeuristic>>() {});
                int inserted = 0;
                for (ZoneHeuristic z : zones) {
                    boolean exists = zoneRepository
                            .findByCityIgnoreCaseAndZoneIgnoreCase(z.getCity(), z.getZone())
                            .isPresent();
                    if (!exists) {
                        zoneRepository.save(z);
                        inserted++;
                    }
                }
                log.info("ZoneDataSeeder: inserted {} new zones (skipped {})",
                        inserted, zones.size() - inserted);
            }
        } catch (Exception ex) {
            log.error("Zone seeding failed: {}", ex.getMessage(), ex);
        }
    }
}
