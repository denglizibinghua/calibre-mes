package com.bikeassembly.service;

import com.bikeassembly.entity.AiRequestAudit;
import com.bikeassembly.mapper.AiRequestAuditMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.HexFormat;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiRequestAuditService {
    private final AiRequestAuditMapper mapper;
    private final ObjectMapper objectMapper;

    public void record(String requestId, String sessionId, Long userId, String operation,
                       String question, String dataScope, String dataTimeRange,
                       String model, List<String> sources, String inputSummary,
                       String status, boolean fallback, long latencyMs, String errorCode) {
        if (userId == null) return;
        AiRequestAudit audit = new AiRequestAudit();
        audit.setRequestId(shorten(requestId, 64));
        audit.setSessionId(shorten(sessionId, 64));
        audit.setUserId(userId);
        audit.setOperation(shorten(operation, 32));
        audit.setQuestionHash(hash(question));
        audit.setDataScope(shorten(dataScope, 16));
        audit.setDataTimeRange(shorten(dataTimeRange, 128));
        audit.setModel(shorten(model, 128));
        audit.setSourcesJson(toJson(sources));
        audit.setInputSummary(shorten(inputSummary, 2000));
        audit.setStatus(shorten(status, 24));
        audit.setFallback(fallback);
        audit.setLatencyMs(Math.max(latencyMs, 0));
        audit.setErrorCode(shorten(errorCode, 64));
        audit.setCreatedAt(LocalDateTime.now());
        try {
            mapper.insert(audit);
        } catch (Exception e) {
            log.warn("AI 审计写入失败，不影响主流程: {}", e.getMessage());
        }
    }

    private String hash(String value) {
        if (value == null) return null;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return null;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private String shorten(String value, int max) {
        if (value == null) return null;
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }
}
