package com.bikeassembly.service.impl;

import com.bikeassembly.utils.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 登录失败计数与账号锁定（Redis 实现）。
 * <p>
 * 使用 Redis INCR + EXPIRE 原子计数，达到阈值后写入锁定标记；
 * 完全迁移自原 DB 字段方案（sys_user.login_fail_count/locked_until 保留列但不再作为判断依据）。
 * Redis 不可用时降级为不计数不锁定（记录告警日志），不影响登录主流程。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LoginFailService {

    private final RedisService redisService;

    private static final int MAX_FAIL_COUNT = 5;
    private static final int LOCK_MINUTES = 15;
    private static final String FAIL_KEY_PREFIX = "login:fail:";
    private static final String LOCK_KEY_PREFIX = "login:lock:";

    /**
     * 增加一次登录失败计数；达到阈值时锁定账号。
     */
    public void incrementFailCount(String username) {
        String failKey = FAIL_KEY_PREFIX + username;
        long count = redisService.increment(failKey);
        if (count == 1) {
            // 首次失败才开始计时，15 分钟后计数自动过期归零
            redisService.expire(failKey, Duration.ofMinutes(LOCK_MINUTES));
        }
        if (count >= MAX_FAIL_COUNT) {
            redisService.set(LOCK_KEY_PREFIX + username, "1", Duration.ofMinutes(LOCK_MINUTES));
            log.warn("账号锁定：username={}, failCount={}", username, count);
        }
    }

    /**
     * 账号当前是否处于锁定状态。
     */
    public boolean isLocked(String username) {
        return redisService.hasKey(LOCK_KEY_PREFIX + username);
    }

    /**
     * 登录成功后清除失败计数与锁定标记。
     */
    public void resetFail(String username) {
        redisService.delete(FAIL_KEY_PREFIX + username);
        redisService.delete(LOCK_KEY_PREFIX + username);
    }
}
