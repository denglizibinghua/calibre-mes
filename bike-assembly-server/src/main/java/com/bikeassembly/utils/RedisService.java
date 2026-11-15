package com.bikeassembly.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 通用访问封装。
 * <p>
 * 所有操作均做异常降级：Redis 不可用时记录告警日志并返回安全默认值，不向上抛异常影响主业务。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RedisService {

    private static final long AVAILABILITY_CACHE_NANOS = TimeUnit.SECONDS.toNanos(1);

    private final RedisTemplate<String, Object> redisTemplate;
    private volatile long availabilityCheckedAtNanos;
    private volatile boolean available;

    /** Check whether Redis can currently serve requests. */
    public boolean isAvailable() {
        long now = System.nanoTime();
        if (availabilityCheckedAtNanos != 0
                && now - availabilityCheckedAtNanos < AVAILABILITY_CACHE_NANOS) {
            return available;
        }
        synchronized (this) {
            now = System.nanoTime();
            if (availabilityCheckedAtNanos != 0
                    && now - availabilityCheckedAtNanos < AVAILABILITY_CACHE_NANOS) {
                return available;
            }
            try {
                if (redisTemplate.getConnectionFactory() == null) {
                    available = false;
                } else {
                    try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
                        available = "PONG".equalsIgnoreCase(connection.ping());
                    }
                }
            } catch (Exception e) {
                available = false;
                log.debug("Redis health check failed: {}", e.getMessage());
            }
            availabilityCheckedAtNanos = System.nanoTime();
            return available;
        }
    }

    /** 写入值（无 TTL） */
    public void set(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value);
        } catch (Exception e) {
            log.warn("Redis set 失败（降级）：key={}, error={}", key, e.getMessage());
        }
    }

    /** 写入值并设置过期时间 */
    public void set(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.warn("Redis set(ttl) 失败（降级）：key={}, error={}", key, e.getMessage());
        }
    }

    /** 读取值；不存在或类型不匹配返回 null */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> clazz) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return null;
            }
            if (clazz.isInstance(value)) {
                return (T) value;
            }
            return null;
        } catch (Exception e) {
            log.warn("Redis get 失败（降级）：key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    /** 读取原始值（调用方自行转型） */
    public Object getRaw(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis getRaw 失败（降级）：key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    /** 删除键 */
    public void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis delete 失败（降级）：key={}, error={}", key, e.getMessage());
        }
    }

    /** 原子自增并返回自增后的值；失败返回 -1 */
    public long increment(String key) {
        try {
            Long value = redisTemplate.opsForValue().increment(key);
            return value == null ? -1 : value;
        } catch (Exception e) {
            log.warn("Redis increment 失败（降级）：key={}, error={}", key, e.getMessage());
            return -1;
        }
    }

    /** 设置过期时间 */
    public void expire(String key, Duration ttl) {
        try {
            redisTemplate.expire(key, ttl);
        } catch (Exception e) {
            log.warn("Redis expire 失败（降级）：key={}, error={}", key, e.getMessage());
        }
    }

    /** 仅当键不存在时写入（原子），用于分布式锁/幂等标记；失败返回 false */
    public boolean setIfAbsent(String key, Object value, Duration ttl) {
        try {
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, value, ttl);
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            log.warn("Redis setIfAbsent 失败（降级）：key={}, error={}", key, e.getMessage());
            return false;
        }
    }

    /** 判断键是否存在 */
    public boolean hasKey(String key) {
        try {
            Boolean exists = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.warn("Redis hasKey 失败（降级）：key={}, error={}", key, e.getMessage());
            return false;
        }
    }

    /** 按前缀批量删除键（keys 模式，数据量小场景使用） */
    public void deleteByPrefix(String prefix) {
        try {
            Set<String> keys = redisTemplate.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("Redis deleteByPrefix 失败（降级）：prefix={}, error={}", prefix, e.getMessage());
        }
    }
}
