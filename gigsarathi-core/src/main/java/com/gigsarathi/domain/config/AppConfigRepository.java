package com.gigsarathi.domain.config;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface AppConfigRepository extends MongoRepository<AppConfig, String> {
}
