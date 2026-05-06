package com.gigsarathi.referral;

import com.gigsarathi.domain.referral.ReferralCode;
import com.gigsarathi.domain.referral.ReferralRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferralServiceTest {

    @Mock private ReferralRepository referralRepository;

    private ReferralService service;

    @BeforeEach
    void setUp() {
        service = new ReferralService(referralRepository);
    }

    @Test
    @DisplayName("ensureReferralCode returns existing code without creating a new one")
    void ensureReferralCode_existingCode_returnsSame() {
        ReferralCode existing = referralCode("user1", "whatsapp", "EXIST123");
        when(referralRepository.findByUserIdAndPlatform("user1", "whatsapp"))
                .thenReturn(Optional.of(existing));

        ReferralCode result = service.ensureReferralCode("user1", "whatsapp");

        assertThat(result.getCode()).isEqualTo("EXIST123");
        verify(referralRepository, times(0)).save(any());
    }

    @Test
    @DisplayName("ensureReferralCode creates new code when none exists")
    void ensureReferralCode_noExisting_createsNew() {
        when(referralRepository.findByUserIdAndPlatform("user2", "whatsapp"))
                .thenReturn(Optional.empty());
        ReferralCode saved = referralCode("user2", "whatsapp", "NEWCODE1");
        when(referralRepository.save(any())).thenReturn(saved);

        ReferralCode result = service.ensureReferralCode("user2", "whatsapp");

        assertThat(result.getCode()).isEqualTo("NEWCODE1");
        ArgumentCaptor<ReferralCode> captor = ArgumentCaptor.forClass(ReferralCode.class);
        verify(referralRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo("user2");
        assertThat(captor.getValue().getPlatform()).isEqualTo("whatsapp");
        assertThat(captor.getValue().getRewardAmount()).isNull();
        assertThat(captor.getValue().getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("ensureReferralCode retries on duplicate key collision and returns existing")
    void ensureReferralCode_collision_retriesAndReturnsExisting() {
        when(referralRepository.findByUserIdAndPlatform("user3", "whatsapp"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(referralCode("user3", "whatsapp", "RACEWIN1")));
        when(referralRepository.save(any()))
                .thenThrow(new DuplicateKeyException("code collision"));

        ReferralCode result = service.ensureReferralCode("user3", "whatsapp");

        assertThat(result.getCode()).isEqualTo("RACEWIN1");
    }

    @Test
    @DisplayName("getReferralCode returns empty when user has no code")
    void getReferralCode_noCode_returnsEmpty() {
        when(referralRepository.findByUserIdAndPlatform("user4", "telegram"))
                .thenReturn(Optional.empty());

        Optional<ReferralCode> result = service.getReferralCode("user4", "telegram");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("generated code uses only characters from safe alphabet")
    void ensureReferralCode_generatedCode_usesOnlySafeChars() {
        when(referralRepository.findByUserIdAndPlatform(any(), any()))
                .thenReturn(Optional.empty());
        when(referralRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReferralCode result = service.ensureReferralCode("user5", "whatsapp");

        assertThat(result.getCode()).hasSize(8);
        assertThat(result.getCode()).matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]+");
    }

    @Test
    @DisplayName("exhausting all retry attempts with no recovery throws IllegalStateException")
    void ensureReferralCode_allRetriesFail_throws() {
        when(referralRepository.findByUserIdAndPlatform(any(), any()))
                .thenReturn(Optional.empty());
        when(referralRepository.save(any()))
                .thenThrow(new DuplicateKeyException("collision"));

        assertThatThrownBy(() -> service.ensureReferralCode("user6", "whatsapp"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to create referral code");
    }

    private ReferralCode referralCode(String userId, String platform, String code) {
        ReferralCode rc = new ReferralCode();
        rc.setUserId(userId);
        rc.setPlatform(platform);
        rc.setCode(code);
        return rc;
    }
}
