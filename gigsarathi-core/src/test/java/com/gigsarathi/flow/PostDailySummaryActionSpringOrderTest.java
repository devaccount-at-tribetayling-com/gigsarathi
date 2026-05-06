package com.gigsarathi.flow;

import com.gigsarathi.domain.earnings.EarningsRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that Spring injects PostDailySummaryAction beans in @Order(N) sequence.
 * Tests 1–5 in PostDailySummaryActionTest use hand-constructed lists; this is the
 * only test that catches a forgotten @Order annotation on a real @Component bean.
 */
@SpringJUnitConfig(PostDailySummaryActionSpringOrderTest.TestConfig.class)
class PostDailySummaryActionSpringOrderTest {

    static class HighPriorityAction implements PostDailySummaryAction {
        @Override
        public Optional<SessionState> apply(String userId, String platform, EarningsRecord record) {
            return Optional.empty();
        }
    }

    static class LowPriorityAction implements PostDailySummaryAction {
        @Override
        public Optional<SessionState> apply(String userId, String platform, EarningsRecord record) {
            return Optional.empty();
        }
    }

    @Configuration
    static class TestConfig {
        @Bean
        @Order(10)
        PostDailySummaryAction highPriority() {
            return new HighPriorityAction();
        }

        @Bean
        @Order(20)
        PostDailySummaryAction lowPriority() {
            return new LowPriorityAction();
        }
    }

    @Autowired
    List<PostDailySummaryAction> actions;

    @Test
    @DisplayName("@Order(10) bean appears at index 0 before @Order(20) bean")
    void order10BeforeOrder20() {
        assertThat(actions).hasSize(2);
        assertThat(actions.get(0)).isInstanceOf(HighPriorityAction.class);
        assertThat(actions.get(1)).isInstanceOf(LowPriorityAction.class);
    }
}
