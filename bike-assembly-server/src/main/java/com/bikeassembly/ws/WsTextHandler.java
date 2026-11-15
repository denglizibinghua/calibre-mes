package com.bikeassembly.ws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WebSocket 文本处理器：维护会话注册表，响应前端心跳。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WsTextHandler extends TextWebSocketHandler {

    private final WsSessionManager sessionManager;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get("userId");
        String clientType = (String) session.getAttributes().get("clientType");
        sessionManager.add(clientType, userId, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        String clientType = (String) session.getAttributes().get("clientType");
        sessionManager.remove(clientType, userId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        if (!"ping".equals(message.getPayload())) {
            return;
        }
        try {
            if (session.isOpen()) {
                synchronized (session) {
                    session.sendMessage(new TextMessage("pong"));
                }
            }
        } catch (Exception e) {
            sessionManager.closeAndRemove(session);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessionManager.closeAndRemove(session);
    }
}
