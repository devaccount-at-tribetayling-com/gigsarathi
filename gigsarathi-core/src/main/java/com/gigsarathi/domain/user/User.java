package com.gigsarathi.domain.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("users")
public class User {

    @Id
    private String id;

    private String userId;
    private String platform;
    private String phoneNumber;
    private String workType;
    private List<String> appsUsed;
    private String city;
    private String onboardingStatus;
    private String status;
    private Instant lastActiveAt;
    private Instant createdAt;
    private String linkedUserId;
}
