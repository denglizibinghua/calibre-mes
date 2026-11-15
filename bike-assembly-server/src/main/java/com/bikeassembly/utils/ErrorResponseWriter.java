package com.bikeassembly.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 统一错误响应序列化组件（AUD-210）。
 * <p>
 * 拦截器无法被 @RestControllerAdvice 捕获，统一通过本组件输出与 Result/GlobalExceptionHandler
 * 一致的结构：code / msg / data / path / timestamp / requestId。
 */
public final class ErrorResponseWriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private ErrorResponseWriter() {
    }

    /**
     * 输出统一错误响应。
     *
     * @param response HTTP 响应
     * @param status   HTTP 状态码
     * @param message  错误提示（前端优先展示）
     * @param data     结构化错误数据（如 SECURITY_PASSWORD_CHANGE_REQUIRED），无则传 null
     * @param request  当前请求（用于填充 path），无则传 null
     */
    public static void write(HttpServletResponse response, HttpStatus status, String message,
                             Map<String, Object> data, HttpServletRequest request) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", status.value());
        body.put("msg", message);
        if (data != null) {
            body.put("data", data);
        }
        if (request != null) {
            body.put("path", request.getRequestURI());
        }
        body.put("timestamp", LocalDateTime.now());
        body.put("requestId", UUID.randomUUID().toString().substring(0, 8));

        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(body));
    }
}
