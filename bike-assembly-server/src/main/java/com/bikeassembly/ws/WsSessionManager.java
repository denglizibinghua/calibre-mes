package com.bikeassembly.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket 会话注册表：按 clientType + userId 管理连接。
 * <p>
 * 单实例部署使用内存；多实例部署需配合 Redis Pub/Sub 广播（预留扩展点）。
 */
@Component
@Slf4j
public class WsSessionManager {

    /** key = clientType:userId，value = 该用户的连接集合 */
    private final ConcurrentMap<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    private String key(String clientType, Long userId) {
        return clientType + ":" + userId;
    }

    public void add(String clientType, Long userId, WebSocketSession session) {
        sessions.compute(key(clientType, userId), (k, userSessions) -> {
            Set<WebSocketSession> result = userSessions == null ? new CopyOnWriteArraySet<>() : userSessions;
            result.add(session);
            return result;
        });
        log.debug("WS 连接建立：clientType={}, userId={}, total={}",
                clientType, userId, sessions.values().stream().mapToInt(Set::size).sum());
    }

    public void remove(String clientType, Long userId, WebSocketSession session) {
        sessions.computeIfPresent(key(clientType, userId), (k, userSessions) -> {
            userSessions.remove(session);
            return userSessions.isEmpty() ? null : userSessions;
        });
    }

    /** 获取指定用户（app 端）的连接 */
    public List<WebSocketSession> getSessions(String clientType, Long userId) {
        Set<WebSocketSession> userSessions = sessions.get(key(clientType, userId));
        return userSessions == null ? new ArrayList<>() : new ArrayList<>(userSessions);
    }

    /** 获取某客户端类型（如 admin）的全部连接 */
    public List<WebSocketSession> getAllByClientType(String clientType) {
        List<WebSocketSession> result = new ArrayList<>();
        String prefix = clientType + ":";
        sessions.forEach((k, v) -> {
            if (k.startsWith(prefix)) {
                result.addAll(v);
            }
        });
        return result;
    }

    /** 关闭并移除指定会话 */
    public void closeAndRemove(String clientType, Long userId, WebSocketSession session) {
        remove(clientType, userId, session);
        try {
            if (session.isOpen()) {
                session.close();
            }
        } catch (IOException | RuntimeException e) {
            log.debug("关闭 WS 会话失败，sessionId={}", session.getId());
        }
    }

    /** 从可信握手属性获取身份，供传输异常与推送失败统一清理。 */
    public void closeAndRemove(WebSocketSession session) {
        String clientType = (String) session.getAttributes().get("clientType");
        Long userId = (Long) session.getAttributes().get("userId");
        closeAndRemove(clientType, userId, session);
    }
}
