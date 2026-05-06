package com.gigsarathi.domain.referral;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("referral_codes")
@CompoundIndex(name = "user_platform_idx", def = "{'userId': 1, 'platform': 1}", unique = true)
public class ReferralCode {

    @Id
    private String id;

    private String userId;
    private String platform;

    @Indexed(unique = true)
    private String code;

    private Double rewardAmount;
    private Instant createdAt;
}
