package com.bikeassembly.interceptor;

import com.bikeassembly.annotation.IdempotencyRequired;
import com.bikeassembly.context.BaseContext;
import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.utils.IdempotencyUtils;
import com.bikeassembly.utils.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 幂等键拦截器：从请求头 Idempotency-Key 提取幂等键。
 *
 * [工作流程]
 *   1. 读取请求头 Idempotency-Key
 *   2. 如果方法上有 @IdempotencyRequired 注解 → 强制校验（缺失返回 400）
 *   3. 如果无注解 → 可选提取存入 BaseContext
 *   4. afterCompletion 中清理 ThreadLocal
 *
 * [安全] 校验幂等键合法性：非空、去空格、长度限制、控制字符检查
 */
@Component
@Slf4j
public class IdempotencyInterceptor implements HandlerInterceptor {

    private static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {

        // 跳过非 Controller 方法的请求（如静态资源）
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        // 跳过 OPTIONS 预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        String rawKey = request.getHeader(HEADER_IDEMPOTENCY_KEY);

        // 检查方法是否有 @IdempotencyRequired 注解
        boolean required = handlerMethod.hasMethodAnnotation(IdempotencyRequired.class);

        if (required) {
            // 强制校验
            try {
                String validatedKey = IdempotencyUtils.validateIdempotencyKey(rawKey);
                BaseContext.setIdempotencyKey(validatedKey);
                log.debug("幂等键校验通过：URI={}, key={}",
                        request.getRequestURI(), truncateKey(validatedKey));
            } catch (com.bikeassembly.exception.IdempotentKeyMissingException e) {
                log.warn("缺少幂等键：URI={}, method={}", request.getRequestURI(), request.getMethod());
                ErrorResponseWriter.write(response, HttpStatus.BAD_REQUEST, e.getMessage(), null, request);
                return false;
            }
        } else if (rawKey != null) {
            // 可选模式：如果提供了就去空格存放
            String trimmed = rawKey.trim();
            if (!trimmed.isEmpty()) {
                BaseContext.setIdempotencyKey(trimmed);
            }
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                 Object handler, Exception ex) {
        // ThreadLocal 由 JwtTokenInterceptor.afterCompletion 统一清理
        // 这里不单独清理，因为 JWT 拦截器的 removeAll() 已包含 removeIdempotencyKey()
    }

    private String truncateKey(String key) {
        if (key == null) return "null";
        if (key.length() <= 8) return key;
        return key.substring(0, 8) + "...";
    }
}
