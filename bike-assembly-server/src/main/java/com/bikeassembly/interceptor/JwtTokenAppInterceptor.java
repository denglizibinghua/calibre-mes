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
 * JWT 拦截器：校验小程序端 /app/** 请求的 token。
 * <p>
 * temp token 仅允许访问 /app/user/wx-bind；full token 允许所有 /app/** 路径。
 * 校验通过后加载用户权限和数据范围到 BaseContext。
 * 注意：拦截器 preHandle 中的异常不会被 @RestControllerAdvice 捕获，
 * 因此这里直接写 JSON 响应。
 */
@Component
@Slf4j
public class JwtTokenAppInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final PermissionService permissionService;
    private final SysUserMapper sysUserMapper;

    /** temp token 唯一放行的 URL */
    private static final String WX_BIND_URI = "/app/user/wx-bind";

    public JwtTokenAppInterceptor(JwtUtil jwtUtil, PermissionService permissionService,
                                   SysUserMapper sysUserMapper) {
        this.jwtUtil = jwtUtil;
        this.permissionService = permissionService;
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        // 放行 OPTIONS 请求（CORS 预检），解决跨域报错
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 从请求头获取 token（使用 app 专属 token name）
        String tokenName = jwtUtil.getAppTokenName();
        String token = request.getHeader(tokenName);

        if (token == null || token.isBlank()) {
            log.warn("请求缺少 token，URI={}", request.getRequestURI());
            ErrorResponseWriter.write(response, HttpStatus.UNAUTHORIZED, MessageConstant.TOKEN_INVALID, null, request);
            return false;
        }

        // 解析 JWT（仅使用 app 密钥）
        try {
            Claims claims = jwtUtil.parseTokenWithAppKey(token);
            Long userId = claims.get("userId", Long.class);

            String tokenType = claims.get("tokenType", String.class);

            if (JwtUtil.TOKEN_TYPE_TEMP.equals(tokenType)) {
                // 临时 token：仅放行绑定接口
                if (!WX_BIND_URI.equals(request.getRequestURI())) {
                    log.warn("temp token 无权访问 URI={}", request.getRequestURI());
                    ErrorResponseWriter.write(response, HttpStatus.FORBIDDEN, "请先完成账号绑定", null, request);
                    return false;
                }
                BaseContext.setCurrentId(userId);
                BaseContext.setCurrentClientType(JwtUtil.CLIENT_TYPE_APP);
                return true;
            }

            // 校验 tokenType — 仅接受 access 类型（向后兼容 full）
            if (!JwtUtil.TOKEN_TYPE_ACCESS.equals(tokenType)) {
                log.warn("非法的 token 类型：{}, URI={}", tokenType, request.getRequestURI());
                ErrorResponseWriter.write(response, HttpStatus.UNAUTHORIZED, "非法的Token类型", null, request);
                return false;
            }

            // 校验 clientType — app 拦截器只接受 app token
            String clientType = claims.get("clientType", String.class);
            if (!JwtUtil.CLIENT_TYPE_APP.equals(clientType)) {
                log.warn("客户端类型不匹配（期望 app，实际 {}），URI={}", clientType, request.getRequestURI());
                ErrorResponseWriter.write(response, HttpStatus.UNAUTHORIZED, "客户端类型不匹配，请使用小程序端登录", null, request);
                return false;
            }

            // full/access token：检查账号是否被禁用
            SysUser user = sysUserMapper.selectById(userId);
            if (user == null) {
                log.warn("用户不存在：userId={}", userId);
                ErrorResponseWriter.write(response, HttpStatus.UNAUTHORIZED, "用户不存在", null, request);
                return false;
            }
            if ("1".equals(user.getStatus())) {
                log.warn("账号已禁用：userId={}", userId);
                ErrorResponseWriter.write(response, HttpStatus.FORBIDDEN, MessageConstant.ACCOUNT_DISABLED_LOGOUT, null, request);
                return false;
            }

            // 检查安全状态：需要修改密码或密码已过期时，仅放行特定接口
            String requestUri = request.getRequestURI();
            boolean isSecurityEndpoint = requestUri.endsWith("/app/user/me")
                    || requestUri.endsWith("/app/user/refresh-token");

            boolean needChangePassword = user.getMustChangePassword() != null && user.getMustChangePassword() == 1;
            boolean passwordExpired = user.getPasswordExpireAt() != null
                    && user.getPasswordExpireAt().isBefore(LocalDateTime.now());

            if (!isSecurityEndpoint && (needChangePassword || passwordExpired)) {
                log.warn("App用户需修改密码或密码已过期：userId={}, mustChange={}, expired={}, URI={}",
                        userId, needChangePassword, passwordExpired, requestUri);
                Map<String, Object> securityData = new LinkedHashMap<>();
                securityData.put("errorCode", "SECURITY_PASSWORD_CHANGE_REQUIRED");
                securityData.put("mustChangePassword", needChangePassword);
                securityData.put("passwordExpired", passwordExpired);
                ErrorResponseWriter.write(response, HttpStatus.FORBIDDEN, "密码需要修改", securityData, request);
                return false;
            }

            // 写入 BaseContext
            BaseContext.setCurrentId(userId);
            BaseContext.setCurrentClientType(JwtUtil.CLIENT_TYPE_APP);
            BaseContext.setCurrentTokenJti(claims.getId());

            // 加载权限和数据范围
            List<String> permissions = List.copyOf(permissionService.getUserPermissions(userId));
            BaseContext.setCurrentPermissions(permissions);
            String dataScope = permissionService.getUserDataScope(userId);
            BaseContext.setCurrentDataScope(dataScope);

            log.debug("App JWT 校验通过，userId={}, tokenType={}, URI={}, permCount={}",
                    userId, tokenType, request.getRequestURI(), permissions.size());
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
