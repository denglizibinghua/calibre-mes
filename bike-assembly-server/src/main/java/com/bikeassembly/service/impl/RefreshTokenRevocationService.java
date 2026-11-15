package com.bikeassembly.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bikeassembly.entity.UserRefreshToken;
import com.bikeassembly.mapper.UserRefreshTokenMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Refresh Token 独立事务撤销服务。
 * <p>
 * 使用 REQUIRES_NEW 确保重放检测后的所有会话撤销不受外层事务回滚影响。
 * 必须在独立 Bean 中定义（不能是同一类内部方法），否则 Spring AOP 不会拦截。
 */
@Service
@Slf4j
public class RefreshTokenRevocationService {

    @Autowired
    private UserRefreshTokenMapper refreshTokenMapper;

    /**
     * 在独立事务中撤销用户所有 Refresh Token。
     * 此方法提交后不可回滚，确保重放攻击日志被持久化。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void revokeAllUserTokensInNewTransaction(Long userId) {
        LambdaUpdateWrapper<UserRefreshToken> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UserRefreshToken::getUserId, userId)
               .eq(UserRefreshToken::getRevoked, 0)
               .set(UserRefreshToken::getRevoked, 1)
               .set(UserRefreshToken::getRevokedAt, LocalDateTime.now());
        int rows = refreshTokenMapper.update(null, wrapper);
        log.warn("Refresh Token 重放/并发冲突：已撤销用户所有会话 userId={}, affectedRows={}", userId, rows);
    }
}
