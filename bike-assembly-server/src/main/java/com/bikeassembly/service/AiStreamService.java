package com.bikeassembly.service;

import com.bikeassembly.dto.AiQueryDTO;
import com.bikeassembly.context.BaseContext;
import com.bikeassembly.vo.AiStreamStatusVO;
import com.bikeassembly.ws.WebSocketNotifier;
import com.bikeassembly.ws.WsMessageDTO;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class AiStreamService {
    private final WebSocketNotifier webSocketNotifier;
    private final ChatClient.Builder chatClientBuilder;
    private final AiQueryService aiQueryService;
    private final Executor aiStreamExecutor;
    private final Cache<String, Submission> submissions = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofHours(24))
            .build();
    private final Cache<String, StreamState> streamStates = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofHours(24))
            .build();
    private final Cache<String, Boolean> activeSessions = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(10_000)
            .build();
    private static final Duration SUBMISSION_TTL = Duration.ofHours(24);
    @Autowired(required = false)
    private com.bikeassembly.utils.RedisService redisService;
    @Autowired(required = false)
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Autowired(required = false)
    private AiRequestAuditService auditService;

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;
    @Value("${spring.ai.openai.chat.options.model:}")
    private String model;
    @Value("${ai.timeout-seconds:120}")
    private long timeoutSeconds;

    public AiStreamService(WebSocketNotifier webSocketNotifier,
                           ChatClient.Builder chatClientBuilder,
                           AiQueryService aiQueryService,
                           @Qualifier("aiStreamExecutor") Executor aiStreamExecutor) {
        this.webSocketNotifier = webSocketNotifier;
        this.chatClientBuilder = chatClientBuilder;
        this.aiQueryService = aiQueryService;
        this.aiStreamExecutor = aiStreamExecutor;
    }

    public String submit(Long userId, String idempotencyKey, AiQueryDTO request) {
        String key = userId + ":" + idempotencyKey;
        RequestFingerprint fingerprint = new RequestFingerprint(
                request.getQuestion(), request.getDays(), request.getSessionId(),
                request.getRetrievalMode() == null ? "RAG" : request.getRetrievalMode().name());
        boolean sharedRedis = redisAvailable();
        if (sharedRedis) {
            RedisSubmission remote = redisService.get(submissionRedisKey(key), RedisSubmission.class);
            if (remote != null) {
                return verifyRemoteSubmission(remote, fingerprint);
            }
        }
        Submission submission = new Submission(UUID.randomUUID().toString(), fingerprint);
        Submission existing = submissions.asMap().putIfAbsent(key, submission);
        if (existing != null) {
            if (!existing.fingerprint().equals(fingerprint)) {
                throw new IllegalStateException("Idempotency-Key 已用于不同的 AI 请求");
            }
            return existing.requestId();
        }
        String activeKey = "ai:stream:active:" + userId + ":" + sessionId(request);
        boolean acquired = !sharedRedis
                ? activeSessions.asMap().putIfAbsent(activeKey, Boolean.TRUE) == null
                : redisService.setIfAbsent(activeKey, Boolean.TRUE, Duration.ofMinutes(10));
        if (!acquired) {
            submissions.invalidate(key);
            if (sharedRedis) {
                RedisSubmission winner = waitForRemoteSubmission(key);
                if (winner != null) return verifyRemoteSubmission(winner, fingerprint);
            }
            throw new IllegalStateException("当前会话已有 AI 请求正在处理");
        }
        String requestId = submission.requestId();
        String sessionId = request.getSessionId() == null || request.getSessionId().isBlank()
                ? requestId
                : request.getSessionId();
        String dataScope = BaseContext.getCurrentDataScope();
        StreamState state = new StreamState(userId, requestId, sessionId, dataScope);
        if (sharedRedis) {
            boolean recorded = redisService.setIfAbsent(
                    submissionRedisKey(key),
                    new RedisSubmission(requestId, fingerprint),
                    SUBMISSION_TTL);
            if (!recorded) {
                releaseActive(userId, sessionId);
                submissions.invalidate(key);
                RedisSubmission winner = waitForRemoteSubmission(key);
                if (winner != null) return verifyRemoteSubmission(winner, fingerprint);
                throw new IllegalStateException("AI 幂等状态暂时不可用，请稍后重试");
            }
        }
        streamStates.put(requestId, state);
        persist(state);
        push(userId, WsMessageDTO.EVENT_AI_REQUEST_ACCEPTED,
                Map.of("requestId", requestId, "sessionId", sessionId));
        try {
            aiStreamExecutor.execute(() -> streamAsync(userId, requestId, request));
        } catch (RuntimeException e) {
            submissions.invalidate(key);
            if (sharedRedis) redisService.delete(submissionRedisKey(key));
            state.fail("AI 服务繁忙，请稍后重试");
            releaseActive(state);
            sendError(userId, requestId, sessionId, "AI 服务繁忙，请稍后重试", new AtomicBoolean());
            throw new IllegalStateException("AI 服务繁忙，请稍后重试", e);
        }
        return requestId;
    }

    public AiStreamStatusVO getStatus(Long userId, String requestId) {
        StreamState state = load(requestId);
        if (state == null || !state.userId.equals(userId)) {
            return null;
        }
        return state.toVO();
    }

    public void streamAsync(Long userId, String requestId, AiQueryDTO request) {
        StreamState state = load(requestId);
        if (state == null) {
            log.warn("AI 流状态已过期: requestId={}", requestId);
            return;
        }
        String sessionId = request.getSessionId() == null || request.getSessionId().isBlank()
                ? requestId
                : request.getSessionId();
        AtomicBoolean terminalErrorSent = new AtomicBoolean(false);
        try {
            // 异步线程没有 HTTP 拦截器设置的 ThreadLocal，必须恢复数据范围，
            // 否则 DashboardScreenService 会按无用户处理，AI 看到的是空数据。
            BaseContext.setCurrentId(state.userId);
            BaseContext.setCurrentDataScope(state.dataScope);
            AiQueryService.StreamContext context = aiQueryService.buildStreamContext(request);
            if (context == null) {
                context = new AiQueryService.StreamContext("", List.of(),
                        state.dataScope, "");
            }
            state.setContext(context.sources(), context.dataTimeRange(), context.dataScope(),
                    context.graphNodes(), context.graphEdges());
            if (!aiConfigured()) {
                // 未配置模型时仍然返回结构化 MES 本地摘要，避免流式入口只能报错。
                var fallback = aiQueryService.ask(request);
                if (fallback == null) {
                    state.fail("AI 未配置：请设置 AI_OPENAI_API_KEY 后使用");
                    sendError(userId, requestId, sessionId,
                            "AI 未配置：请设置 AI_OPENAI_API_KEY 后使用", terminalErrorSent);
                    return;
                }
                String answer = fallback.getAnswer() == null ? "" : fallback.getAnswer();
                state.markFallback(fallback.getSources(), fallback.getModel());
                if (!answer.isBlank() && !cancelledAcrossInstances(requestId, state)) {
                    long seq = state.append(answer);
                    persist(state);
                    push(userId, WsMessageDTO.EVENT_AI_DELTA,
                            Map.of("requestId", requestId, "sessionId", sessionId,
                                    "seq", seq, "delta", answer, "sources", state.sources(),
                                    "fallback", true, "graphNodes", state.graphNodes(),
                                    "graphEdges", state.graphEdges()));
                }
                if (cancelledAcrossInstances(requestId, state)) return;
                state.complete();
                persist(state);
                releaseActive(state);
                push(userId, WsMessageDTO.EVENT_AI_COMPLETED,
                        Map.of("requestId", requestId, "sessionId", sessionId,
                                "seq", state.sequence(), "sources", state.sources(),
                                "dataTimeRange", state.dataTimeRange(), "model", state.model(),
                                "fallback", true, "message",
                                fallback.getMessage() == null ? "" : fallback.getMessage(),
                                "graphNodes", state.graphNodes(), "graphEdges", state.graphEdges()));
                return;
            }
            state.setModel(model);
            chatClientBuilder.build().prompt()
                    .system(aiQueryService.systemPrompt())
                    .user(context.prompt())
                    .stream()
                    .content()
                    .timeout(java.time.Duration.ofSeconds(Math.max(timeoutSeconds, 1)))
                    .doOnNext(delta -> {
                        if (cancelledAcrossInstances(requestId, state)) return;
                        long seq = state.append(delta);
                        persist(state);
                        push(userId, WsMessageDTO.EVENT_AI_DELTA,
                                Map.of("requestId", requestId, "sessionId", sessionId,
                                        "seq", seq, "delta", delta, "sources", state.sources(),
                                        "fallback", state.fallback(), "graphNodes", state.graphNodes(),
                                        "graphEdges", state.graphEdges()));
                    })
                    .doOnComplete(() -> {
                        if (cancelledAcrossInstances(requestId, state)) return;
                        state.complete();
                        persist(state);
                        releaseActive(state);
                        push(userId, WsMessageDTO.EVENT_AI_COMPLETED,
                                Map.of("requestId", requestId, "sessionId", sessionId,
                                        "seq", state.sequence(), "sources", state.sources(),
                                        "dataTimeRange", state.dataTimeRange(),
                                        "model", model, "fallback", state.fallback(),
                                        "graphNodes", state.graphNodes(), "graphEdges", state.graphEdges()));
                    })
                    .blockLast();
        } catch (Exception e) {
            String message = conciseError(e);
            log.warn("AI 流式请求失败: requestId={}, message={}", requestId, message);
            if (!state.hasContent()) {
                try {
                    String fallback = aiQueryService.fallbackAnswer(request);
                    if (fallback != null && !fallback.isBlank()) {
                        state.markFallback(List.of(), model);
                        long seq = state.append(fallback);
                        persist(state);
                        push(userId, WsMessageDTO.EVENT_AI_DELTA,
                                Map.of("requestId", requestId, "sessionId", sessionId,
                                        "seq", seq, "delta", fallback, "sources", state.sources(),
                                        "fallback", true, "graphNodes", state.graphNodes(),
                                        "graphEdges", state.graphEdges()));
                        state.complete();
                        persist(state);
                        releaseActive(state);
                        push(userId, WsMessageDTO.EVENT_AI_COMPLETED,
                                Map.of("requestId", requestId, "sessionId", sessionId,
                                        "seq", state.sequence(), "sources", state.sources(),
                                        "dataTimeRange", state.dataTimeRange(), "model", state.model(),
                                        "fallback", true, "message", "模型暂时不可用，已返回 MES 数据摘要",
                                        "graphNodes", state.graphNodes(), "graphEdges", state.graphEdges()));
                        return;
                    }
                } catch (Exception fallbackError) {
                    log.warn("AI 流式本地回退也失败: requestId={}, message={}",
                            requestId, conciseError(fallbackError));
                }
            }
            state.fail(message);
            persist(state);
            releaseActive(state);
            sendError(userId, requestId, sessionId, "AI 请求失败：" + message, terminalErrorSent);
        } finally {
            BaseContext.removeAll();
        }
    }

    public boolean cancel(Long userId, String requestId) {
        StreamState state = load(requestId);
        if (state == null || !state.userId.equals(userId)) return false;
        if ("COMPLETED".equals(state.status) || "FAILED".equals(state.status)
                || "CANCELLED".equals(state.status)) return false;
        state.cancel();
        persist(state);
        releaseActive(state);
        push(userId, WsMessageDTO.EVENT_AI_ERROR,
                Map.of("requestId", requestId, "sessionId", state.sessionId(),
                        "seq", state.sequence(), "sources", state.sources(),
                        "fallback", state.fallback(), "message", "AI 请求已取消",
                        "code", "CANCELLED"));
        return true;
    }

    private String redisKey(String requestId) {
        return "ai:stream:" + requestId;
    }

    private String submissionRedisKey(String key) {
        return "ai:stream:idempotency:" + key;
    }

    private boolean redisAvailable() {
        return redisService != null && redisService.isAvailable();
    }

    private void persist(StreamState state) {
        streamStates.put(state.requestId(), state);
        if (redisAvailable()) {
            redisService.set(redisKey(state.requestId()), state, Duration.ofHours(24));
        }
    }

    private void releaseActive(StreamState state) {
        String key = "ai:stream:active:" + state.userId + ":" + state.sessionId;
        activeSessions.invalidate(key);
        if (redisAvailable()) redisService.delete(key);
    }

    private void releaseActive(Long userId, String sessionId) {
        String key = "ai:stream:active:" + userId + ":" + sessionId;
        activeSessions.invalidate(key);
        if (redisAvailable()) redisService.delete(key);
    }

    private String sessionId(AiQueryDTO request) {
        return request.getSessionId() == null || request.getSessionId().isBlank()
                ? "default" : request.getSessionId();
    }

    private StreamState load(String requestId) {
        if (redisAvailable()) {
            StreamState remote = redisService.get(redisKey(requestId), StreamState.class);
            if (remote != null) {
                streamStates.put(requestId, remote);
                return remote;
            }
        }
        StreamState local = streamStates.getIfPresent(requestId);
        if (local != null) return local;
        return null;
    }

    private RedisSubmission waitForRemoteSubmission(String key) {
        if (!redisAvailable()) return null;
        for (int attempt = 0; attempt < 3; attempt++) {
            RedisSubmission remote = redisService.get(submissionRedisKey(key), RedisSubmission.class);
            if (remote != null) return remote;
            try {
                Thread.sleep(20L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private String verifyRemoteSubmission(RedisSubmission remote, RequestFingerprint fingerprint) {
        RequestFingerprint remoteFingerprint = remote.fingerprint();
        if (!remoteFingerprint.equals(fingerprint)) {
            throw new IllegalStateException("Idempotency-Key 已用于不同的 AI 请求");
        }
        return remote.requestId;
    }

    private boolean cancelledAcrossInstances(String requestId, StreamState local) {
        if (local.cancelled()) return true;
        if (!redisAvailable()) return false;
        StreamState remote = redisService.get(redisKey(requestId), StreamState.class);
        if (remote != null && remote.cancelled()) {
            local.cancel();
            return true;
        }
        return false;
    }

    private void sendError(Long userId, String requestId, String sessionId,
                           String message, AtomicBoolean terminalErrorSent) {
        if (terminalErrorSent.compareAndSet(false, true)) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("requestId", requestId);
            data.put("sessionId", sessionId);
            data.put("message", message);
            data.put("seq", 0L);
            data.put("sources", List.of());
            data.put("fallback", false);
            push(userId, WsMessageDTO.EVENT_AI_ERROR,
                    data);
        }
    }

    private void push(Long userId, String eventType, Map<String, Object> data) {
        webSocketNotifier.pushToAdminUser(userId, WsMessageDTO.BIZ_AI, eventType, data);
    }

    private record RequestFingerprint(String question, Integer days, String sessionId,
                                      String retrievalMode) {}

    private record Submission(String requestId, RequestFingerprint fingerprint) {}

    @com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility =
            com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)
    private static final class RedisSubmission {
        private String requestId;
        private String question;
        private Integer days;
        private String sessionId;
        private String retrievalMode;

        private RedisSubmission() {
        }

        private RedisSubmission(String requestId, RequestFingerprint fingerprint) {
            this.requestId = requestId;
            this.question = fingerprint.question();
            this.days = fingerprint.days();
            this.sessionId = fingerprint.sessionId();
            this.retrievalMode = fingerprint.retrievalMode();
        }

        private RequestFingerprint fingerprint() {
            return new RequestFingerprint(question, days, sessionId, retrievalMode);
        }
    }

    @com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility =
            com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)
    private static final class StreamState {
        private Long userId;
        private String requestId;
        private String sessionId;
        private String dataScope;
        private String dataTimeRange;
        private StringBuilder content = new StringBuilder();
        private String status = "ACCEPTED";
        private String message;
        private long sequence;
        private boolean fallback;
        private List<String> sources = List.of();
        private List<com.bikeassembly.vo.KnowledgeGraphNodeVO> graphNodes = List.of();
        private List<com.bikeassembly.vo.KnowledgeGraphEdgeVO> graphEdges = List.of();
        private String model;
        private LocalDateTime startedAt = LocalDateTime.now();
        private LocalDateTime completedAt;

        private StreamState() {
        }

        private StreamState(Long userId, String requestId, String sessionId, String dataScope) {
            this.userId = userId;
            this.requestId = requestId;
            this.sessionId = sessionId;
            this.dataScope = dataScope == null ? "SELF" : dataScope;
        }

        private synchronized long append(String delta) {
            content.append(delta);
            status = "STREAMING";
            return ++sequence;
        }

        private synchronized boolean hasContent() {
            return content.length() > 0;
        }

        private synchronized void complete() {
            status = "COMPLETED";
            completedAt = LocalDateTime.now();
        }

        private synchronized void fail(String errorMessage) {
            status = "FAILED";
            message = errorMessage;
            completedAt = LocalDateTime.now();
        }

        private synchronized void cancel() {
            status = "CANCELLED";
            message = "AI 请求已取消";
            completedAt = LocalDateTime.now();
        }

        private synchronized AiStreamStatusVO toVO() {
            return AiStreamStatusVO.builder()
                    .requestId(requestId)
                    .sessionId(sessionId)
                    .status(status)
                    .content(content.toString())
                    .sequence(sequence)
                    .dataScope(dataScope)
                    .dataTimeRange(dataTimeRange)
                    .model(model)
                    .fallback(fallback)
                    .sources(sources)
                    .graphNodes(graphNodes)
                    .graphEdges(graphEdges)
                    .startedAt(startedAt)
                    .completedAt(completedAt)
                    .message(message)
                    .build();
        }

        private synchronized long sequence() { return sequence; }
        private synchronized List<String> sources() { return sources; }
        private synchronized List<com.bikeassembly.vo.KnowledgeGraphNodeVO> graphNodes() { return graphNodes; }
        private synchronized List<com.bikeassembly.vo.KnowledgeGraphEdgeVO> graphEdges() { return graphEdges; }
        private synchronized boolean fallback() { return fallback; }
        private synchronized boolean cancelled() { return "CANCELLED".equals(status); }
        private synchronized String model() { return model; }
        private synchronized String dataTimeRange() { return dataTimeRange; }
        private synchronized void markFallback(List<String> sourceList, String modelName) {
            fallback = true;
            sources = sourceList == null ? List.of() : List.copyOf(sourceList);
            model = modelName;
        }
        private synchronized void setContext(List<String> sourceList, String timeRange,
                                             String resolvedScope,
                                             List<com.bikeassembly.vo.KnowledgeGraphNodeVO> nodes,
                                             List<com.bikeassembly.vo.KnowledgeGraphEdgeVO> edges) {
            sources = sourceList == null ? List.of() : List.copyOf(sourceList);
            graphNodes = nodes == null ? List.of() : List.copyOf(nodes);
            graphEdges = edges == null ? List.of() : List.copyOf(edges);
            dataTimeRange = timeRange;
            if (resolvedScope != null && !resolvedScope.isBlank()) {
                dataScope = resolvedScope;
            }
        }
        private synchronized void setModel(String modelName) {
            model = modelName;
        }
        private String requestId() { return requestId; }
        private String sessionId() { return sessionId; }
    }

    private String conciseError(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        if (message == null || message.isBlank()) return cause.getClass().getSimpleName();
        message = message.replaceAll("\\s+", " ").trim();
        return message.length() > 160 ? message.substring(0, 160) + "..." : message;
    }

    private boolean aiConfigured() {
        return apiKey != null && !apiKey.isBlank()
                && !"local-disabled-key".equals(apiKey)
                && model != null && !model.isBlank();
    }
}
