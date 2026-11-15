package com.bikeassembly.ws;

import com.bikeassembly.config.WebSocketConfig;
import com.bikeassembly.config.JacksonConfig;
import com.bikeassembly.utils.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** 真实 HTTP Upgrade 和网络帧测试，不加载业务数据库，不宣称业务 API 已联调。 */
@SpringBootTest(classes = WebSocketIntegrationTest.App.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "bike.jwt.admin.secret-key=" + WsHandshakeInterceptorTest.ADMIN_KEY,
                "bike.jwt.app.secret-key=" + WsHandshakeInterceptorTest.APP_KEY,
                "bike.jwt.admin.access-token-ttl=60000", "bike.jwt.admin.refresh-token-ttl=120000",
                "bike.jwt.app.access-token-ttl=60000", "bike.jwt.app.refresh-token-ttl=120000",
                "bike.jwt.admin.token-name=token", "bike.jwt.app.token-name=token",
                "bike.cors.allowed-origins=http://localhost:5173",
                "spring.ai.openai.api-key=test-only-no-provider",
                "spring.ai.openai.embedding.api-key=test-only-no-provider"
        })
class WebSocketIntegrationTest {
    @Configuration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class,
            RabbitAutoConfiguration.class, RedisAutoConfiguration.class})
    @Import({WebSocketConfig.class, WsHandshakeInterceptor.class, WsTextHandler.class,
            WsSessionManager.class, WebSocketNotifier.class, JwtUtil.class, JacksonConfig.class})
    static class App { }

    @LocalServerPort int port;
    @Autowired JwtUtil jwt;
    @Autowired WebSocketNotifier notifier;
    @Autowired WsSessionManager manager;
    @Autowired ObjectMapper objectMapper;

    @Test
    void realConnectionsReceiveHeartbeatsAndOnlyTheirTargetedEvents() throws Exception {
        List<WebSocket> sockets = new ArrayList<>();
        Inbox admin = new Inbox();
        Inbox first = new Inbox();
        Inbox second = new Inbox();
        HttpClient http = HttpClient.newHttpClient();
        try {
            sockets.add(connect(http, jwt.createAdminAccessToken(11L, "test-admin"), admin));
            sockets.add(connect(http, jwt.createAppAccessToken(21L, "test-app"), first));
            sockets.add(connect(http, jwt.createAppAccessToken(22L, "other-app"), second));
            for (int i = 0; i < sockets.size(); i++) sockets.get(i).sendText("ping", true).join();
            assertEquals("pong", admin.next());
            assertEquals("pong", first.next());
            assertEquals("pong", second.next());

            notifier.pushToAdmin("WORK_ORDER", "RELEASE", Map.of("workOrderId", 8L));
            var event = objectMapper.readTree(admin.next());
            assertEquals("WORK_ORDER", event.get("bizType").asText());
            assertTrue(event.get("timestamp").isTextual());
            notifier.pushToUser(21L, "ANDON", "ACCEPT", Map.of("alertId", 9L));
            assertEquals("ANDON", objectMapper.readTree(first.next()).get("bizType").asText());

            // pong 是前面通知之后同连接的顺序屏障，避免用 sleep 断言没有串用户推送。
            sockets.get(0).sendText("ping", true).join();
            sockets.get(1).sendText("ping", true).join();
            sockets.get(2).sendText("ping", true).join();
            assertEquals("pong", admin.next());
            assertEquals("pong", first.next());
            assertEquals("pong", second.next());

            sockets.forEach(socket -> socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join());
            assertTrue(admin.closed.await(5, TimeUnit.SECONDS));
            assertTrue(first.closed.await(5, TimeUnit.SECONDS));
            assertTrue(second.closed.await(5, TimeUnit.SECONDS));
            // 处理器从注册表移除发生在服务端 close 回调，使用有界等待。
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while ((!manager.getSessions("admin", 11L).isEmpty()
                    || !manager.getSessions("app", 21L).isEmpty()
                    || !manager.getSessions("app", 22L).isEmpty()) && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertTrue(manager.getSessions("admin", 11L).isEmpty());
            assertTrue(manager.getSessions("app", 21L).isEmpty());
            assertTrue(manager.getSessions("app", 22L).isEmpty());
        } finally {
            sockets.forEach(WebSocket::abort);
        }
    }

    @Test
    void realHandshakeRejectsMissingOrRefreshTokensAndUntrustedOrigins() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        for (String token : new String[]{"", "malformed", jwt.createAdminRefreshToken(1L, "test")}) {
            var failure = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> http.newWebSocketBuilder().buildAsync(uri(token), new Inbox()).get(5, TimeUnit.SECONDS));
            assertInstanceOf(WebSocketHandshakeException.class, failure.getCause());
            assertEquals(401, ((WebSocketHandshakeException) failure.getCause()).getResponse().statusCode());
        }
        var failure = assertThrows(java.util.concurrent.ExecutionException.class,
                () -> http.newWebSocketBuilder().header("Origin", "https://untrusted.example")
                        .buildAsync(uri(jwt.createAdminAccessToken(1L, "test")), new Inbox())
                        .get(5, TimeUnit.SECONDS));
        assertEquals(403, ((WebSocketHandshakeException) failure.getCause()).getResponse().statusCode());
    }

    private URI uri(String token) { return URI.create("ws://localhost:" + port + "/ws?token=" + token); }
    private WebSocket connect(HttpClient http, String token, Inbox inbox) throws Exception {
        return http.newWebSocketBuilder().header("Origin", "http://localhost:5173")
                .buildAsync(uri(token), inbox).get(5, TimeUnit.SECONDS);
    }

    static class Inbox implements WebSocket.Listener {
        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final StringBuilder fragments = new StringBuilder();
        final java.util.concurrent.CountDownLatch closed = new java.util.concurrent.CountDownLatch(1);
        @Override public void onOpen(WebSocket socket) { socket.request(1); }
        @Override public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            fragments.append(data);
            if (last) { messages.add(fragments.toString()); fragments.setLength(0); }
            socket.request(1);
            return null;
        }
        @Override public CompletionStage<?> onClose(WebSocket socket, int status, String reason) {
            closed.countDown();
            return null;
        }
        String next() throws InterruptedException {
            String message = messages.poll(5, TimeUnit.SECONDS);
            assertNotNull(message, "等待服务端帧超时");
            return message;
        }
    }
}
