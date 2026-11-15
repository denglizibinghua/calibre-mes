package com.bikeassembly.interceptor;

import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.context.BaseContext;
import com.bikeassembly.entity.SysUser;
import com.bikeassembly.mapper.SysUserMapper;
import com.bikeassembly.service.PermissionService;
import com.bikeassembly.utils.JwtUtil;
import com.bikeassembly.utils.ErrorResponseWriter;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JWT 拦截器：校验 admin 端请求的 token，加载权限到 BaseContext。
 * 注意：拦截器 preHandle 中的异常不会被 @RestControllerAdvice 捕获，
 * 因此这里直接写 JSON 响应。
 */
@Component
@Slf4j
public class JwtTokenAdminInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final PermissionService permissionService;
    private final SysUserMapper sysUserMapper;

    public JwtTokenAdminInterceptor(JwtUtil jwtUtil, PermissionService permissionService,
                                     SysUserMapper sysUserMapper) {
        this.jwtUtil = jwtUtil;
        this.permissionService = permissionService;
        this.sysUserMapper = sysUserMapper;
    }

    /**
     * [答辩入口] JWT 拦截器 preHandle：每个 /admin/** 请求（除登录等白名单外）都会经过此方法。
     * [Token] 从请求头 token 中提取 JWT，仅使用 admin 密钥解析（拒绝 app 密钥签发的 Token）
     * [权限控制] 校验顺序：
     *   1. 放行 OPTIONS（CORS 预检）
     *   2. 检查请求头 token 是否存在
     *   3. 解析 JWT（admin 密钥）→ 校验签名和过期
     *   4. 校验 tokenType 必须是 access（拒绝 refresh token 访问业务接口）
     *   5. 校验 clientType 必须是 admin（拒绝 app token 访问管理端接口）
     *   6. 查询用户状态（不存在 → 401，已禁用 → 403）
     *   7. 检查密码安全状态（mustChangePassword / passwordExpired → 403 + 结构化错误码）
     *   8. 加载权限列表和数据范围到 BaseContext（ThreadLocal）
     * [实现情况] 拦截器中的异常不会被 @RestControllerAdvice 捕获，因此直接写 JSON 响应
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        // 放行 OPTIONS 请求（CORS 预检请求不携带 Token）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // [Token] 从请求头获取 token（请求头名称从配置 bike.jwt.admin.token-name 读取）
        String tokenName = jwtUtil.getAdminTokenName();
        String token = request.getHeader(tokenName);

        if (token == null || token.isBlank()) {
            log.warn("请求缺少 token，URI={}", request.getRequestURI());
            ErrorResponseWriter.write(response, HttpStatus.UNAUTHORIZED, MessageConstant.TOKEN_INVALID, null, request);
            return false;
        }

        // [Token] 解析 JWT：仅使用 admin 密钥，app 密钥签发的 Token 会因签名不匹配而失败
        try {
            Claims claims = jwtUtil.parseTokenWithAdminKey(token);
            Long userId = claims.get("userId", Long.class);

            // [Token] 校验 tokenType：业务接口只接受 access 类型，Refresh Token 不能用于业务访问
            String tokenType = claims.get("tokenType", String.class);
            if (!JwtUtil.TOKEN_TYPE_ACCESS.equals(tokenType)) {
                log.warn("非法的 token 类型：{}, URI={}", tokenType, request.getRequestURI());
                ErrorResponseWriter.write(response, HttpStatus.UNAUTHORIZED, "非法的Token类型", null, request);
                return false;
            }

            // [Token] 校验 clientType：管理端接口只接受 clientType=admin 的 Token
            String clientType = claims.get("clientType", String.class);
            if (!JwtUtil.CLIENT_TYPE_ADMIN.equals(clientType)) {
                log.warn("客户端类型不匹配：{}, URI={}", clientType, request.getRequestURI());
                ErrorResponseWriter.write(response, HttpStatus.UNAUTHORIZED, "客户端类型不匹配", null, request);
                return false;
            }

            // [权限控制] 检查账号状态：用户是否存在、是否被禁用
            SysUser user = sysUserMapper.selectById(userId);
            if (user == null) {
                log.warn("用户不存在：userId={}", userId);
                ErrorResponseWriter.write(response, HttpStatus.UNAUTHORIZED, "用户不存在", null, request);
                return false;
            }
            // status = "1" 表示账号已禁用
            if ("1".equals(user.getStatus())) {
                log.warn("账号已禁用：userId={}", userId);
                ErrorResponseWriter.write(response, HttpStatus.FORBIDDEN, MessageConstant.ACCOUNT_DISABLED_LOGOUT, null, request);
                return false;
            }

            // [业务规则] 密码安全状态检查：需要改密或密码过期时，只允许访问安全相关接口
            // 安全白名单：修改密码、退出、获取菜单、刷新 Token
            String requestUri = request.getRequestURI();
            boolean isSecurityEndpoint = requestUri.endsWith("/admin/sys-user/change-password")
                    || requestUri.endsWith("/admin/user/logout")
                    || requestUri.endsWith("/admin/user/menus")
                    || requestUri.endsWith("/admin/user/refresh-token");

            boolean needChangePassword = user.getMustChangePassword() != null && user.getMustChangePassword() == 1;
            boolean passwordExpired = user.getPasswordExpireAt() != null
                    && user.getPasswordExpireAt().isBefore(LocalDateTime.now());

            if (!isSecurityEndpoint && (needChangePassword || passwordExpired)) {
                // [业务规则] 非安全接口 + 需要改密 → 返回 403 + 结构化错误码 SECURITY_PASSWORD_CHANGE_REQUIRED
                // 前端根据 errorCode 自动跳转到修改密码页
                log.warn("用户需修改密码或密码已过期：userId={}, mustChange={}, expired={}, URI={}",
                        userId, needChangePassword, passwordExpired, requestUri);
                Map<String, Object> securityData = new LinkedHashMap<>();
                securityData.put("errorCode", "SECURITY_PASSWORD_CHANGE_REQUIRED");
                securityData.put("mustChangePassword", needChangePassword);
                securityData.put("passwordExpired", passwordExpired);
                ErrorResponseWriter.write(response, HttpStatus.FORBIDDEN, "密码需要修改", securityData, request);
                return false;
            }

            // [答辩重点] 将当前请求的上下文信息存入 ThreadLocal（BaseContext），后续 Controller/Service 可通过 BaseContext.getCurrentId() 获取
            BaseContext.setCurrentId(userId);
            BaseContext.setCurrentClientType(JwtUtil.CLIENT_TYPE_ADMIN);
            BaseContext.setCurrentTokenJti(claims.getId());

            // [权限控制] 加载用户权限码列表和数据范围到 BaseContext
            // 权限从 Caffeine 缓存加载（30分钟 TTL），缓存未命中时查询 sys_role_menu 表
            List<String> permissions = List.copyOf(permissionService.getUserPermissions(userId));
            BaseContext.setCurrentPermissions(permissions);
            String dataScope = permissionService.getUserDataScope(userId);
            BaseContext.setCurrentDataScope(dataScope);

            log.debug("Admin JWT 校验通过，userId={}, URI={}, permCount={}",
                    userId, request.getRequestURI(), permissions.size());
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("token 已过期，URI={}", request.getRequestURI());
            Map<String, Object> expiredData = new LinkedHashMap<>();
            expiredData.put("errorCode", "TOKEN_EXPIRED");
            ErrorResponseWriter.write(response, HttpStatus.UNAUTHORIZED, "token已过期，请重新登录", expiredData, request);
            return false;
        } catch (SignatureException | MalformedJwtException e) {
            log.warn("token 无效，URI={}", request.getRequestURI());
            ErrorResponseWriter.write(response, HttpStatus.UNAUTHORIZED, MessageConstant.TOKEN_INVALID, null, request);
            return false;
        } catch (Exception e) {
            log.error("JWT 解析异常，URI={}", request.getRequestURI(), e);
            ErrorResponseWriter.write(response, HttpStatus.UNAUTHORIZED, MessageConstant.TOKEN_INVALID, null, request);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
            throws Exception {
        // 清理 ThreadLocal，防止内存泄漏
        BaseContext.removeAll();
    }

    /**
     * 写 JSON 错误响应，统一委托 ErrorResponseWriter，格式与 Result 保持一致。
     */
    private void writeErrorResponse(HttpServletResponse response, HttpStatus status, String message,
                                    Map<String, Object> data, HttpServletRequest request) throws Exception {
        ErrorResponseWriter.write(response, status, message, data, request);
    }
}
