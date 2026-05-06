package com.gigsarathi.flow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
public class SessionService {

    private static final Duration TTL = Duration.ofHours(24);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public SessionService(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<SessionState> getSession(String platform, String userId) {
        String key = key(platform, userId);
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, SessionState.class));
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse session JSON for {}: {}", key, ex.getMessage());
            redisTemplate.delete(key);
            return Optional.empty();
        }
    }

    public void saveSession(String platform, String userId, SessionState state) {
        String key = key(platform, userId);
        try {
            String json = objectMapper.writeValueAsString(state);
            redisTemplate.opsForValue().set(key, json, TTL);
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize session for {}: {}", key, ex.getMessage());
        }
    }

    public void clearSession(String platform, String userId) {
        redisTemplate.delete(key(platform, userId));
    }

    private String key(String platform, String userId) {
        return "session:" + platform + ":" + userId;
    }
}
