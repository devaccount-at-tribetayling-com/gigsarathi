package com.gigsarathi.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("events")
public class Event {

    @Id
    private String id;

    private String eventType;
    private String userId;
    private String platform;
    private Map<String, Object> metadata;
    private Instant createdAt;
}
