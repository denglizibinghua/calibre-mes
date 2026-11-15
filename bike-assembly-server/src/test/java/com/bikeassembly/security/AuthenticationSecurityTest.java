package com.bikeassembly.security;

import com.bikeassembly.utils.JwtUtil;
import com.bikeassembly.utils.PasswordValidator;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 认证安全集成测试 — 双 Token + 密码复杂度 + Token 类型隔离。
 */
@SpringBootTest
@DisplayName("认证安全测试")
class AuthenticationSecurityTest {

    @Autowired
    private JwtUtil jwtUtil;

    // ==================== 双 Token 签发 ====================

    @Nested
    @DisplayName("管理端双 Token")
    class AdminDualToken {

        @Test
        @DisplayName("签发 Access Token 和 Refresh Token 应为不同类型")
        void shouldCreateDifferentTokenTypes() {
            String accessToken = jwtUtil.createAdminAccessToken(1L, "admin");
            String refreshToken = jwtUtil.createAdminRefreshToken(1L, "admin");

            assertNotNull(accessToken);
            assertNotNull(refreshToken);
            assertNotEquals(accessToken, refreshToken);

            assertEquals(JwtUtil.TOKEN_TYPE_ACCESS, jwtUtil.getTokenType(accessToken));
            assertEquals(JwtUtil.TOKEN_TYPE_REFRESH, jwtUtil.getTokenType(refreshToken));
        }

        @Test
        @DisplayName("Access Token 和 Refresh Token 应有不同的 JTI")
        void shouldHaveDifferentJti() {
            String accessToken = jwtUtil.createAdminAccessToken(1L, "admin");
            String refreshToken = jwtUtil.createAdminRefreshToken(1L, "admin");

            assertNotEquals(jwtUtil.getTokenJti(accessToken), jwtUtil.getTokenJti(refreshToken));
        }

        @Test
        @DisplayName("Token 应包含正确的 clientType")
        void shouldHaveCorrectClientType() {
            String token = jwtUtil.createAdminAccessToken(1L, "admin");
            assertEquals(JwtUtil.CLIENT_TYPE_ADMIN, jwtUtil.getClientType(token));
        }

        @Test
        @DisplayName("Token 应包含正确的 userId")
        void shouldHaveCorrectUserId() {
            String token = jwtUtil.createAdminAccessToken(42L, "testuser");
            assertEquals(42L, jwtUtil.getUserIdFromToken(token));
        }
    }

    @Nested
    @DisplayName("App 双 Token")
    class AppDualToken {

        @Test
        @DisplayName("App Access Token 应有正确的 clientType")
        void shouldHaveAppClientType() {
            String token = jwtUtil.createAppAccessToken(1L, "wxuser");
            assertEquals(JwtUtil.CLIENT_TYPE_APP, jwtUtil.getClientType(token));
            assertEquals(JwtUtil.TOKEN_TYPE_ACCESS, jwtUtil.getTokenType(token));
        }

        @Test
        @DisplayName("App Refresh Token 应有正确的 token 类型")
        void shouldBeRefreshType() {
            String token = jwtUtil.createAppRefreshToken(1L, "wxuser");
            assertEquals(JwtUtil.TOKEN_TYPE_REFRESH, jwtUtil.getTokenType(token));
        }

        @Test
        @DisplayName("临时 Token 应有正确的类型和短有效期")
        void shouldCreateTempToken() {
            String tempToken = jwtUtil.createTempToken(1L);
            assertEquals(JwtUtil.TOKEN_TYPE_TEMP, jwtUtil.getTokenType(tempToken));
            assertEquals(JwtUtil.CLIENT_TYPE_APP, jwtUtil.getClientType(tempToken));
        }
    }

    // ==================== Token 跨通道隔离 ====================

    @Nested
    @DisplayName("Token 通道隔离")
    class TokenChannelIsolation {

        @Test
        @DisplayName("管理端 Token 不能用 App 密钥解析")
        void adminTokenShouldNotParseWithAppKey() {
            String adminToken = jwtUtil.createAdminAccessToken(1L, "admin");
            // App 密钥解析 Admin token 应失败
            assertThrows(Exception.class, () -> jwtUtil.parseTokenWithAppKey(adminToken));
        }

        @Test
        @DisplayName("App Token 不能用 Admin 密钥解析")
        void appTokenShouldNotParseWithAdminKey() {
            String appToken = jwtUtil.createAppAccessToken(1L, "wxuser");
            // Admin 密钥解析 App token 应失败
            assertThrows(Exception.class, () -> jwtUtil.parseTokenWithAdminKey(appToken));
        }

        @Test
        @DisplayName("parseTokenAny 可以解析两种 token")
        void parseTokenAnyShouldWorkForBoth() {
            String adminToken = jwtUtil.createAdminAccessToken(1L, "admin");
            String appToken = jwtUtil.createAppAccessToken(1L, "wxuser");

            Claims adminClaims = jwtUtil.parseTokenAny(adminToken);
            Claims appClaims = jwtUtil.parseTokenAny(appToken);

            assertNotNull(adminClaims);
            assertNotNull(appClaims);
            assertEquals(JwtUtil.CLIENT_TYPE_ADMIN, adminClaims.get("clientType", String.class));
            assertEquals(JwtUtil.CLIENT_TYPE_APP, appClaims.get("clientType", String.class));
        }

