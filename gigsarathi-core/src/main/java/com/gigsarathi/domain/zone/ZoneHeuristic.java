package com.gigsarathi.domain.zone;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("zone_heuristics")
public class ZoneHeuristic {

    @Id
    private String id;

    private String city;
    private String zone;
    private String timeSlot;
    private double baseDemandScore;
    private Integer estimatedSupply;
    private int recommendationPriority;
    private boolean active;
}
