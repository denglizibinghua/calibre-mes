package com.bikeassembly.controller.admin;

import com.bikeassembly.context.BaseContext;
import com.bikeassembly.dto.UserLoginDTO;
import com.bikeassembly.dto.FaceLoginDTO;
import com.bikeassembly.dto.FaceTemplateDTO;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.RefreshTokenService;
import com.bikeassembly.service.SysMenuService;
import com.bikeassembly.service.SysUserService;
import com.bikeassembly.service.FaceAuthService;
import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.PermissionConstant;
import com.bikeassembly.utils.JwtUtil;
import com.bikeassembly.vo.SysMenuVO;
import com.bikeassembly.vo.UserLoginVO;
import com.bikeassembly.vo.UserMenusVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * [答辩入口] 管理端用户认证控制器。
 * 基础路径：/admin/user
 * 负责登录、退出、Token 刷新和菜单获取，不包含用户 CRUD（用户 CRUD 在 SysUserController）。
 */
@RestController
@RequestMapping("/admin/user")
@Slf4j
public class UserController {

    @Autowired
    private SysUserService sysUserService;
    @Autowired
    private SysMenuService sysMenuService;
    @Autowired
    private FaceAuthService faceAuthService;

    /**
     * [答辩入口] 管理端登录接口。
     * 完整路径：POST /admin/user/login
     * [请求参数] UserLoginDTO（JSON body）：username + password
     * [调用链] Controller → SysUserService.login → SysUserMapper.findByUsername → BCrypt 校验 → JwtUtil.createAdminAccessToken/createAdminRefreshToken
     * [前端调用] 管理端登录页 login/index.vue 的 handleLogin() 直接调用 axios.post('/admin/user/login', form)
     * [实现情况] 登录页面直接使用 axios，没有经过统一 API 封装文件
     */
    @PostMapping("/login")
    public Result<UserLoginVO> login(@RequestBody UserLoginDTO dto) {
        log.info("用户登录：{}", dto.getUsername());
        // [调用链] Controller 不处理登录业务，全部转交 SysUserService.login
        UserLoginVO vo = sysUserService.login(dto);
        return Result.success(vo);
    }

    @PostMapping("/face-login")
    public Result<UserLoginVO> faceLogin(@RequestBody @Valid FaceLoginDTO dto) {
        return Result.success(faceAuthService.login(dto, JwtUtil.CLIENT_TYPE_ADMIN));
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

    @RequirePermission(PermissionConstant.ADMIN_USER_MANAGE)
    @PostMapping("/{userId}/face-template")
    public Result<Void> registerUserFace(@org.springframework.web.bind.annotation.PathVariable Long userId,
                                         @RequestBody @Valid FaceTemplateDTO dto) {
        faceAuthService.register(userId, dto);
        return Result.success();
    }

    @RequirePermission(PermissionConstant.ADMIN_USER_MANAGE)
    @org.springframework.web.bind.annotation.DeleteMapping("/{userId}/face-template")
    public Result<Void> revokeUserFace(@org.springframework.web.bind.annotation.PathVariable Long userId) {
        faceAuthService.revoke(userId);
        return Result.success();
    }

    /**
     * 安全退出接口。
     * POST /admin/user/logout
     *
     * 接收 Refresh Token（通过 header 或 query param）并撤销其 JTI。
     * 不使用 Access Token JTI（数据库存储的是 Refresh Token JTI）。
     *
     * Access Token 在剩余短有效期内仍然可用（本系统不实现 Access Token 黑名单）。
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestParam(required = false) String refreshToken,
                                @RequestHeader(value = "X-Refresh-Token", required = false) String refreshTokenHeader) {
        String rt = refreshTokenHeader != null ? refreshTokenHeader : refreshToken;
        if (rt == null || rt.isBlank()) {
            throw new BaseException("请提供 Refresh Token");
        }
        sysUserService.logout(rt);
        log.info("用户安全退出：userId={}", BaseContext.getCurrentId());
        return Result.success();
    }

    /**
     * [答辩入口] Access Token 刷新接口（Token Rotation 机制）。
     * 完整路径：POST /admin/user/refresh-token
     * [请求参数] Refresh Token 通过请求头 token 传入（而非请求体）
     * [调用链] Controller → SysUserService.refreshToken → RefreshTokenService.rotateRefreshToken
     * [核心业务] 验证旧 Refresh Token → 撤销旧 Token → 签发新 Access Token + 新 Refresh Token（轮换）
     * [前端调用] request.ts 响应拦截器在收到 401 时自动调用此接口
     * [实现情况] 该接口在 JWT 拦截器中已排除（excludePathPatterns），不需要 Access Token 即可访问
     */
    @PostMapping("/refresh-token")
    public Result<Map<String, Object>> refreshToken(HttpServletRequest request) {
        String refreshToken = request.getHeader("token");
        if (refreshToken == null || refreshToken.isBlank()) {
            return Result.error("缺少 Refresh Token");
        }

        RefreshTokenService.RefreshResult result =
                sysUserService.refreshToken(refreshToken, JwtUtil.CLIENT_TYPE_ADMIN);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accessToken", result.accessToken());
        data.put("refreshToken", result.refreshToken());
        data.put("tokenType", "Bearer");
        data.put("expiresIn", result.expiresIn());

        log.info("Admin Token 刷新成功");
        return Result.success(data);
    }

    /**
     * [答辩入口] 获取当前用户的菜单树和权限码列表。
     * 完整路径：GET /admin/user/menus
     * [调用链] Controller → SysMenuService.getCurrentUserMenuTree / getUserPermissionCodes
     * [前端调用] 路由守卫中调用，用于动态生成侧边栏菜单和注册路由
     * [权限控制] 根据 userId 从 sys_role_menu 关联表查询该角色拥有的菜单和权限码
     */
    @GetMapping("/menus")
    public Result<UserMenusVO> getCurrentUserMenus() {
        Long userId = BaseContext.getCurrentId();
        List<SysMenuVO> menus = sysMenuService.getCurrentUserMenuTree(userId);
        List<String> permissions = sysMenuService.getUserPermissionCodes(userId);
        UserMenusVO vo = UserMenusVO.builder()
                .menus(menus)
                .permissions(permissions)
                .build();
        return Result.success(vo);
    }
}
