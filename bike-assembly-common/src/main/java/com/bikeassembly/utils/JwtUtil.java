package com.bikeassembly.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 工具类 — V3.3 支持双通道 (admin/app) + 双 Token 类型 (access/refresh)。
 */
@Component
public class JwtUtil {

    // ==================== Admin 配置 ====================
    @Value("${bike.jwt.admin.secret-key}")
    private String adminSecretKey;
    @Value("${bike.jwt.admin.access-token-ttl}")
    private long adminAccessTtl;
    @Value("${bike.jwt.admin.refresh-token-ttl}")
    private long adminRefreshTtl;
    @Value("${bike.jwt.admin.token-name}")
    private String adminTokenName;

    // ==================== App 配置 ====================
    @Value("${bike.jwt.app.secret-key}")
    private String appSecretKey;
    @Value("${bike.jwt.app.access-token-ttl}")
    private long appAccessTtl;
    @Value("${bike.jwt.app.refresh-token-ttl}")
    private long appRefreshTtl;
    @Value("${bike.jwt.app.token-name}")
    private String appTokenName;

    // ==================== Token 类型常量 ====================
    /** [Token] Access Token：短有效期，用于接口鉴权 */
    public static final String TOKEN_TYPE_ACCESS = "access";
    /** [Token] Refresh Token：长有效期，用于刷新 Access Token */
    public static final String TOKEN_TYPE_REFRESH = "refresh";
    /** [Token] 临时 Token：微信绑定流程使用，5 分钟有效 */
    public static final String TOKEN_TYPE_TEMP = "temp";
    /** @deprecated 旧版 Token 类型，向后兼容，新代码不应使用 */
    public static final String TOKEN_TYPE_FULL = "full";

    /** [Token] 管理端客户端类型，使用 admin 密钥签名，与小程序端密钥隔离 */
    public static final String CLIENT_TYPE_ADMIN = "admin";
    /** [Token] 小程序端客户端类型，使用 app 密钥签名 */
    public static final String CLIENT_TYPE_APP = "app";

    // ==================== 公共 API ====================

    /** [Token] 生成管理端 Access Token：JWT 中包含 userId、username、tokenType=access、clientType=admin */
    public String createAdminAccessToken(Long userId, String username) {
        return createToken(userId, username, CLIENT_TYPE_ADMIN, TOKEN_TYPE_ACCESS,
                adminSecretKey, adminAccessTtl);
    }

    /** [Token] 生成管理端 Refresh Token：JWT 中包含 userId、username、tokenType=refresh、clientType=admin */
    public String createAdminRefreshToken(Long userId, String username) {
        return createToken(userId, username, CLIENT_TYPE_ADMIN, TOKEN_TYPE_REFRESH,
                adminSecretKey, adminRefreshTtl);
    }

    /** [Token] 生成小程序 Access Token：使用 app 密钥签名，与管理端密钥隔离 */
    public String createAppAccessToken(Long userId, String username) {
        return createToken(userId, username, CLIENT_TYPE_APP, TOKEN_TYPE_ACCESS,
                appSecretKey, appAccessTtl);
    }

    /** [Token] 生成小程序 Refresh Token */
    public String createAppRefreshToken(Long userId, String username) {
        return createToken(userId, username, CLIENT_TYPE_APP, TOKEN_TYPE_REFRESH,
                appSecretKey, appRefreshTtl);
    }

    /** [Token] 生成临时 Token（微信绑定流程，5分钟有效，不包含 username） */
    public String createTempToken(Long userId) {
        return createToken(userId, null, CLIENT_TYPE_APP, TOKEN_TYPE_TEMP,
                appSecretKey, 300_000L);
    }

    // ---- 向后兼容方法（旧代码依赖）----

    /** @deprecated 使用 createAdminAccessToken 替代 */
    public String createToken(Long userId) {
        return createAdminAccessToken(userId, null);
    }

    // ==================== Token 解析 ====================

    /** 使用 admin 密钥解析 token */
    public Claims parseTokenWithAdminKey(String token) {
        return parseToken(token, adminSecretKey);
    }

    /** 使用 app 密钥解析 token */
    public Claims parseTokenWithAppKey(String token) {
        return parseToken(token, appSecretKey);
    }

    /** 尝试两种密钥解析 token */
    public Claims parseTokenAny(String token) {
        try {
            return parseTokenWithAdminKey(token);
        } catch (SignatureException | MalformedJwtException e) {
            return parseTokenWithAppKey(token);
        }
    }

    /** @deprecated 使用 parseTokenWithAdminKey 或 parseTokenAny 替代 */
    public Claims parseToken(String token) {
        return parseTokenWithAdminKey(token);
    }

    // ==================== Claims 提取 ====================

    public Long getUserIdFromToken(String token) {
        Claims claims = parseTokenAny(token);
        return claims.get("userId", Long.class);
    }

    public String getTokenType(String token) {
        Claims claims = parseTokenAny(token);
        String type = claims.get("tokenType", String.class);
        return type != null ? type : TOKEN_TYPE_FULL;
    }

    public String getClientType(String token) {
        Claims claims = parseTokenAny(token);
        return claims.get("clientType", String.class);
    }

    public String getUsername(String token) {
        Claims claims = parseTokenAny(token);
        return claims.get("username", String.class);
    }

    public String getTokenJti(String token) {
        Claims claims = parseTokenAny(token);
        return claims.getId();
    }

    // ---- 向后兼容 ----
    public String getAdminTokenName() { return adminTokenName; }
    public String getAppTokenName() { return appTokenName; }
    public long getAdminTtl() { return adminAccessTtl; }
    public long getAdminAccessTtl() { return adminAccessTtl; }
    public long getAdminRefreshTtl() { return adminRefreshTtl; }
    public long getAppAccessTtl() { return appAccessTtl; }
    public long getAppRefreshTtl() { return appRefreshTtl; }

    // ==================== 内部实现 ====================

    /**
     * [Token] JWT 内部构造方法。
     * 每个 Token 包含以下 Claims：
     *   - jti：UUID 随机生成的唯一标识，用于 Refresh Token 的撤销和重放检测
     *   - userId：用户 ID
     *   - tokenType：access / refresh / temp
     *   - clientType：admin / app
     *   - username：用户名（temp token 不包含）
     *   - iat：签发时间
     *   - exp：过期时间 = now + ttl
     * 签名算法：HS256（HMAC-SHA256），密钥从配置文件 bike.jwt.{admin|app}.secret-key 读取
     */
    private String createToken(Long userId, String username, String clientType,
                                String tokenType, String secretKey, long ttl) {
        SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();

        var builder = Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .claim("userId", userId)
                .claim("tokenType", tokenType)
                .claim("clientType", clientType)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + ttl));

        if (username != null) {
            builder.claim("username", username);
        }

        return builder.signWith(key, SignatureAlgorithm.HS256).compact();
    }

    private Claims parseToken(String token, String secretKey) {
        SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
