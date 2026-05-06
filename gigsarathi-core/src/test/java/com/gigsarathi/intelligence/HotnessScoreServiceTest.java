package com.gigsarathi.intelligence;

import com.gigsarathi.domain.earnings.EarningsRecord;
import com.gigsarathi.domain.earnings.EarningsRepository;
import com.gigsarathi.domain.zone.ZoneHeuristic;
import com.gigsarathi.domain.zone.ZoneHeuristicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotnessScoreServiceTest {

    @Mock private ZoneHeuristicRepository zoneRepository;
    @Mock private EarningsRepository earningsRepository;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private HotnessScoreService service;

    private static final LocalDate TODAY = LocalDate.of(2024, 6, 15);

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new HotnessScoreService(zoneRepository, earningsRepository, redisTemplate);
    }

    @Test
    @DisplayName("cache miss: score computed and stored in Redis")
    void score_cacheMiss_computesAndCaches() {
        ZoneHeuristic zone = zone("z1", "Andheri", "morning", 2.0, 4);
        EarningsRecord rec = earningsRecord(500.0);
        when(valueOps.get(anyString())).thenReturn(null);
        when(earningsRepository.findByZoneAndSubmittedAtAfter(eq("Andheri"), any(Instant.class)))
                .thenReturn(List.of(rec));

        double result = service.score(zone, TODAY);

        // (500 * 2.0) / (4 + 1) = 200.0
        assertThat(result).isCloseTo(200.0, within(0.001));
        verify(valueOps).set(
                eq(HotnessScoreService.cacheKey("z1", "morning", TODAY)),
                eq("200.0"),
                eq(1L),
                eq(TimeUnit.HOURS));
    }

    @Test
    @DisplayName("cache hit: returns cached value without DB calls")
    void score_cacheHit_returnsCachedNoDB() {
        ZoneHeuristic zone = zone("z1", "Andheri", "morning", 2.0, 4);
        when(valueOps.get(HotnessScoreService.cacheKey("z1", "morning", TODAY))).thenReturn("99.5");

        double result = service.score(zone, TODAY);

        assertThat(result).isEqualTo(99.5);
        verify(earningsRepository, never()).findByZoneAndSubmittedAtAfter(anyString(), any());
    }

    @Test
    @DisplayName("supply fallback: estimatedSupply null uses count from DB")
    void score_supplyFallback_usesCountWhenEstimatedSupplyNull() {
        ZoneHeuristic zone = zone("z2", "Bandra", "evening", 3.0, null);
        EarningsRecord rec = earningsRecord(300.0);
        when(valueOps.get(anyString())).thenReturn(null);
        when(earningsRepository.findByZoneAndSubmittedAtAfter(eq("Bandra"), any(Instant.class)))
                .thenReturn(List.of(rec));
        when(earningsRepository.countByZoneAndSubmittedAtAfter(eq("Bandra"), any(Instant.class)))
                .thenReturn(5L);

        double result = service.score(zone, TODAY);

        // (300 * 3.0) / (5 + 1) = 150.0
        assertThat(result).isCloseTo(150.0, within(0.001));
    }

    @Test
    @DisplayName("zero earnings: score is 0.0")
    void score_noEarnings_returnsZero() {
        ZoneHeuristic zone = zone("z3", "Dadar", "afternoon", 1.5, 2);
        when(valueOps.get(anyString())).thenReturn(null);
        when(earningsRepository.findByZoneAndSubmittedAtAfter(anyString(), any(Instant.class)))
                .thenReturn(List.of());

        double result = service.score(zone, TODAY);

        assertThat(result).isEqualTo(0.0);
    }

    @Test
    @DisplayName("topZones: sorted score DESC, tiebreak by recommendationPriority ASC")
    void topZones_sortedByScoreDescThenPriorityAsc() {
        ZoneHeuristic z1 = zoneWithPriority("z1", "Mumbai", "morning", 1.0, 0, 2);  // score=100
        ZoneHeuristic z2 = zoneWithPriority("z2", "Mumbai", "morning", 2.0, 0, 1);  // score=200
        ZoneHeuristic z3 = zoneWithPriority("z3", "Mumbai", "morning", 2.0, 0, 3);  // score=200, priority=3
        when(zoneRepository.findByCityIgnoreCaseAndActiveTrue("Mumbai")).thenReturn(List.of(z1, z2, z3));
        when(valueOps.get(anyString())).thenReturn(null);
        EarningsRecord rec = earningsRecord(100.0);
        when(earningsRepository.findByZoneAndSubmittedAtAfter(anyString(), any(Instant.class)))
                .thenReturn(List.of(rec));

        List<ZoneHeuristic> result = service.topZones("Mumbai", TODAY);

        // z2 and z3 both have score=200 (100*2/(0+1)); z2 priority=1 wins tiebreak; z1 score=100
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getId()).isEqualTo("z2"); // score=200, priority=1
        assertThat(result.get(1).getId()).isEqualTo("z3"); // score=200, priority=3
        assertThat(result.get(2).getId()).isEqualTo("z1"); // score=100
    }

    @Test
    @DisplayName("cacheKey: format is hotness:{zoneId}:{timeSlot}:{date}")
    void cacheKey_format() {
        String key = HotnessScoreService.cacheKey("abc123", "morning", LocalDate.of(2024, 6, 15));
        assertThat(key).isEqualTo("hotness:abc123:morning:2024-06-15");
    }

    private ZoneHeuristic zone(String id, String zoneName, String timeSlot, double demand, Integer supply) {
        ZoneHeuristic z = new ZoneHeuristic();
        z.setId(id);
        z.setZone(zoneName);
        z.setTimeSlot(timeSlot);
        z.setBaseDemandScore(demand);
        z.setEstimatedSupply(supply);
        z.setRecommendationPriority(1);
        return z;
    }

    private ZoneHeuristic zoneWithPriority(String id, String city, String timeSlot, double demand, Integer supply, int priority) {
        ZoneHeuristic z = zone(id, city, timeSlot, demand, supply);
        z.setCity(city);
        z.setRecommendationPriority(priority);
        return z;
    }

    private EarningsRecord earningsRecord(double amount) {
        EarningsRecord r = new EarningsRecord();
        r.setEarningsAmount(amount);
        return r;
    }
}
