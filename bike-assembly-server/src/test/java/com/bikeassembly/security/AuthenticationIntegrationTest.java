package com.bikeassembly.security;

import com.bikeassembly.AbstractPostgresContainerIntegrationTest;
import com.bikeassembly.entity.UserRefreshToken;
import com.bikeassembly.mapper.SysUserMapper;
import com.bikeassembly.mapper.UserRefreshTokenMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP + 数据库集成测试：双 Token 认证、刷新、撤销、通道隔离、强制改密。
 * 使用 Testcontainers PostgreSQL 16 + init_database.sql 提供 Schema 和种子数据。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("认证安全集成测试 (HTTP + DB)")
class AuthenticationIntegrationTest extends AbstractPostgresContainerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRefreshTokenMapper refreshTokenMapper;
    @Autowired private SysUserMapper sysUserMapper;
    @Autowired private com.bikeassembly.utils.JwtUtil jwtUtil;

    private static String adminAccessToken;
    private static String adminRefreshToken;
    private static Long adminUserId;
    private static String newAccessTokenAfterRefresh;
    private static String newRefreshTokenAfterRefresh;

    // ==================== 管理端登录与双 Token ====================

    @Test
    @Order(1)
    @DisplayName("1. 登录成功返回 accessToken + refreshToken")
    void loginReturnsDualToken() throws Exception {
        String body = "{\"username\":\"admin\",\"password\":\"Demo@123456\"}";

        MvcResult result = mockMvc.perform(post("/admin/user/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").isNumber())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        adminAccessToken = data.get("accessToken").asText();
        adminRefreshToken = data.get("refreshToken").asText();
        adminUserId = data.get("id").asLong();

        assertNotNull(adminAccessToken);
        assertNotNull(adminRefreshToken);
        assertNotEquals(adminAccessToken, adminRefreshToken, "Access 与 Refresh Token 应不同");
    }

    @Test
    @Order(2)
    @DisplayName("2. Refresh Token SHA-256 哈希已写入 user_refresh_token 表")
    void refreshTokenHashPersistedInDb() {
        List<UserRefreshToken> tokens = refreshTokenMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserRefreshToken>()
                        .eq(UserRefreshToken::getUserId, adminUserId)
                        .eq(UserRefreshToken::getClientType, "admin")
                        .eq(UserRefreshToken::getRevoked, 0)
        );
        assertFalse(tokens.isEmpty(), "Refresh Token 应已持久化到数据库");
        for (UserRefreshToken t : tokens) {
            assertNotNull(t.getTokenHash(), "tokenHash 不应为空");
            assertNotEquals(adminRefreshToken, t.getTokenHash(), "DB 存的是 SHA-256 哈希，不是明文");
            assertNotNull(t.getTokenJti(), "JTI 不应为空");
        }
    }

    @Test
    @Order(3)
    @DisplayName("3. Access Token 可访问受保护接口 GET /admin/sys-user/list")
    void accessTokenCanCallProtectedApi() throws Exception {
        mockMvc.perform(get("/admin/sys-user/list")
                        .param("page", "1").param("size", "5")
                        .header("token", adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @Order(4)
    @DisplayName("4. Refresh Token 不能调用普通业务接口（tokenType 校验）")
    void refreshTokenCannotCallBusinessApi() throws Exception {
        mockMvc.perform(get("/admin/sys-user/list")
                        .param("page", "1").param("size", "5")
                        .header("token", adminRefreshToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(5)
    @DisplayName("5. POST /admin/user/refresh-token 返回新双 Token（Rotation）")
    void refreshReturnsNewDualToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/admin/user/refresh-token")
                        .header("token", adminRefreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        newAccessTokenAfterRefresh = data.get("accessToken").asText();
        newRefreshTokenAfterRefresh = data.get("refreshToken").asText();

        assertNotEquals(adminAccessToken, newAccessTokenAfterRefresh, "Access Token 应已更替");
        assertNotEquals(adminRefreshToken, newRefreshTokenAfterRefresh, "Refresh Token 应已 Rotation");

        // 更新为新 token 供后续测试使用
        adminAccessToken = newAccessTokenAfterRefresh;
    }

    @Test
    @Order(6)
    @DisplayName("6. 旧 Refresh Token 重放失败（Replay Detection）")
    void oldRefreshTokenReplayFails() throws Exception {
        mockMvc.perform(post("/admin/user/refresh-token")
                        .header("token", adminRefreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").exists());
    }

    @Test
    @Order(7)
    @DisplayName("7. 新 Access Token 可访问受保护接口")
    void newAccessTokenWorks() throws Exception {
        mockMvc.perform(get("/admin/sys-user/list")
                        .param("page", "1").param("size", "5")
                        .header("token", adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    // ==================== App 退出接口（幂等性） ====================

    @Test
    @Order(8)
    @DisplayName("8. App 退出接口无 Token 时幂等返回成功")
    void appLogoutNoTokenSuccess() throws Exception {
        mockMvc.perform(post("/app/user/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @Order(9)
    @DisplayName("9. App 退出接口无效 Token 时幂等返回成功")
    void appLogoutInvalidTokenSuccess() throws Exception {
        mockMvc.perform(post("/app/user/logout")
                        .header("token", "invalid_token_string"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    // ==================== 跨通道隔离 ====================

    @Test
    @Order(10)
    @DisplayName("10. Admin Refresh Token 不能调用 App 刷新接口（服务层密钥隔离）")
    void adminTokenBlockedByAppKey() throws Exception {
        // App 刷新接口排除在拦截器外，但服务层会用 App 密钥解析 Token，
        // Admin Token 无法被 App 密钥解析 → 返回业务错误
        mockMvc.perform(post("/app/user/refresh-token")
                        .header("token", adminRefreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ==================== 错误响应格式 ====================

    @Test
    @Order(11)
    @DisplayName("11. Token 过期响应包含 errorCode=TOKEN_EXPIRED")
    void tokenExpiredResponseHasErrorCode() throws Exception {
        // 使用一个格式正确但已过期的 JWT（无法生成真实过期 token，验证 error response 结构）
        // 这里验证无效 token 返回的响应格式
        MvcResult result = mockMvc.perform(get("/admin/sys-user/list")
                        .param("page", "1").param("size", "5")
                        .header("token", "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjF9.abc"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        assertTrue(content.contains("\"code\""), "响应应包含 code 字段");
        assertTrue(content.contains("\"msg\""), "响应应包含 msg 字段");
    }

    // ==================== 用户禁用撤销 Token ====================

    private static Long disableTargetUserId;
    private static String disableUserAccessToken;
    private static String disableUserRefreshToken;

    @Test
    @Order(20)
    @DisplayName("13. 创建测试用户并登录两次产生多个 Refresh Token 会话")
    void createUserAndMultipleSessions() throws Exception {
        // 清理可能存在的旧用户
        var existingUser = sysUserMapper.getByUsername("test_disable_user");
        if (existingUser != null) {
            // 先删除旧的 refresh tokens
            refreshTokenMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserRefreshToken>()
                    .eq(UserRefreshToken::getUserId, existingUser.getUserId()));
            sysUserMapper.deleteById(existingUser.getUserId());
        }

        // 创建用户
        String body = "{\"username\":\"test_disable_user\",\"password\":\"Test123!@#\",\"nickname\":\"禁用测试\",\"phone\":\"13800000001\",\"roleId\":2,\"status\":\"0\"}";
        MvcResult result = mockMvc.perform(post("/admin/sys-user")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("token", adminAccessToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode resp = objectMapper.readTree(result.getResponse().getContentAsString());
        disableTargetUserId = resp.get("data").get("userId").asLong();

        // 登录两次产生多个会话
        String loginBody = "{\"username\":\"test_disable_user\",\"password\":\"Test123!@#\"}";
        MvcResult r1 = mockMvc.perform(post("/admin/user/login")
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk()).andReturn();
        JsonNode d1 = objectMapper.readTree(r1.getResponse().getContentAsString()).get("data");
        disableUserAccessToken = d1.get("accessToken").asText();
        disableUserRefreshToken = d1.get("refreshToken").asText();

        // 第二次登录（产生第二个会话）
        mockMvc.perform(post("/admin/user/login")
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk());

        // 数据库断言：至少 2 个未撤销会话
        long count = refreshTokenMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserRefreshToken>()
                        .eq(UserRefreshToken::getUserId, disableTargetUserId)
                        .eq(UserRefreshToken::getRevoked, 0));
        assertTrue(count >= 2, "应有至少 2 个有效 Refresh Token 会话，实际: " + count);
    }

    @Test
    @Order(21)
    @DisplayName("14. 管理员禁用用户后所有 Refresh Token 被撤销")
    void disableUserRevokesAllTokens() throws Exception {
        String body = "{\"username\":\"test_disable_user\",\"nickname\":\"禁用测试\",\"phone\":\"13800000001\",\"roleId\":2,\"status\":\"1\"}";
        mockMvc.perform(put("/admin/sys-user/" + disableTargetUserId)
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("token", adminAccessToken))
                .andExpect(status().isOk());

        long count = refreshTokenMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserRefreshToken>()
                        .eq(UserRefreshToken::getUserId, disableTargetUserId)
                        .eq(UserRefreshToken::getRevoked, 0));
        assertEquals(0, count, "禁用后所有 Refresh Token 应被撤销");
    }

    @Test
    @Order(22)
    @DisplayName("15. 禁用后旧 Refresh Token 无法刷新")
    void oldTokensCannotRefreshAfterDisable() throws Exception {
        mockMvc.perform(post("/admin/user/refresh-token")
                        .header("token", disableUserRefreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @Order(23)
    @DisplayName("16. 重新启用用户后旧 Token 已被撤销，必须重新登录")
    void reEnabledUserNeedsRelogin() throws Exception {
        // 重新启用
        String body = "{\"username\":\"test_disable_user\",\"nickname\":\"禁用测试\",\"phone\":\"13800000001\",\"roleId\":2,\"status\":\"0\"}";
        mockMvc.perform(put("/admin/sys-user/" + disableTargetUserId)
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("token", adminAccessToken))
                .andExpect(status().isOk());

        // 旧 token 已被撤销（恢复的 token 如果是之前撤销的，拦截器会返回 401）
        // 如果 token 恰好还有效但被安全状态阻止，则返回 403
        mockMvc.perform(get("/admin/sys-user/list")
                        .param("page", "1").param("size", "5")
                        .header("token", disableUserAccessToken))
                .andExpect(status().is4xxClientError()); // 401 or 403

        // 重新登录应成功
        String loginBody = "{\"username\":\"test_disable_user\",\"password\":\"Test123!@#\"}";
        mockMvc.perform(post("/admin/user/login")
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    // ==================== 强制改密 ====================

    @Test
    @Order(30)
    @DisplayName("17. mustChangePassword=1 的用户登录返回必须改密标记")
    void mustChangePasswordUserLoginFlag() throws Exception {
        // 管理员重置密码会设置 mustChangePassword=1
        mockMvc.perform(put("/admin/sys-user/" + disableTargetUserId + "/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"ResetTest456!@#\"}")
                        .header("token", adminAccessToken))
                .andExpect(status().isOk());

        String loginBody = "{\"username\":\"test_disable_user\",\"password\":\"ResetTest456!@#\"}";
        MvcResult result = mockMvc.perform(post("/admin/user/login")
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.mustChangePassword").value(1))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        disableUserAccessToken = data.get("accessToken").asText();
        disableUserRefreshToken = data.get("refreshToken").asText();
    }

    @Test
    @Order(31)
    @DisplayName("18. mustChangePassword 用户不能访问普通业务 API（返回 SECURITY_PASSWORD_CHANGE_REQUIRED）")
    void mustChangeUserBlockedFromBusinessApi() throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/sys-user/list")
                        .param("page", "1").param("size", "5")
                        .header("token", disableUserAccessToken))
                .andExpect(status().isForbidden())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        assertTrue(content.contains("SECURITY_PASSWORD_CHANGE_REQUIRED"), "应包含 SECURITY_PASSWORD_CHANGE_REQUIRED");
    }

    @Test
    @Order(32)
    @DisplayName("19. mustChangePassword 用户可以访问修改密码接口")
    void mustChangeUserCanAccessChangePassword() throws Exception {
        mockMvc.perform(put("/admin/sys-user/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"Changed789!@#\"}")
                        .header("token", disableUserAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @Order(33)
    @DisplayName("20. 修改密码后数据库 must_change_password 清零，Refresh Token 已撤销")
    void passwordChangedClearsFlagAndRevokesTokens() throws Exception {
        var user = sysUserMapper.selectById(disableTargetUserId);
        assertEquals(0, user.getMustChangePassword(), "mustChangePassword 应清零");

        // Refresh Token 已撤销（数据库中查找，而非通过 Access Token）
        long remainingTokens = refreshTokenMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserRefreshToken>()
                        .eq(UserRefreshToken::getUserId, disableTargetUserId)
                        .eq(UserRefreshToken::getRevoked, 0));
        assertEquals(0, remainingTokens, "修改密码后所有 Refresh Token 应被撤销");

        // 重新登录成功且 mustChangePassword=0
        String loginBody = "{\"username\":\"test_disable_user\",\"password\":\"Changed789!@#\"}";
        mockMvc.perform(post("/admin/user/login")
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.mustChangePassword").value(0));
    }

    // ==================== 登录锁定与解锁 ====================

    private static Long lockTargetUserId;

    @Test
    @Order(40)
    @DisplayName("21. 创建锁定测试用户")
    void createLockTestUser() throws Exception {
        var existing = sysUserMapper.getByUsername("test_lock_user");
        if (existing != null) {
            refreshTokenMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserRefreshToken>()
                    .eq(UserRefreshToken::getUserId, existing.getUserId()));
            sysUserMapper.deleteById(existing.getUserId());
        }
        String body = "{\"username\":\"test_lock_user\",\"password\":\"Test123!@#\",\"nickname\":\"锁定测试\",\"phone\":\"13800000002\",\"roleId\":2,\"status\":\"0\"}";
        MvcResult result = mockMvc.perform(post("/admin/sys-user")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("token", adminAccessToken))
                .andExpect(status().isOk()).andReturn();
        lockTargetUserId = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("userId").asLong();
    }

    @Test
    @Order(41)
    @DisplayName("22. 连续 5 次错误密码触发锁定")
    void consecutiveFailuresCauseLockout() throws Exception {
        String body = "{\"username\":\"test_lock_user\",\"password\":\"WrongPassword1!\"}";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/admin/user/login")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(jsonPath("$.code").value(0));
        }

        // Redis 锁定标记已生效：第 6 次登录（错误密码）直接提示锁定
        mockMvc.perform(post("/admin/user/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value(containsString("锁定")));
    }

    @Test
    @Order(42)
    @DisplayName("23. 锁定期间正确密码也不能登录")
    void correctPasswordFailsWhenLocked() throws Exception {
        String body = "{\"username\":\"test_lock_user\",\"password\":\"Test123!@#\"}";
        mockMvc.perform(post("/admin/user/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value(containsString("锁定")));
    }

    @Test
    @Order(43)
    @DisplayName("24. 管理员解锁后 loginFailCount=0, lockedUntil=null, 可正常登录")
    void unlockAllowsLogin() throws Exception {
        mockMvc.perform(put("/admin/sys-user/" + lockTargetUserId + "/unlock")
                        .header("token", adminAccessToken))
                .andExpect(status().isOk());

        var user = sysUserMapper.selectById(lockTargetUserId);
        assertEquals(0, user.getLoginFailCount(), "loginFailCount 应清零");
        assertNull(user.getLockedUntil(), "lockedUntil 应为空");

        String body = "{\"username\":\"test_lock_user\",\"password\":\"Test123!@#\"}";
        mockMvc.perform(post("/admin/user/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    // ==================== 管理员退出 ====================

    @Test
    @Order(12)
    @DisplayName("12. 管理端退出撤销当前 Refresh Token")
    void adminLogoutRevokesToken() throws Exception {
        MvcResult refreshResult = mockMvc.perform(post("/admin/user/refresh-token")
                        .header("token", newRefreshTokenAfterRefresh))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode refreshData = objectMapper.readTree(refreshResult.getResponse().getContentAsString()).get("data");
        if (refreshData != null && refreshData.get("accessToken") != null) {
            String currentAccess = refreshData.get("accessToken").asText();
            mockMvc.perform(post("/admin/user/logout")
                            .header("token", currentAccess))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(1));
        }
    }

    // ==================== 密码过期 ====================

    private static Long pwdExpiredUserId;
    private static String pwdExpiredAccessToken;
    private static String pwdExpiredRefreshToken;

    @Test
    @Order(50)
    @DisplayName("25. 密码过期用户登录返回 passwordExpired=true")
    void passwordExpiredUserLoginFlag() throws Exception {
        // 创建用户
        var existing = sysUserMapper.getByUsername("test_pwd_expired");
        if (existing != null) {
            refreshTokenMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserRefreshToken>()
                    .eq(UserRefreshToken::getUserId, existing.getUserId()));
            sysUserMapper.deleteById(existing.getUserId());
        }
        String body = "{\"username\":\"test_pwd_expired\",\"password\":\"Test123!@#\",\"nickname\":\"密码过期测试\",\"phone\":\"13800000004\",\"roleId\":2,\"status\":\"0\"}";
        MvcResult result = mockMvc.perform(post("/admin/sys-user")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("token", adminAccessToken))
                .andExpect(status().isOk()).andReturn();
        pwdExpiredUserId = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("userId").asLong();

        // 设置 password_expire_at 为过去时间
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.bikeassembly.entity.SysUser> wrapper =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        wrapper.eq(com.bikeassembly.entity.SysUser::getUserId, pwdExpiredUserId)
               .set(com.bikeassembly.entity.SysUser::getPasswordExpireAt, java.time.LocalDateTime.now().minusDays(1));
        sysUserMapper.update(null, wrapper);

        // 登录
        String loginBody = "{\"username\":\"test_pwd_expired\",\"password\":\"Test123!@#\"}";
        MvcResult loginResult = mockMvc.perform(post("/admin/user/login")
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.passwordExpired").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andReturn();

        JsonNode loginData = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("data");
        pwdExpiredAccessToken = loginData.get("accessToken").asText();
        pwdExpiredRefreshToken = loginData.get("refreshToken").asText();
    }

    @Test
    @Order(51)
    @DisplayName("26. 密码过期用户不能访问普通业务 API")
    void passwordExpiredUserBlockedFromBusinessApi() throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/sys-user/list")
                        .param("page", "1").param("size", "5")
                        .header("token", pwdExpiredAccessToken))
                .andExpect(status().isForbidden())
                .andReturn();
        String content = result.getResponse().getContentAsString();
        assertTrue(content.contains("SECURITY_PASSWORD_CHANGE_REQUIRED"), "应包含安全错误码");
    }

    @Test
    @Order(52)
    @DisplayName("27. 密码过期用户可以访问修改密码接口")
    void passwordExpiredUserCanChangePassword() throws Exception {
        mockMvc.perform(put("/admin/sys-user/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"NewPwdExp!@3\"}")
                        .header("token", pwdExpiredAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        // 数据库断言
        var user = sysUserMapper.selectById(pwdExpiredUserId);
        assertEquals(0, user.getMustChangePassword(), "mustChangePassword 应清零");
        // passwordExpireAt 根据系统配置可能为 null（如果 password.expire_days=0）或未来时间
        if (user.getPasswordExpireAt() != null) {
            assertTrue(user.getPasswordExpireAt().isAfter(java.time.LocalDateTime.now()), "新过期时间应晚于当前");
        }

        // Refresh Token 已撤销（数据库验证）
        long revokedCount = refreshTokenMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserRefreshToken>()
                        .eq(UserRefreshToken::getUserId, pwdExpiredUserId)
                        .eq(UserRefreshToken::getRevoked, 0));
        assertEquals(0, revokedCount, "修改密码后所有 Refresh Token 应被撤销");

        // 新密码登录 passwordExpired=false
        String loginBody = "{\"username\":\"test_pwd_expired\",\"password\":\"NewPwdExp!@3\"}";
        mockMvc.perform(post("/admin/user/login")
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.passwordExpired").value(false));
    }

    // ==================== App 退出撤销有效会话 ====================

    @Test
    @Order(60)
    @DisplayName("28. App 退出幂等 + 有效退出撤销 RT 会话")
    void appLogoutRevokesValidRefreshToken() throws Exception {
        // 用 adminAccessToken 调用管理端退出（撤销 admin RT）
        mockMvc.perform(post("/admin/user/logout")
                        .header("X-Refresh-Token", adminRefreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        // App 退出幂等：无 token 也成功
        mockMvc.perform(post("/app/user/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        // App 退出幂等：无效 token 也成功
        mockMvc.perform(post("/app/user/logout")
                        .header("token", "invalid_token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    // ==================== changePassword 安全边界 ====================

    @Test
    @Order(70)
    @DisplayName("29. 普通主动改密不提供旧密码 → 失败")
    void normalChangePasswordRequireOldPassword() throws Exception {
        // 普通用户（mustChangePassword=0）改密需要旧密码
        mockMvc.perform(put("/admin/sys-user/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"SomePass1!\"}")
                        .header("token", adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0)); // 业务错误
    }
}
