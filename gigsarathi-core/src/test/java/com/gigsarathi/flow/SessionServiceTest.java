package com.gigsarathi.flow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> redisOps;
    @Mock private ObjectMapper objectMapper;

    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(redisOps);
        sessionService = new SessionService(redisTemplate, objectMapper);
    }

    @Test
    @DisplayName("forward-compat: JSON without previousFlow deserializes to previousFlow=null")
    void forwardCompat_missingPreviousFlow_isNull() throws JsonProcessingException {
        String json = "{\"flowType\":\"DAILY_EARNINGS\",\"stepIndex\":1,\"pendingData\":{},\"startedAt\":\"2024-01-01T00:00:00Z\"}";
        ObjectMapper realMapper = new ObjectMapper();

        SessionState state = realMapper.readValue(json, SessionState.class);

        assertThat(state.getPreviousFlow()).isNull();
        assertThat(state.getFlowType()).isEqualTo("DAILY_EARNINGS");
        assertThat(state.getStepIndex()).isEqualTo(1);
    }

    @Test
    @DisplayName("rollback: JSON parse failure returns empty and deletes Redis key")
    void rollback_jsonParseFailure_deletesKeyAndReturnsEmpty() throws JsonProcessingException {
        // Simulates old code receiving new JSON (previousFlow field unknown to strict deserializer)
        when(redisOps.get("session:whatsapp:user1"))
                .thenReturn("{\"flowType\":\"DAILY_EARNINGS\",\"previousFlow\":\"DAILY_EARNINGS\"}");
        doThrow(new JsonProcessingException("Unrecognized field 'previousFlow'") {})
                .when(objectMapper).readValue(anyString(), eq(SessionState.class));

        Optional<SessionState> result = sessionService.getSession("whatsapp", "user1");

        assertThat(result).isEmpty();
        verify(redisTemplate).delete("session:whatsapp:user1");
    }

    @Test
    @DisplayName("roundtrip: previousFlow survives saveSession + getSession")
    void roundtrip_previousFlowSurvives() {
        ObjectMapper realMapper = new ObjectMapper();
        AtomicReference<String> stored = new AtomicReference<>();

        doAnswer(inv -> { stored.set(inv.getArgument(1)); return null; })
                .when(redisOps).set(anyString(), anyString(), any(Duration.class));
        when(redisOps.get(anyString())).thenAnswer(inv -> stored.get());

        SessionService svc = new SessionService(redisTemplate, realMapper);
        SessionState state = SessionState.builder()
                .flowType(FlowType.DAILY_EARNINGS.name())
                .stepIndex(4)
                .pendingData(new HashMap<>())
                .startedAt("2024-01-01T00:00:00Z")
                .previousFlow(FlowType.DAILY_EARNINGS.name())
                .build();

        svc.saveSession("whatsapp", "user1", state);
        Optional<SessionState> loaded = svc.getSession("whatsapp", "user1");

        assertThat(loaded).isPresent();
        assertThat(loaded.get().getPreviousFlow()).isEqualTo(FlowType.DAILY_EARNINGS.name());
        assertThat(loaded.get().getFlowType()).isEqualTo(FlowType.DAILY_EARNINGS.name());
    }
}
