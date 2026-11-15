package com.bikeassembly.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bikeassembly.entity.UserRefreshToken;

/**
 * Refresh Token 管理服务。
 */
public interface RefreshTokenService extends IService<UserRefreshToken> {

    /**
     * 保存 Refresh Token 记录（存储 SHA-256 哈希）。
     */
    UserRefreshToken saveRefreshToken(Long userId, String clientType,
                                       String tokenJti, String rawToken,
                                       long ttlMillis, String deviceInfo, String ipAddress);

    /**
     * 使用 Refresh Token（Token Rotation）：
     * 1. 校验 token 签名
     * 2. 校验 token 类型和客户端类型
     * 3. 查询 DB 记录是否存在且未撤销
     * 4. 撤销旧 token，记录 replaced_by_jti
     * 5. 签发新的 access + refresh token
     *
     * @return {accessToken, refreshToken, expiresIn} 或 null（表示刷新失败）
     */
    RefreshResult rotateRefreshToken(String rawRefreshToken, String expectedClientType);

    /**
     * 撤销指定的 Refresh Token（退出登录时调用）。
     */
    void revokeRefreshToken(String tokenJti);

    /**
     * 撤销用户某客户端类型的所有 Refresh Token。
     */
    void revokeAllUserTokens(Long userId, String clientType);

    /**
     * 撤销用户所有 Refresh Token（密码修改、账号禁用时调用）。
     */
    void revokeAllUserTokens(Long userId);

    /**
     * 检测重放攻击：如果 token_jti 已被使用过，撤销该用户所有会话。
     */
    void detectReplayAttack(String tokenJti, Long userId);

    /** 刷新结果 */
    record RefreshResult(String accessToken, String refreshToken, long expiresIn) {}
}
