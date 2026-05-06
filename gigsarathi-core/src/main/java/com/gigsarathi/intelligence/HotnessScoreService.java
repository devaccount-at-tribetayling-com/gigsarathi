package com.gigsarathi.intelligence;

import com.gigsarathi.domain.earnings.EarningsRecord;
import com.gigsarathi.domain.earnings.EarningsRepository;
import com.gigsarathi.domain.zone.ZoneHeuristic;
import com.gigsarathi.domain.zone.ZoneHeuristicRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class HotnessScoreService {

    private final ZoneHeuristicRepository zoneRepository;
    private final EarningsRepository earningsRepository;
    private final RedisTemplate<String, String> redisTemplate;

    public HotnessScoreService(ZoneHeuristicRepository zoneRepository,
                                EarningsRepository earningsRepository,
                                RedisTemplate<String, String> redisTemplate) {
        this.zoneRepository = zoneRepository;
        this.earningsRepository = earningsRepository;
        this.redisTemplate = redisTemplate;
    }

    public double score(ZoneHeuristic zone, LocalDate date) {
        String key = cacheKey(zone.getId(), zone.getTimeSlot(), date);
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return Double.parseDouble(cached);
        }
        double computed = compute(zone);
        redisTemplate.opsForValue().set(key, String.valueOf(computed), 1, TimeUnit.HOURS);
        return computed;
    }

    public List<ZoneHeuristic> topZones(String city, LocalDate date) {
        List<ZoneHeuristic> active = zoneRepository.findByCityIgnoreCaseAndActiveTrue(city);
        Map<String, Double> scores = new HashMap<>();
        for (ZoneHeuristic z : active) {
            scores.put(z.getId(), score(z, date));
        }
        return active.stream()
                .sorted(Comparator
                        .comparingDouble((ZoneHeuristic z) -> scores.get(z.getId())).reversed()
                        .thenComparingInt(ZoneHeuristic::getRecommendationPriority))
                .collect(Collectors.toList());
    }

    private double compute(ZoneHeuristic zone) {
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        double avgEarnings = earningsRepository
                .findByZoneAndSubmittedAtAfter(zone.getZone(), cutoff)
                .stream()
                .mapToDouble(EarningsRecord::getEarningsAmount)
                .average()
                .orElse(0.0);
        double demand = zone.getBaseDemandScore();
        int supply = zone.getEstimatedSupply() != null
                ? zone.getEstimatedSupply()
                : (int) earningsRepository.countByZoneAndSubmittedAtAfter(zone.getZone(), cutoff);
        return (avgEarnings * demand) / (supply + 1);
    }

    static String cacheKey(String zoneId, String timeSlot, LocalDate date) {
        return "hotness:" + zoneId + ":" + timeSlot + ":" + date;
    }
}
