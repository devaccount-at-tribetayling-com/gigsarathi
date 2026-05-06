package com.gigsarathi.admin;

import com.gigsarathi.domain.zone.ZoneHeuristic;
import com.gigsarathi.domain.zone.ZoneHeuristicRepository;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/admin")
public class ZoneController {

    private final ZoneHeuristicRepository zoneRepository;
    private final RedisTemplate<String, String> redisTemplate;

    public ZoneController(ZoneHeuristicRepository zoneRepository,
                          RedisTemplate<String, String> redisTemplate) {
        this.zoneRepository = zoneRepository;
        this.redisTemplate = redisTemplate;
    }

    @PostMapping("/zones")
    public ZoneHeuristic upsert(@Valid @RequestBody ZoneRequest request) {
        Optional<ZoneHeuristic> existing = zoneRepository
                .findByCityIgnoreCaseAndZoneIgnoreCase(request.getCity(), request.getZone());

        ZoneHeuristic zone = existing.orElseGet(() -> ZoneHeuristic.builder()
                .city(request.getCity())
                .zone(request.getZone())
                .build());

        zone.setCity(request.getCity());
        zone.setZone(request.getZone());
        zone.setTimeSlot(request.getTimeSlot());
        zone.setBaseDemandScore(request.getBaseDemandScore());
        zone.setEstimatedSupply(request.getEstimatedSupply());
        zone.setRecommendationPriority(request.getRecommendationPriority());
        zone.setActive(request.isActive());
        ZoneHeuristic saved = zoneRepository.save(zone);
        try {
            Set<String> keys = redisTemplate.keys("hotness:" + saved.getId() + ":*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("Hotness cache invalidation failed for zone {}; stale scores may persist up to 1h",
                    saved.getId(), e);
        }
        return saved;
    }

    @GetMapping("/zones")
    public List<ZoneHeuristic> listByCity(@RequestParam String city) {
        return zoneRepository.findByCityIgnoreCase(city);
    }
}
