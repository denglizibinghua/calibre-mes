package com.bikeassembly.config;

import com.bikeassembly.ws.WsHandshakeInterceptor;
import com.bikeassembly.ws.WsTextHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 端点：/ws，握手携带 token 认证，跨域来源与 CORS 白名单一致。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final WsTextHandler wsTextHandler;
    private final WsHandshakeInterceptor wsHandshakeInterceptor;

    @Value("${bike.cors.allowed-origins:http://localhost:5173}")
    private String[] allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(wsTextHandler, "/ws")
                .addInterceptors(wsHandshakeInterceptor)
                .setAllowedOriginPatterns(allowedOrigins);
    }
}
