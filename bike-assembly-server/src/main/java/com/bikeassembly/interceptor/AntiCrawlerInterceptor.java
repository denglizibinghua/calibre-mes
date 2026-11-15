package com.bikeassembly.interceptor;

import com.bikeassembly.config.AntiCrawlerProperties;
import com.bikeassembly.utils.RedisService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 轻量反爬/接口限流：按 IP + 路径固定窗口计数。
 * 登录和 AI 接口采用更严格阈值；Redis 故障时放行并记录告警。
 */
@Slf4j
@Component
public class AntiCrawlerInterceptor implements HandlerInterceptor {
    private final RedisService redisService;
    private final AntiCrawlerProperties properties;

    public AntiCrawlerInterceptor(RedisService redisService, AntiCrawlerProperties properties) {
        this.redisService = redisService;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!properties.isEnabled() || !isProtected(request.getRequestURI())) {
            return true;
        }
        String path = request.getRequestURI();
        String ip = clientIp(request);
        int limit = limitFor(path);
        String key = "anti-crawler:" + safe(ip) + ":" + safe(path);
        long count = redisService.increment(key);
        // RedisService约定自增失败返回-1，按现有降级契约放行。
        if (count < 0) {
            log.warn("反爬限流暂时降级：Redis 不可用，path={}", path);
            return true;
        }
        if (count == 1) {
            redisService.expire(key, Duration.ofSeconds(properties.getWindowSeconds()));
        }
        if (count > limit) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(properties.getWindowSeconds()));
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":429,\"msg\":\"请求过于频繁，请稍后再试\"}");
            return false;
        }
        return true;
    }

    private boolean isProtected(String path) {
        List<String> prefixes = properties.getProtectedPrefixes();
        return prefixes != null && prefixes.stream().anyMatch(path::startsWith);
    }

    private int limitFor(String path) {
        if (path.contains("/login")) return properties.getLoginMaxRequests();
        if (path.startsWith("/admin/ai")) return properties.getAiMaxRequests();
        return properties.getMaxRequests();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String safe(String value) {
        return value == null ? "unknown" : value.replaceAll("[^a-zA-Z0-9:._/-]", "_");
    }
}
