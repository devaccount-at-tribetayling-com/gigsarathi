package com.gigsarathi.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gigsarathi.flow.FlowEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.data.mongodb.uri=mongodb://localhost:27017/gigsarathi-test",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379",
        "gigsarathi.whatsapp.api-url=https://graph.facebook.com/v19.0",
        "gigsarathi.whatsapp.phone-number-id=test",
        "gigsarathi.whatsapp.access-token=test",
        "gigsarathi.telegram.bot-token=test",
        "gigsarathi.telegram.bot-username=test",
        "gigsarathi.admin.api-key=test-admin"
})
class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FlowEngine flowEngine;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("rejects request missing required fields with 400")
    void rejects_missingFields() throws Exception {
        InboundMessageRequest request = InboundMessageRequest.builder()
                .platform("")
                .userId("")
                .messageType("")
                .payload(Map.of())
                .build();

        mockMvc.perform(post("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("duplicate messageId returns status=duplicate on second post")
    void duplicate_messageId_returnsDuplicate() throws Exception {
        InboundMessageRequest request = InboundMessageRequest.builder()
                .platform("whatsapp")
                .userId("+919876543210")
                .messageType("text")
                .payload(Map.of(
                        "id", "wamid.test-" + System.nanoTime(),
                        "text", Map.of("body", "hello")
                ))
                .build();

        String json = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));

        mockMvc.perform(post("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("duplicate"));

        verify(flowEngine, times(1))
                .handle(org.mockito.ArgumentMatchers.eq("+919876543210"),
                        org.mockito.ArgumentMatchers.eq("whatsapp"),
                        org.mockito.ArgumentMatchers.eq("hello"),
                        org.mockito.ArgumentMatchers.eq("text"),
                        org.mockito.ArgumentMatchers.anyMap());
    }
}
