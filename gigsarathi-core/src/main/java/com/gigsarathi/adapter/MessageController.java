package com.gigsarathi.adapter;

import com.gigsarathi.domain.idempotency.IdempotencyRecord;
import com.gigsarathi.domain.idempotency.IdempotencyRepository;
import com.gigsarathi.flow.FlowEngine;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1")
public class MessageController {

    private final IdempotencyRepository idempotencyRepository;
    private final FlowEngine flowEngine;

    public MessageController(IdempotencyRepository idempotencyRepository, FlowEngine flowEngine) {
        this.idempotencyRepository = idempotencyRepository;
        this.flowEngine = flowEngine;
    }

    @PostMapping("/messages")
    public ResponseEntity<Map<String, String>> ingest(@Valid @RequestBody InboundMessageRequest request) {
        String platform = request.getPlatform();
        String userId = request.getUserId();
        Map<String, Object> payload = request.getPayload() != null ? request.getPayload() : Map.of();

        String messageId = extractMessageId(platform, payload);
        if (messageId == null || messageId.isBlank()) {
            log.warn("No messageId in payload for platform={} userId={}", platform, userId);
            return ResponseEntity.ok(Map.of("status", "accepted"));
        }

        if (idempotencyRepository.existsByPlatformAndMessageId(platform, messageId)) {
            return ResponseEntity.ok(Map.of("status", "duplicate"));
        }

        try {
            idempotencyRepository.save(IdempotencyRecord.builder()
                    .platform(platform)
                    .messageId(messageId)
                    .createdAt(Instant.now())
                    .build());
        } catch (DuplicateKeyException dup) {
            return ResponseEntity.ok(Map.of("status", "duplicate"));
        }

        String text = extractText(platform, payload);
        try {
            flowEngine.handle(userId, platform, text, request.getMessageType(), payload);
        } catch (Exception ex) {
            log.error("Flow handling failed for {}/{}: {}", platform, userId, ex.getMessage(), ex);
        }

        return ResponseEntity.ok(Map.of("status", "accepted"));
    }

    @SuppressWarnings("unchecked")
    private String extractMessageId(String platform, Map<String, Object> payload) {
        if ("whatsapp".equalsIgnoreCase(platform)) {
            Object id = payload.get("id");
            return id != null ? id.toString() : null;
        }
        if ("telegram".equalsIgnoreCase(platform)) {
            Object updateId = payload.get("update_id");
            return updateId != null ? updateId.toString() : null;
        }
        Object id = payload.get("id");
        return id != null ? id.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private String extractText(String platform, Map<String, Object> payload) {
        if ("whatsapp".equalsIgnoreCase(platform)) {
            Object textObj = payload.get("text");
            if (textObj instanceof Map<?, ?> map) {
                Object body = map.get("body");
                return body != null ? body.toString() : "";
            }
            // interactive button reply
            Object interactive = payload.get("interactive");
            if (interactive instanceof Map<?, ?> imap) {
                Object br = imap.get("button_reply");
                if (br instanceof Map<?, ?> brm) {
                    Object title = brm.get("title");
                    if (title != null) return title.toString();
                }
                Object lr = imap.get("list_reply");
                if (lr instanceof Map<?, ?> lrm) {
                    Object title = lrm.get("title");
                    if (title != null) return title.toString();
                }
            }
            return "";
        }
        if ("telegram".equalsIgnoreCase(platform)) {
            Object text = payload.get("text");
            return text != null ? text.toString() : "";
        }
        Object text = payload.get("text");
        return text != null ? text.toString() : "";
    }
}
