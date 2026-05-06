package com.gigsarathi.domain.user;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByPlatformAndUserId(String platform, String userId);

    List<User> findByStatusAndLastActiveAtAfter(String status, Instant after);
}
