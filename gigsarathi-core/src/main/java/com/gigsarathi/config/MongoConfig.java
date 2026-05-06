package com.gigsarathi.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class MongoConfig {

    private final MongoTemplate mongoTemplate;

    public MongoConfig(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    public void initIndexes() {
        // idempotency_records: unique compound index + TTL on createdAt (7 days)
        IndexOperations idemOps = mongoTemplate.indexOps("idempotency_records");
        idemOps.ensureIndex(new Index()
                .on("platform", Sort.Direction.ASC)
                .on("messageId", Sort.Direction.ASC)
                .unique()
                .named("platform_messageId_unique"));
        idemOps.ensureIndex(new Index()
                .on("createdAt", Sort.Direction.ASC)
                .expire(7, TimeUnit.DAYS)
                .named("createdAt_ttl"));

        // earnings_records: index on userId + submittedAt desc
        IndexOperations earningsOps = mongoTemplate.indexOps("earnings_records");
        earningsOps.ensureIndex(new Index()
                .on("userId", Sort.Direction.ASC)
                .on("submittedAt", Sort.Direction.DESC)
                .named("userId_submittedAt"));

        // users: unique compound index
        IndexOperations userOps = mongoTemplate.indexOps("users");
        userOps.ensureIndex(new Index()
                .on("platform", Sort.Direction.ASC)
                .on("userId", Sort.Direction.ASC)
                .unique()
                .named("platform_userId_unique"));

        log.info("MongoDB indexes initialized");
    }
}
