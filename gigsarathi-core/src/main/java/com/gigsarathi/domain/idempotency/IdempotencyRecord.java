package com.gigsarathi.domain.idempotency;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("idempotency_records")
public class IdempotencyRecord {

    @Id
    private String id;

    private String platform;
    private String messageId;
    private Instant createdAt;
}
