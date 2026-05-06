package com.gigsarathi.domain.event;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface EventRepository extends MongoRepository<Event, String> {

    List<Event> findByCreatedAtBetween(Instant from, Instant to);
}
