package com.gigsarathi.flow;

import com.gigsarathi.domain.earnings.EarningsRecord;
import com.gigsarathi.domain.earnings.EarningsRepository;
import com.gigsarathi.domain.user.User;
import com.gigsarathi.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountLinkServiceTest {

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private UserRepository userRepository;
    @Mock private EarningsRepository earningsRepository;
    @Mock private ValueOperations<String, String> valueOps;

    private AccountLinkService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new AccountLinkService(redisTemplate, userRepository, earningsRepository);
    }

    @Test
    @DisplayName("generateCode — returns 6-digit string and stores it in Redis with 10-min TTL")
    void generateCode_storesTokenInRedis() {
        String code = service.generateCode("user1", "whatsapp");

        assertThat(code).matches("\\d{6}");
        verify(valueOps).set(
                eq(AccountLinkService.LINK_TOKEN_PREFIX + code),
                eq("whatsapp:user1"),
                eq(Duration.ofMinutes(10))
        );
    }

    @Test
    @DisplayName("link valid code — merges earnings, marks secondary MERGED, returns success")
    void link_validCode_returnsSuccess() {
        User primary = mock(User.class);
        User secondary = mock(User.class);
        EarningsRecord r1 = mock(EarningsRecord.class);
        EarningsRecord r2 = mock(EarningsRecord.class);

        when(valueOps.get(AccountLinkService.LINK_TOKEN_PREFIX + "123456")).thenReturn("whatsapp:primaryId");
        when(userRepository.findByPlatformAndUserId("whatsapp", "primaryId")).thenReturn(Optional.of(primary));
        when(userRepository.findByPlatformAndUserId("telegram", "secondaryId")).thenReturn(Optional.of(secondary));
        when(earningsRepository.findByUserIdAndPlatformOrderBySubmittedAtDesc("secondaryId", "telegram"))
                .thenReturn(List.of(r1, r2));

        AccountLinkService.LinkResult result = service.link("123456", "secondaryId", "telegram");

        assertThat(result.found()).isTrue();
        assertThat(result.valid()).isTrue();
        assertThat(result.success()).isTrue();
        assertThat(result.primaryUserId()).isEqualTo("primaryId");
        assertThat(result.recordsMerged()).isEqualTo(2);
        verify(secondary).setStatus("MERGED");
        verify(secondary).setLinkedUserId("primaryId");
        verify(r1).setUserId("primaryId");
        verify(r1).setPlatform("whatsapp");
        verify(r2).setUserId("primaryId");
        verify(r2).setPlatform("whatsapp");
        verify(redisTemplate).delete(AccountLinkService.LINK_TOKEN_PREFIX + "123456");
    }

    @Test
    @DisplayName("link with expired or unknown code — returns notFound")
    void link_expiredCode_returnsNotFound() {
        when(valueOps.get(AccountLinkService.LINK_TOKEN_PREFIX + "999999")).thenReturn(null);

        AccountLinkService.LinkResult result = service.link("999999", "secondaryId", "telegram");

        assertThat(result.found()).isFalse();
        assertThat(result.valid()).isFalse();
        assertThat(result.success()).isFalse();
    }

    @Test
    @DisplayName("link with malformed Redis value (no colon) — returns invalid")
    void link_malformedValue_returnsInvalid() {
        when(valueOps.get(AccountLinkService.LINK_TOKEN_PREFIX + "111111")).thenReturn("BADFORMAT");

        AccountLinkService.LinkResult result = service.link("111111", "secondaryId", "telegram");

        assertThat(result.found()).isTrue();
        assertThat(result.valid()).isFalse();
        assertThat(result.success()).isFalse();
    }
}