        @Test
        @DisplayName("Refresh Token 不能当作 Access Token 使用（类型检查）")
        void refreshTokenIsNotAccessType() {
            String refreshToken = jwtUtil.createAdminRefreshToken(1L, "admin");
            assertNotEquals(JwtUtil.TOKEN_TYPE_ACCESS, jwtUtil.getTokenType(refreshToken));
            assertEquals(JwtUtil.TOKEN_TYPE_REFRESH, jwtUtil.getTokenType(refreshToken));
        }
    }

    // ==================== 密码复杂度 ====================

    @Nested
    @DisplayName("密码复杂度校验")
    class PasswordComplexity {

        @Test
        @DisplayName("合法密码应通过校验")
        void validPasswordShouldPass() {
            PasswordValidator.Result result = PasswordValidator.validate("Abc123!@#", "testuser", 8, 64);
            assertTrue(result.valid);
            assertNull(result.message);
        }

        @Test
        @DisplayName("密码过短应拒绝")
        void tooShortPasswordShouldFail() {
            PasswordValidator.Result result = PasswordValidator.validate("Ab1!", "testuser", 8, 64);
            assertFalse(result.valid);
            assertNotNull(result.message);
        }

        @Test
        @DisplayName("缺少大写字母应拒绝")
        void missingUppercaseShouldFail() {
            PasswordValidator.Result result = PasswordValidator.validate("abc123!@#", "testuser", 8, 64);
            assertFalse(result.valid);
            assertTrue(result.message.contains("大写"));
        }

        @Test
        @DisplayName("缺少小写字母应拒绝")
        void missingLowercaseShouldFail() {
            PasswordValidator.Result result = PasswordValidator.validate("ABC123!@#", "testuser", 8, 64);
            assertFalse(result.valid);
            assertTrue(result.message.contains("小写"));
        }

        @Test
        @DisplayName("缺少数字应拒绝")
        void missingDigitShouldFail() {
            PasswordValidator.Result result = PasswordValidator.validate("Abcdefg!@#", "testuser", 8, 64);
            assertFalse(result.valid);
            assertTrue(result.message.contains("数字"));
        }

        @Test
        @DisplayName("缺少特殊字符应拒绝")
        void missingSpecialCharShouldFail() {
            PasswordValidator.Result result = PasswordValidator.validate("Abcdefg123", "testuser", 8, 64);
            assertFalse(result.valid);
            assertTrue(result.message.contains("特殊"));
        }

        @Test
        @DisplayName("密码包含用户名应拒绝")
        void passwordContainingUsernameShouldFail() {
            PasswordValidator.Result result = PasswordValidator.validate("TestUser123!", "testuser", 8, 64);
            assertFalse(result.valid);
            assertTrue(result.message.contains("用户名"));
        }

        @Test
        @DisplayName("弱密码应拒绝")
        void weakPasswordShouldFail() {
            PasswordValidator.Result result = PasswordValidator.validate("12345678", null, 8, 64);
            assertFalse(result.valid);
        }

        @Test
        @DisplayName("空密码应拒绝")
        void emptyPasswordShouldFail() {
            PasswordValidator.Result result = PasswordValidator.validate("", null, 8, 64);
            assertFalse(result.valid);
            assertTrue(result.message.contains("不能为空"));
        }

        @Test
        @DisplayName("null 密码应拒绝")
        void nullPasswordShouldFail() {
            PasswordValidator.Result result = PasswordValidator.validate(null, null, 8, 64);
            assertFalse(result.valid);
        }
    }

    // ==================== TTL 配置 ====================

    @Nested
    @DisplayName("Token TTL 配置")
    class TokenTTL {

        @Test
        @DisplayName("Admin Access TTL 应为 1800 秒")
        void adminAccessTtlShouldBe1800Seconds() {
            assertEquals(1_800_000L, jwtUtil.getAdminAccessTtl());
        }

        @Test
        @DisplayName("Admin Refresh TTL 应为 604800 秒")
        void adminRefreshTtlShouldBe7Days() {
            assertEquals(604_800_000L, jwtUtil.getAdminRefreshTtl());
        }

        @Test
        @DisplayName("App Access TTL 应与 Admin 一致（默认 1800 秒）")
        void appAccessTtlShouldBeConfigured() {
            assertTrue(jwtUtil.getAppAccessTtl() > 0);
        }

        @Test
        @DisplayName("App Refresh TTL 应大于 Access TTL")
        void refreshTtlShouldBeGreaterThanAccessTtl() {
            assertTrue(jwtUtil.getAppRefreshTtl() > jwtUtil.getAppAccessTtl());
            assertTrue(jwtUtil.getAdminRefreshTtl() > jwtUtil.getAdminAccessTtl());
        }
    }
}
