package com.bikeassembly.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 幂等工具类：请求摘要计算 + 幂等键校验。
 * [安全] 使用 SHA-256 计算请求参数的稳定哈希。
 * [规范] 使用 Jackson 将对象序列化为规范 JSON（字段排序、时间格式统一）再计算哈希。
 */
@Slf4j
public final class IdempotencyUtils {

    /** 幂等键最大长度 */
    public static final int MAX_KEY_LENGTH = 128;

    /** 规范化 JSON 的 ObjectMapper：按字段名排序、禁用时间戳格式 */
    private static final ObjectMapper CANONICAL_MAPPER = new ObjectMapper();

    static {
        CANONICAL_MAPPER.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        CANONICAL_MAPPER.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        CANONICAL_MAPPER.registerModule(new JavaTimeModule());
    }

    private IdempotencyUtils() {
    }

    /**
     * 校验幂等键合法性。
     * @return 去除首尾空格后的合法幂等键
     * @throws com.bikeassembly.exception.IdempotentKeyMissingException 键为空或仅空格
     */
    public static String validateIdempotencyKey(String rawKey) {
        if (rawKey == null) {
            throw new com.bikeassembly.exception.IdempotentKeyMissingException(
                    com.bikeassembly.constant.MessageConstant.IDEMPOTENCY_KEY_MISSING);
        }
        String key = rawKey.trim();
        if (key.isEmpty()) {
            throw new com.bikeassembly.exception.IdempotentKeyMissingException(
                    com.bikeassembly.constant.MessageConstant.IDEMPOTENCY_KEY_EMPTY);
        }
        if (key.length() > MAX_KEY_LENGTH) {
            throw new com.bikeassembly.exception.IdempotentKeyMissingException(
                    com.bikeassembly.constant.MessageConstant.IDEMPOTENCY_KEY_TOO_LONG);
        }
        // 不能包含不可见控制字符
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (Character.isISOControl(c) && !Character.isWhitespace(c)) {
                throw new com.bikeassembly.exception.IdempotentKeyMissingException(
                        com.bikeassembly.constant.MessageConstant.IDEMPOTENCY_KEY_INVALID_CHARS);
            }
        }
        return key;
    }

    /**
     * 计算请求对象的 SHA-256 摘要。
     * 先将对象序列化为规范 JSON，再计算哈希。
     *
     * @param request 请求对象（DTO、Map 或任何可序列化的对象）
     * @return 64 位十六进制 SHA-256 哈希字符串
     */
    public static String computeHash(Object request) {
        try {
            String canonicalJson = CANONICAL_MAPPER.writeValueAsString(request);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(64);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (JsonProcessingException e) {
            log.error("请求序列化失败，无法计算请求摘要", e);
            throw new RuntimeException("请求序列化失败", e);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 算法不可用", e);
            throw new RuntimeException("SHA-256 算法不可用", e);
        }
    }

    /**
     * 计算请求参数的 SHA-256 摘要（便捷方法，内部使用 Map）。
     * 适用于参数以 RequestParam 方式传入的场景。
     */
    public static String computeHashFromMap(java.util.Map<String, Object> params) {
        // TreeMap 保证 key 排序一致
        java.util.TreeMap<String, Object> sorted = new java.util.TreeMap<>(params);
        return computeHash(sorted);
    }
}
