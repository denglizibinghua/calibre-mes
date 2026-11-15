package com.bikeassembly.ws;

import com.bikeassembly.utils.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class WsHandshakeInterceptorTest {
    static final String ADMIN_KEY = "ws-test-admin-key-at-least-32-bytes-long";
    static final String APP_KEY = "ws-test-app-key-at-least-32-bytes-long";
    private WsHandshakeInterceptor interceptor;

    @BeforeEach
    void setup() {
        JwtUtil jwt = new JwtUtil();
        ReflectionTestUtils.setField(jwt, "adminSecretKey", ADMIN_KEY);
        ReflectionTestUtils.setField(jwt, "appSecretKey", APP_KEY);
        interceptor = new WsHandshakeInterceptor(jwt);
    }

    static String token(String key, String clientType, String type, Long userId, long ttl) {
        return Jwts.builder().claim("userId", userId).claim("clientType", clientType)
                .claim("tokenType", type).setExpiration(new Date(System.currentTimeMillis() + ttl))
                .signWith(Keys.hmacShaKeyFor(key.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    void acceptsBothSignedClientTypesAndIgnoresQueryIdentity() {
        for (String client : new String[]{"admin", "app"}) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setParameter("token", token(client.equals("admin") ? ADMIN_KEY : APP_KEY,
                    client, "access", 42L, 60_000));
            request.setParameter("userId", "999");
            request.setParameter("clientType", "admin");
            Map<String, Object> attributes = new HashMap<>();
            assertTrue(interceptor.beforeHandshake(new ServletServerHttpRequest(request),
                    new ServletServerHttpResponse(new MockHttpServletResponse()), mock(WsTextHandler.class), attributes));
            assertEquals(42L, attributes.get("userId"));
            assertEquals(client, attributes.get("clientType"));
        }
    }

    @Test
    void rejectsMissingExpiredMalformedRefreshAndMissingIdentityWith401() {
        String[] rejected = {null, " ", "malformed", token(ADMIN_KEY, "admin", "access", 1L, -60_000),
                token(ADMIN_KEY, "admin", "refresh", 1L, 60_000),
                token(ADMIN_KEY, "unknown", "access", 1L, 60_000),
                token(ADMIN_KEY, "admin", "access", null, 60_000),
                token(ADMIN_KEY, "admin", "access", 0L, 60_000),
                token("wrong-signature-key-at-least-32-bytes", "admin", "access", 1L, 60_000)};
        for (String value : rejected) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            if (value != null) request.setParameter("token", value);
            MockHttpServletResponse response = new MockHttpServletResponse();
            Map<String, Object> attributes = new HashMap<>();
            assertFalse(interceptor.beforeHandshake(new ServletServerHttpRequest(request),
                    new ServletServerHttpResponse(response), mock(WsTextHandler.class), attributes));
            assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
            assertTrue(attributes.isEmpty());
        }
    }

    @Test
    void rejectsClaimsSignedWithTheOtherChannelsKey() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("token", token(APP_KEY, "admin", "access", 1L, 60_000));
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.beforeHandshake(new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(response), mock(WsTextHandler.class), new HashMap<>()));
        assertEquals(401, response.getStatus());
    }
}
