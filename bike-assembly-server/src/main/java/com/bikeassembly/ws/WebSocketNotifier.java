package com.bikeassembly.ws;

import com.bikeassembly.utils.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * WebSocket 推送器：向管理端全部在线连接或指定用户推送消息。
 * <p>
 * 推送失败不影响主业务（与 MQ 发送同样采用 try-catch 降级模式）。
 */
@Component
@Slf4j
public class WebSocketNotifier {

    private final ObjectMapper objectMapper;
    private final WsSessionManager sessionManager;

    public WebSocketNotifier(WsSessionManager sessionManager, ObjectMapper objectMapper) {
        this.sessionManager = sessionManager;
        SimpleModule dates = new SimpleModule("websocket-timestamps");
        dates.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        this.objectMapper = objectMapper.copy().registerModule(dates)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /** 推送给管理端全部在线连接 */
    public void pushToAdmin(String bizType, String eventType, Map<String, Object> data) {
        push(sessionManager.getAllByClientType(JwtUtil.CLIENT_TYPE_ADMIN),
                WsMessageDTO.of(bizType, eventType, data));
    }

    /** 推送给 app 端指定用户 */
    public void pushToUser(Long userId, String bizType, String eventType, Map<String, Object> data) {
        if (userId == null) {
            return;
        }
        push(sessionManager.getSessions(JwtUtil.CLIENT_TYPE_APP, userId),
                WsMessageDTO.of(bizType, eventType, data));
    }

    /** Push to one authenticated admin session. */
    public void pushToAdminUser(Long userId, String bizType, String eventType, Map<String, Object> data) {
        if (userId == null) {
            return;
        }
        push(sessionManager.getSessions(JwtUtil.CLIENT_TYPE_ADMIN, userId),
                WsMessageDTO.of(bizType, eventType, data));
    }

    private void push(Collection<WebSocketSession> sessions, WsMessageDTO message) {
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(message);
            for (WebSocketSession session : sessions) {
                try {
                    if (session.isOpen()) {
                        synchronized (session) {
                            session.sendMessage(new TextMessage(payload));
                        }
                    } else {
                        sessionManager.closeAndRemove(session);
                    }
                } catch (Exception e) {
                    sessionManager.closeAndRemove(session);
                    log.debug("WS 推送单连接失败：bizType={}, eventType={}",
                            message.getBizType(), message.getEventType());
                }
            }
        } catch (Exception e) {
            log.warn("WS 推送失败（不影响主业务）：bizType={}, eventType={}, error={}",
                    message.getBizType(), message.getEventType(), e.getMessage());
        }
    }
}
