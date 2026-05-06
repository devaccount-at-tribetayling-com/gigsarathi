package com.gigsarathi.domain.idempotency;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface IdempotencyRepository extends MongoRepository<IdempotencyRecord, String> {

    boolean existsByPlatformAndMessageId(String platform, String messageId);
}
