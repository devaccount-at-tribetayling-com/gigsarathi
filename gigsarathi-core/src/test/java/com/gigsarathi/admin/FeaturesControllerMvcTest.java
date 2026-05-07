package com.gigsarathi.admin;

import com.gigsarathi.config.AdminKeyInterceptor;
import com.gigsarathi.config.AppProperties;
import com.gigsarathi.config.WebMvcConfig;
import com.gigsarathi.domain.config.AppConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FeaturesController.class)
@Import({WebMvcConfig.class, AdminKeyInterceptor.class})
class FeaturesControllerMvcTest {

    @MockBean private AppConfigRepository appConfigRepository;
    @MockBean private AppProperties appProperties;

    @Autowired private MockMvc mockMvc;

    @BeforeEach
    void setUpAdminKey() {
        AppProperties.Admin admin = new AppProperties.Admin();
        admin.setApiKey("test-admin-key");
        when(appProperties.getAdmin()).thenReturn(admin);
    }

    @Test
    @DisplayName("PATCH /admin/features without X-Admin-Key header — 401 Unauthorized")
    void updateFeatures_missingAdminKey_returns401() throws Exception {
        mockMvc.perform(patch("/admin/features")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PATCH /admin/features with wrong X-Admin-Key — 401 Unauthorized")
    void updateFeatures_wrongAdminKey_returns401() throws Exception {
        mockMvc.perform(patch("/admin/features")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Admin-Key", "wrong-key")
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
