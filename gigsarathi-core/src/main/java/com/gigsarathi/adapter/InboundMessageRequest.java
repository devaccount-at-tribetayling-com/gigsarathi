package com.gigsarathi.adapter;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboundMessageRequest {

    @NotBlank
    private String platform;

    @NotBlank
    private String userId;

    @NotBlank
    private String messageType;

    private Map<String, Object> payload;
}
