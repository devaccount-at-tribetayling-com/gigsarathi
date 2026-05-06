package com.gigsarathi.domain.zone;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ZoneHeuristicRepository extends MongoRepository<ZoneHeuristic, String> {

    List<ZoneHeuristic> findByCityIgnoreCase(String city);

    List<ZoneHeuristic> findByCityIgnoreCaseAndActiveTrue(String city);

    Optional<ZoneHeuristic> findByCityIgnoreCaseAndZoneIgnoreCase(String city, String zone);
}
