package com.gigsarathi.flow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionState {

    private String flowType;
    private int stepIndex;
    @Builder.Default
    private Map<String, Object> pendingData = new HashMap<>();
    private String startedAt;
}
