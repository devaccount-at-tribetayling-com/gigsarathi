package com.gigsarathi.domain.config;

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
@Document("app_config")
public class AppConfig {

    @Id
    private String id;

    @Builder.Default
    private boolean loanEnabled = false;

    @Builder.Default
    private boolean referralRewardEnabled = false;
}
