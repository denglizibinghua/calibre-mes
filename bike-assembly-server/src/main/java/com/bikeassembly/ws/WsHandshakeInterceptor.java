package com.bikeassembly.ws;

import com.bikeassembly.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手认证：从 query 参数 token 解析 JWT（浏览器 WebSocket 无法自定义 header）。
 * <p>
 * 校验：签名有效、未过期、tokenType=access、clientType 合法；解析结果放入 attributes 供连接处理器使用。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WsHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            log.warn("WS 握手失败：非 Servlet 请求");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        String token = servletRequest.getServletRequest().getParameter("token");
        if (token == null || token.isBlank()) {
            log.warn("WS 握手失败：缺少 token");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            Claims claims = jwtUtil.parseTokenAny(token);
            String tokenType = claims.get("tokenType", String.class);
            String clientType = claims.get("clientType", String.class);
            if (!JwtUtil.TOKEN_TYPE_ACCESS.equals(tokenType)) {
                log.warn("WS 握手失败：非 access token");
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            if (!JwtUtil.CLIENT_TYPE_ADMIN.equals(clientType)
                    && !JwtUtil.CLIENT_TYPE_APP.equals(clientType)) {
                log.warn("WS 握手失败：非法客户端类型");
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            // 验证类型对应的密钥，避免 app 密钥签发的 token 冒充 admin。
            claims = JwtUtil.CLIENT_TYPE_ADMIN.equals(clientType)
                    ? jwtUtil.parseTokenWithAdminKey(token) : jwtUtil.parseTokenWithAppKey(token);
            Long userId = claims.get("userId", Long.class);
            if (userId == null || userId <= 0 || claims.getExpiration() == null) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            attributes.put("userId", userId);
            attributes.put("clientType", clientType);
            log.debug("WS 握手通过：clientType={}, userId={}", clientType, claims.get("userId", Long.class));
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("WS 握手失败：认证凭证无效");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需额外处理
    }
}
