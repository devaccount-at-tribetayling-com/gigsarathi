package com.gigsarathi.domain.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
public class EventService {

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public void emit(String eventType, String userId, String platform, Map<String, Object> metadata) {
        Event event = Event.builder()
                .eventType(eventType)
                .userId(userId)
                .platform(platform)
                .metadata(metadata)
                .createdAt(Instant.now())
                .build();
        eventRepository.save(event);
        log.debug("Emitted event {} for user {}/{}", eventType, platform, userId);
    }
}
