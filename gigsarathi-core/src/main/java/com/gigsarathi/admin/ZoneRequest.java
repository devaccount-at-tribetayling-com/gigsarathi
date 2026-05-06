package com.gigsarathi.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoneRequest {

    @NotBlank
    private String city;

    @NotBlank
    private String zone;

    private String timeSlot;
    private double baseDemandScore;
    private Integer estimatedSupply;
    private int recommendationPriority;
    private boolean active;
}
