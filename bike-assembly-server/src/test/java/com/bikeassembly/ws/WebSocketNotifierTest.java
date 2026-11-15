package com.bikeassembly.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bikeassembly.config.JacksonConfig;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(WebSocketNotifierTest.Config.class)
class WebSocketNotifierTest {
    @Configuration
    @Import({WsSessionManager.class, WebSocketNotifier.class, JacksonConfig.class})
    static class Config {
        @Bean ObjectMapper objectMapper(Jackson2ObjectMapperBuilderCustomizer customizer) {
            Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
            customizer.customize(builder);
            return builder.build();
        }
    }
    @Autowired WsSessionManager manager;
    @Autowired WebSocketNotifier notifier;

    @Test
    void deliversStringTimestampAndIsolatesTargetsAndFailedSessions() throws Exception {
        WebSocketSession bad = session("admin", 501L);
        WebSocketSession admin = session("admin", 502L);
        WebSocketSession app = session("app", 501L);
        WebSocketSession other = session("app", 503L);
        try {
            doThrow(new IOException("broken")).when(bad).sendMessage(any());
            notifier.pushToAdmin("ANDON", "CREATE", Map.of("id", 1L));
            ArgumentCaptor<TextMessage> message = ArgumentCaptor.forClass(TextMessage.class);
            verify(admin).sendMessage(message.capture());
            var json = new ObjectMapper().readTree(message.getValue().getPayload());
            assertEquals("ANDON", json.get("bizType").asText());
            assertTrue(json.get("timestamp").isTextual());
            assertNotNull(LocalDateTime.parse(json.get("timestamp").asText()));
            assertTrue(manager.getSessions("admin", 501L).isEmpty());
            verify(bad).close();
            verify(app, never()).sendMessage(any());
            notifier.pushToUser(501L, "ANDON", "ACCEPT", Map.of("id", 1L));
            verify(app).sendMessage(any(TextMessage.class));
            notifier.pushToUser(null, "ANDON", "CLOSE", Map.of());
            verify(other, never()).sendMessage(any());
        } finally {
            manager.remove("admin", 501L, bad);
            manager.remove("admin", 502L, admin);
            manager.remove("app", 501L, app);
            manager.remove("app", 503L, other);
        }
    }

    private WebSocketSession session(String client, Long user) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(Map.of("clientType", client, "userId", user));
        when(session.isOpen()).thenReturn(true);
        manager.add(client, user, session);
        return session;
    }
}
