package com.gigsarathi.domain.earnings;

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
@Document("earnings_records")
public class EarningsRecord {

    @Id
    private String id;

    private String userId;
    private String platform;
    private String ordersRange;
    private int ordersCount;
    private String earningsRange;
    private double earningsAmount;
    private String fuelRange;
    private double fuelAmount;
    private String zone;
    private Instant submittedAt;
    private Instant updatedAt;
}
