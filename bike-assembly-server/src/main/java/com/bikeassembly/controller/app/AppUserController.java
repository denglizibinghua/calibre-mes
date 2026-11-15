package com.bikeassembly.controller.app;

import com.bikeassembly.context.BaseContext;
import com.bikeassembly.dto.UserLoginDTO;
import com.bikeassembly.dto.WxBindDTO;
import com.bikeassembly.dto.WxLoginDTO;
import com.bikeassembly.dto.FaceLoginDTO;
import com.bikeassembly.dto.FaceTemplateDTO;
import com.bikeassembly.entity.SysRole;
import com.bikeassembly.entity.SysUser;
import com.bikeassembly.mapper.SysRoleMapper;
import com.bikeassembly.mapper.SysUserMapper;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.RefreshTokenService;
import com.bikeassembly.service.SysUserService;
import com.bikeassembly.service.FaceAuthService;
import com.bikeassembly.utils.JwtUtil;
import com.bikeassembly.vo.UserLoginVO;
import com.bikeassembly.vo.UserProfileVO;
import com.bikeassembly.vo.WxBindVO;
import com.bikeassembly.vo.WxLoginVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 小程序端用户接口 — 微信登录、账号绑定、当前用户信息。
 */
@RestController
@RequestMapping("/app/user")
@Slf4j
public class AppUserController {

    @Autowired
    private SysUserService sysUserService;
    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private SysRoleMapper sysRoleMapper;
    @Autowired
    private com.bikeassembly.service.PermissionService permissionService;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private FaceAuthService faceAuthService;

    /**
     * 微信小程序登录。
     * 公开端点（不在拦截器保护范围内）。
     */
    @PostMapping("/wx-login")
    public Result<WxLoginVO> wxLogin(@RequestBody WxLoginDTO dto) {
        if (dto.getCode() == null || dto.getCode().isBlank()) {
            log.warn("微信登录请求缺少 code 参数");
            return Result.error("缺少微信登录凭证");
        }
        log.info("微信登录请求，code={}***", dto.getCode().substring(0, Math.min(dto.getCode().length(), 6)));
        WxLoginVO vo = sysUserService.wxLogin(dto);
        return Result.success(vo);
    }

    /**
     * App 端用户名密码登录。
     * 公开端点（不在拦截器保护范围内）。
     * 签发 App 端 Token，与 /admin/user/login 生成的管理端 Token 使用不同签名密钥。
     */
    @PostMapping("/login")
    public Result<UserLoginVO> login(@RequestBody @Valid UserLoginDTO dto) {
        log.info("App端用户名密码登录请求，username={}", dto.getUsername());
        UserLoginVO vo = sysUserService.appLogin(dto);
        return Result.success(vo);
    }

    @PostMapping("/face-login")
    public Result<UserLoginVO> faceLogin(@RequestBody @Valid FaceLoginDTO dto) {
        return Result.success(faceAuthService.login(dto, JwtUtil.CLIENT_TYPE_APP));
    }

    @PostMapping("/face-template")
    public Result<Void> registerOwnFace(@RequestBody @Valid FaceTemplateDTO dto) {
        faceAuthService.register(BaseContext.getCurrentId(), dto);
        return Result.success();
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/face-template")
    public Result<Void> revokeOwnFace() {
        faceAuthService.revoke(BaseContext.getCurrentId());
        return Result.success();
    }

    @GetMapping("/face-template")
    public Result<Boolean> ownFaceStatus() {
        return Result.success(faceAuthService.isRegistered(BaseContext.getCurrentId()));
    }

    /**
     * 微信账号绑定 / 注册。
     * 需要 temp token（由 JwtTokenAppInterceptor 校验）。
     */
    @PostMapping("/wx-bind")
    public Result<WxBindVO> wxBind(@RequestBody WxBindDTO dto) {
        Long userId = BaseContext.getCurrentId();
        log.info("微信绑定请求，userId={}, bindType={}", userId, dto.getBindType());
        WxBindVO vo = sysUserService.bindUser(userId, dto);
        return Result.success(vo);
    }

    /**
     * 获取当前登录用户信息（角色、权限、数据范围）。
     * GET /app/user/me
     */
    /**
     * GET /app/user/me
     * 获取当前登录用户信息。
     * 权限和数据范围从 PermissionService 加载（含缓存），
     * 而非从 BaseContext 读取，以确保角色变更后刷新可获得最新权限。
     */
    @GetMapping("/me")
    public Result<UserProfileVO> me() {
        Long userId = BaseContext.getCurrentId();
        SysUser user = sysUserMapper.selectById(userId);

        // 从 PermissionService 加载权限（支持缓存，角色变更时主动清除）
        Set<String> permSet = permissionService.getUserPermissions(userId);
        List<String> permissions = new java.util.ArrayList<>(permSet);

        // 从 PermissionService 加载数据范围
        String dataScope = permissionService.getUserDataScope(userId);
        if (dataScope == null) dataScope = "SELF";

        List<String> roles = Collections.emptyList();
        if (user != null && user.getRoleId() != null) {
            SysRole role = sysRoleMapper.selectById(user.getRoleId());
            if (role != null) {
                roles = Collections.singletonList(role.getRoleKey());
            }
        }

        boolean passwordExpired = user != null && user.getPasswordExpireAt() != null
                && user.getPasswordExpireAt().isBefore(java.time.LocalDateTime.now());

        UserProfileVO vo = UserProfileVO.builder()
                .userId(userId)
                .username(user != null ? user.getUsername() : "")
                .nickname(user != null ? user.getNickname() : "")
                .roles(roles)
                .permissions(permissions)
                .dataScope(dataScope)
                .mustChangePassword(user != null ? user.getMustChangePassword() : 0)
                .passwordExpired(passwordExpired)
                .build();
        return Result.success(vo);
    }

    /**
     * POST /app/user/refresh-token
     * 使用 Refresh Token 刷新 Access Token。
     * Header: token = <refresh_token>
     */
    @PostMapping("/refresh-token")
    public Result<Map<String, Object>> refreshToken(HttpServletRequest request) {
        String refreshToken = request.getHeader("token");
        if (refreshToken == null || refreshToken.isBlank()) {
            return Result.error("缺少 Refresh Token");
        }

        RefreshTokenService.RefreshResult result =
                sysUserService.refreshToken(refreshToken, JwtUtil.CLIENT_TYPE_APP);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accessToken", result.accessToken());
        data.put("refreshToken", result.refreshToken());
        data.put("tokenType", "Bearer");
        data.put("expiresIn", result.expiresIn());

        log.info("App Token 刷新成功");
        return Result.success(data);
    }

    /**
     * POST /app/user/logout
     * 小程序安全退出：撤销当前 Refresh Token 会话。
     * Header: token = <refresh_token>
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String tokenHeader = request.getHeader("token");
        if (tokenHeader != null && !tokenHeader.isBlank()) {
            try {
                String tokenJti = jwtUtil.getTokenJti(tokenHeader);
                String clientType = jwtUtil.getClientType(tokenHeader);
                if (JwtUtil.CLIENT_TYPE_APP.equals(clientType)) {
                    sysUserService.logout(tokenJti);
                    log.info("App用户安全退出：tokenJti={}", tokenJti);
                }
            } catch (Exception e) {
                // Token 无效或已过期，仍然返回成功（幂等退出）
                log.debug("App退出时Token已失效，跳过服务端撤销");
            }
        }
        return Result.success();
    }
}
