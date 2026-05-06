package com.gigsarathi.flow;

import com.gigsarathi.bot.MessageSenderRouter;
import com.gigsarathi.domain.event.EventService;
import com.gigsarathi.domain.user.User;
import com.gigsarathi.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnboardingFlowTest {

    @Mock
    private SessionService sessionService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EventService eventService;

    @Mock
    private MessageSenderRouter messageSender;

    private OnboardingFlow onboardingFlow;

    @BeforeEach
    void setUp() {
        onboardingFlow = new OnboardingFlow(sessionService, userRepository, eventService, messageSender);
    }

    @Test
    @DisplayName("start sends work-type buttons exactly per spec")
    void start_sendsWorkTypeButtons() {
        onboardingFlow.start("user-1", "telegram");

        ArgumentCaptor<List<String>> buttonsCaptor = ArgumentCaptor.forClass(List.class);
        verify(messageSender).sendButtonMessage(
                eq("user-1"),
                eq("telegram"),
                eq("Welcome to Gigsarathi! 🛵 What type of gig work do you do?"),
                buttonsCaptor.capture()
        );
        assertThat(buttonsCaptor.getValue())
                .containsExactly("Food Delivery", "Ride", "Courier", "Multiple");
        verify(eventService).emit(eq("onboarding_started"), eq("user-1"), eq("telegram"), anyMap());
        verify(sessionService).saveSession(eq("telegram"), eq("user-1"), any(SessionState.class));
    }

    @Test
    @DisplayName("step 3 city creates User with COMPLETED status and ACTIVE")
    void cityStep_createsUser() {
        Map<String, Object> pending = new HashMap<>();
        pending.put("workType", "Ride");
        pending.put("appsUsed", List.of());
        SessionState state = SessionState.builder()
                .flowType(FlowType.ONBOARDING.name())
                .stepIndex(3)
                .pendingData(pending)
                .build();

        when(userRepository.findByPlatformAndUserId("whatsapp", "+91999"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        onboardingFlow.handle("+91999", "whatsapp", "Kolhapur", state);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getUserId()).isEqualTo("+91999");
        assertThat(saved.getPlatform()).isEqualTo("whatsapp");
        assertThat(saved.getCity()).isEqualTo("Kolhapur");
        assertThat(saved.getOnboardingStatus()).isEqualTo("COMPLETED");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getWorkType()).isEqualTo("Ride");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getLastActiveAt()).isNotNull();
        assertThat(saved.getPhoneNumber()).isEqualTo("+91999");

        verify(eventService).emit(eq("onboarding_completed"), eq("+91999"), eq("whatsapp"), anyMap());
        verify(sessionService).clearSession("whatsapp", "+91999");
        verify(messageSender).sendMessage(eq("+91999"), eq("whatsapp"), anyString());
        verify(messageSender, never()).sendButtonMessage(anyString(), anyString(), anyString(), any());
    }
}
