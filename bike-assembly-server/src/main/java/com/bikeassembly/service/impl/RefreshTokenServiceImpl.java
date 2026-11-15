package com.bikeassembly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bikeassembly.entity.SysUser;
import com.bikeassembly.entity.UserRefreshToken;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.mapper.SysUserMapper;
import com.bikeassembly.mapper.UserRefreshTokenMapper;
import com.bikeassembly.service.RefreshTokenService;
import com.bikeassembly.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;

@Service
@Slf4j
public class RefreshTokenServiceImpl
        extends ServiceImpl<UserRefreshTokenMapper, UserRefreshToken>
        implements RefreshTokenService {

    @Autowired
    private UserRefreshTokenMapper refreshTokenMapper;
    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private RefreshTokenRevocationService revocationService;
    @Autowired
    private JwtUtil jwtUtil;

    /**
     * [Token] 保存 Refresh Token 记录。
     * [数据库操作] 向 user_refresh_token 表插入一条记录
     * [核心业务] 数据库中只存储 tokenHash（SHA-256 哈希），不存明文
     *   —— 即使数据库泄露，攻击者也无法直接使用 Refresh Token
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserRefreshToken saveRefreshToken(Long userId, String clientType,
                                              String tokenJti, String rawToken,
                                              long ttlMillis, String deviceInfo, String ipAddress) {
        UserRefreshToken entity = UserRefreshToken.builder()
                .userId(userId)
                .clientType(clientType)
                .tokenJti(tokenJti)
                .tokenHash(sha256(rawToken))
                .expiresAt(LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(System.currentTimeMillis() + ttlMillis),
                        ZoneId.systemDefault()))
                .revoked(0)
                .deviceInfo(deviceInfo)
                .ipAddress(ipAddress)
                .createTime(LocalDateTime.now())
                .build();
        save(entity);
        log.debug("Refresh Token 已保存：userId={}, clientType={}, jti={}", userId, clientType, tokenJti);
        return entity;
    }

    /**
     * [答辩重点] Refresh Token 轮换（Token Rotation）—— 双 Token 机制的核心。
     * [核心业务] 每次刷新 Access Token 时，同时撤销旧的 Refresh Token 并签发新的 Refresh Token。
     *   这样即使 Refresh Token 被窃取，一旦被使用就会触发重放检测。
     * [调用链] UserController.refreshToken → SysUserService.refreshToken → RefreshTokenService.rotateRefreshToken
     * [业务规则] 刷新判断顺序：
     *   1. 解析 JWT 签名并校验 tokenType=refresh 和 clientType 匹配
     *   2. 查 user_refresh_token 表确认 jti 存在且未撤销（revoked=0）
     *   3. 校验 SHA-256 哈希防止数据库被篡改
     *   4. 检查用户状态（存在 + 未被禁用）
     *   5. 签发新 Access Token + 新 Refresh Token
     *   6. 撤销旧 Token（设置 revoked=1, revokedAt, replacedByJti）
     *   7. 保存新 Refresh Token 到数据库
     * [消息幂等] 如果 jti 在数据库中不存在（已被使用），说明这是重放攻击，撤销该用户所有会话
     * [事务] 整个轮换过程在一个事务中执行
     * [数据库操作] 查询 user_refresh_token（按 jti），查询 sys_user（按 userId），
     *   更新 user_refresh_token（撤销旧），插入 user_refresh_token（保存新）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefreshResult rotateRefreshToken(String rawRefreshToken, String expectedClientType) {
        // 1. 解析 token（签名+过期校验在 parseClaimsJws 中自动完成）
        Claims claims;
        try {
            claims = parseRefreshTokenClaims(rawRefreshToken, expectedClientType);
        } catch (ExpiredJwtException e) {
            throw new BaseException("Refresh Token 已过期，请重新登录");
        } catch (SignatureException | MalformedJwtException e) {
            throw new BaseException("Refresh Token 无效");
        }

        String tokenType = claims.get("tokenType", String.class);
        String clientType = claims.get("clientType", String.class);
        String jti = claims.getId();
        Long userId = claims.get("userId", Long.class);

        if (!JwtUtil.TOKEN_TYPE_REFRESH.equals(tokenType)) {
            throw new BaseException("非法的 Token 类型，仅支持 Refresh Token");
        }
        if (!expectedClientType.equals(clientType)) {
            throw new BaseException("客户端类型不匹配");
        }

        // [消息幂等] 2. 查询 DB 记录 —— 如果 jti 不存在，说明此 Token 已被轮换过，判定为重放攻击
        UserRefreshToken stored = getOne(new LambdaQueryWrapper<UserRefreshToken>()
                .eq(UserRefreshToken::getTokenJti, jti));

        if (stored == null) {
            // [业务规则] Token 的 jti 在数据库中找不到 → 已被使用过（上一轮刷新时被撤销）
            // → 判定为重放攻击 → 撤销该用户所有会话，强制重新登录
            // 先独立事务撤销所有会话（不可回滚），再抛异常
            revocationService.revokeAllUserTokensInNewTransaction(userId);
            log.warn("Refresh Token 重放攻击检测：jti={}, userId={}", jti, userId);
            throw new BaseException("Refresh Token 已被使用，请重新登录");
        }

        if (stored.getRevoked() != null && stored.getRevoked() == 1) {
            revocationService.revokeAllUserTokensInNewTransaction(userId);
            log.warn("已撤销的 Refresh Token 被使用：jti={}, userId={}", jti, userId);
            throw new BaseException("Refresh Token 已被撤销，请重新登录");
        }

        // 3. 校验 token 哈希
        String tokenHash = sha256(rawRefreshToken);
        if (!tokenHash.equals(stored.getTokenHash())) {
            throw new BaseException("Refresh Token 校验失败");
        }

        // 4. 校验用户状态
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BaseException("用户不存在");
        }
        if ("1".equals(user.getStatus())) {
            revocationService.revokeAllUserTokensInNewTransaction(userId);
            throw new BaseException("账号已被禁用，请重新登录");
        }

        // 5. 签发新 Token
        String username = user.getUsername();
        String newAccessToken;
        String newRefreshToken;
        long accessTtl;
        long refreshTtl;

        if (JwtUtil.CLIENT_TYPE_ADMIN.equals(clientType)) {
            newAccessToken = jwtUtil.createAdminAccessToken(userId, username);
            newRefreshToken = jwtUtil.createAdminRefreshToken(userId, username);
            accessTtl = jwtUtil.getAdminAccessTtl();
            refreshTtl = jwtUtil.getAdminRefreshTtl();
        } else {
            newAccessToken = jwtUtil.createAppAccessToken(userId, username);
            newRefreshToken = jwtUtil.createAppRefreshToken(userId, username);
            accessTtl = jwtUtil.getAppAccessTtl();
            refreshTtl = jwtUtil.getAppRefreshTtl();
        }

        String newJti = jwtUtil.getTokenJti(newRefreshToken);

        // 6. 撤销旧 token，记录替换关系（并发安全：条件更新，仅 revoked=0 时成功）
        LambdaUpdateWrapper<UserRefreshToken> revokeWrapper = new LambdaUpdateWrapper<>();
        revokeWrapper.eq(UserRefreshToken::getId, stored.getId())
                     .eq(UserRefreshToken::getRevoked, 0)
                     .set(UserRefreshToken::getRevoked, 1)
                     .set(UserRefreshToken::getRevokedAt, LocalDateTime.now())
                     .set(UserRefreshToken::getReplacedByJti, newJti);
        int revokeRows = refreshTokenMapper.update(null, revokeWrapper);
        if (revokeRows == 0) {
            // 并发刷新：先独立事务撤销所有会话（不可回滚），再抛异常
            revocationService.revokeAllUserTokensInNewTransaction(userId);
            log.warn("Refresh Token 并发刷新冲突：jti={}, userId={}", jti, userId);
            throw new BaseException("Refresh Token 已被使用，请重新登录");
        }

        // 7. 保存新 token
        saveRefreshToken(userId, clientType, newJti, newRefreshToken, refreshTtl,
                stored.getDeviceInfo(), stored.getIpAddress());

        log.info("Refresh Token 旋转成功：userId={}, oldJti={}, newJti={}", userId, jti, newJti);

        return new RefreshResult(newAccessToken, newRefreshToken, accessTtl / 1000);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeRefreshToken(String tokenJti) {
        // 并发安全：条件更新 WHERE token_jti=? AND revoked=0
        LambdaUpdateWrapper<UserRefreshToken> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UserRefreshToken::getTokenJti, tokenJti)
               .eq(UserRefreshToken::getRevoked, 0)
               .set(UserRefreshToken::getRevoked, 1)
               .set(UserRefreshToken::getRevokedAt, LocalDateTime.now());
        int rows = refreshTokenMapper.update(null, wrapper);
        if (rows > 0) {
            log.info("Refresh Token 已撤销：jti={}", tokenJti);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeAllUserTokens(Long userId, String clientType) {
        LambdaUpdateWrapper<UserRefreshToken> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UserRefreshToken::getUserId, userId)
               .eq(UserRefreshToken::getClientType, clientType)
               .eq(UserRefreshToken::getRevoked, 0)
               .set(UserRefreshToken::getRevoked, 1)
               .set(UserRefreshToken::getRevokedAt, LocalDateTime.now());
        update(wrapper);
        log.info("用户 Refresh Token 已批量撤销：userId={}, clientType={}", userId, clientType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeAllUserTokens(Long userId) {
        LambdaUpdateWrapper<UserRefreshToken> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UserRefreshToken::getUserId, userId)
               .eq(UserRefreshToken::getRevoked, 0)
               .set(UserRefreshToken::getRevoked, 1)
               .set(UserRefreshToken::getRevokedAt, LocalDateTime.now());
        update(wrapper);
        log.info("用户全部 Refresh Token 已撤销：userId={}", userId);
    }

    @Override
    public void detectReplayAttack(String tokenJti, Long userId) {
        List<UserRefreshToken> tokens = list(new LambdaQueryWrapper<UserRefreshToken>()
                .eq(UserRefreshToken::getTokenJti, tokenJti));
        if (!tokens.isEmpty()) {
            UserRefreshToken t = tokens.get(0);
            if (t.getRevoked() != null && t.getRevoked() == 1 && t.getReplacedByJti() != null) {
                log.warn("Refresh Token 重放攻击：jti={} 已被替换为 {}", tokenJti, t.getReplacedByJti());
                revokeAllUserTokens(userId);
                throw new BaseException("检测到异常会话，请重新登录");
            }
        }
    }

    // ==================== 私有方法 ====================

    private Claims parseRefreshTokenClaims(String rawToken, String expectedClientType) {
        if (JwtUtil.CLIENT_TYPE_ADMIN.equals(expectedClientType)) {
            return jwtUtil.parseTokenWithAdminKey(rawToken);
        } else {
            return jwtUtil.parseTokenWithAppKey(rawToken);
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
