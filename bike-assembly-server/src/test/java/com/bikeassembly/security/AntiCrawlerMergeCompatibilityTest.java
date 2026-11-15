package com.bikeassembly.security;

import com.bikeassembly.config.AntiCrawlerConfiguration;
import com.bikeassembly.config.AntiCrawlerProperties;
import com.bikeassembly.interceptor.AntiCrawlerInterceptor;
import com.bikeassembly.utils.RedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** main合并后的限流编译契约、降级和Spring IoC回归。 */
class AntiCrawlerMergeCompatibilityTest {
    RedisService redis;
    AntiCrawlerProperties properties;
    AntiCrawlerInterceptor interceptor;

    @BeforeEach void setup() {
        redis = mock(RedisService.class);
        properties = new AntiCrawlerProperties();
        interceptor = new AntiCrawlerInterceptor(redis, properties);
    }
    @Test void firstProtectedRequestSetsWindowAndPasses() throws Exception {
        when(redis.increment(anyString())).thenReturn(1L);
        var response = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(new MockHttpServletRequest("POST", "/app/user/login"), response, new Object()));
        verify(redis).expire(anyString(), eq(Duration.ofSeconds(60)));
        assertEquals(200, response.getStatus());
    }
    @Test void exceededLoginLimitReturns429WithRetryAfter() throws Exception {
        when(redis.increment(anyString())).thenReturn(11L);
        var response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(new MockHttpServletRequest("POST", "/app/user/login"), response, new Object()));
        assertEquals(429, response.getStatus());
        assertEquals("60", response.getHeader("Retry-After"));
        assertTrue(response.getContentAsString().contains("请求过于频繁"));
    }
    @Test void redisFailureUsesExistingMinusOneContractAndFailsOpen() throws Exception {
        when(redis.increment(anyString())).thenReturn(-1L);
        var response = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(new MockHttpServletRequest("POST", "/app/user/login"), response, new Object()));
        assertEquals(200, response.getStatus());
        verify(redis, never()).expire(anyString(), any());
    }
    @Test void disabledLimiterAndUnprotectedScanDoNotAccessRedis() throws Exception {
        properties.setEnabled(false);
        assertTrue(interceptor.preHandle(new MockHttpServletRequest("POST", "/app/user/login"), new MockHttpServletResponse(), new Object()));
        properties.setEnabled(true);
        assertTrue(interceptor.preHandle(new MockHttpServletRequest("POST", "/app/scan/resolve"), new MockHttpServletResponse(), new Object()));
        verifyNoInteractions(redis);
    }
    @Test void aiAndExportKeepTheirSeparateLimits() throws Exception {
        when(redis.increment(anyString())).thenReturn(31L);
        assertFalse(interceptor.preHandle(new MockHttpServletRequest("POST", "/admin/ai/analyze"), new MockHttpServletResponse(), new Object()));
        when(redis.increment(anyString())).thenReturn(121L);
        assertFalse(interceptor.preHandle(new MockHttpServletRequest("GET", "/admin/export/orders"), new MockHttpServletResponse(), new Object()));
    }
    @Test void interceptorIsManagedBySpringAndConfigurationUsesIt() {
        new ApplicationContextRunner()
                .withUserConfiguration(AntiCrawlerConfiguration.class, AntiCrawlerInterceptor.class)
                .withBean(RedisService.class, () -> redis)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertNotNull(context.getBean(AntiCrawlerInterceptor.class));
                    assertNotNull(context.getBean(AntiCrawlerConfiguration.class));
                    assertEquals(1, context.getBeansOfType(AntiCrawlerInterceptor.class).size());
                });
    }
}
