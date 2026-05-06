package com.gigsarathi.admin;

import com.gigsarathi.domain.zone.ZoneHeuristic;
import com.gigsarathi.domain.zone.ZoneHeuristicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import org.mockito.ArgumentMatchers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZoneControllerCacheTest {

    @Mock private ZoneHeuristicRepository zoneRepository;
    @Mock private RedisTemplate<String, String> redisTemplate;

    private ZoneController controller;

    @BeforeEach
    void setUp() {
        controller = new ZoneController(zoneRepository, redisTemplate);
    }

    @Test
    @DisplayName("upsert invalidates hotness cache keys for the saved zone")
    void upsert_invalidatesCacheForSavedZone() {
        ZoneHeuristic saved = savedZone("zone42");
        when(zoneRepository.findByCityIgnoreCaseAndZoneIgnoreCase("Mumbai", "Andheri"))
                .thenReturn(Optional.of(saved));
        when(zoneRepository.save(any())).thenReturn(saved);
        when(redisTemplate.keys("hotness:zone42:*")).thenReturn(Set.of("hotness:zone42:morning:2024-06-15"));

        ZoneHeuristic result = controller.upsert(zoneRequest());

        assertThat(result.getId()).isEqualTo("zone42");
        verify(redisTemplate).keys("hotness:zone42:*");
        verify(redisTemplate).delete(Set.of("hotness:zone42:morning:2024-06-15"));
    }

    @Test
    @DisplayName("no cache keys found: delete not called")
    void upsert_noCacheKeys_deleteNotCalled() {
        ZoneHeuristic saved = savedZone("zone99");
        when(zoneRepository.findByCityIgnoreCaseAndZoneIgnoreCase(any(), any()))
                .thenReturn(Optional.of(saved));
        when(zoneRepository.save(any())).thenReturn(saved);
        when(redisTemplate.keys("hotness:zone99:*")).thenReturn(Set.of());

        controller.upsert(zoneRequest());

        verify(redisTemplate).keys("hotness:zone99:*");
        verify(redisTemplate, never()).delete(ArgumentMatchers.<Collection<String>>any());
    }

    @Test
    @DisplayName("null keys response: delete not called")
    void upsert_nullKeys_deleteNotCalled() {
        ZoneHeuristic saved = savedZone("zone77");
        when(zoneRepository.findByCityIgnoreCaseAndZoneIgnoreCase(any(), any()))
                .thenReturn(Optional.of(saved));
        when(zoneRepository.save(any())).thenReturn(saved);
        when(redisTemplate.keys("hotness:zone77:*")).thenReturn(null);

        ZoneHeuristic result = controller.upsert(zoneRequest());

        assertThat(result).isNotNull();
        verify(redisTemplate, never()).delete(ArgumentMatchers.<Collection<String>>any());
    }

    @Test
    @DisplayName("cache invalidation failure: zone still returned (resilient)")
    void upsert_cacheException_zoneStillReturned() {
        ZoneHeuristic saved = savedZone("zoneErr");
        when(zoneRepository.findByCityIgnoreCaseAndZoneIgnoreCase(any(), any()))
                .thenReturn(Optional.of(saved));
        when(zoneRepository.save(any())).thenReturn(saved);
        doThrow(new RuntimeException("Redis unavailable"))
                .when(redisTemplate).keys(eq("hotness:zoneErr:*"));

        ZoneHeuristic result = controller.upsert(zoneRequest());

        assertThat(result.getId()).isEqualTo("zoneErr");
    }

    private ZoneHeuristic savedZone(String id) {
        ZoneHeuristic z = new ZoneHeuristic();
        z.setId(id);
        z.setCity("Mumbai");
        z.setZone("Andheri");
        z.setTimeSlot("morning");
        z.setBaseDemandScore(1.5);
        z.setActive(true);
        return z;
    }

    private ZoneRequest zoneRequest() {
        ZoneRequest r = new ZoneRequest();
        r.setCity("Mumbai");
        r.setZone("Andheri");
        r.setTimeSlot("morning");
        r.setBaseDemandScore(1.5);
        r.setActive(true);
        return r;
    }
}
