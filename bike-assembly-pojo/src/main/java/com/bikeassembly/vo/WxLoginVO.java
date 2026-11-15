package com.bikeassembly.vo;

import com.bikeassembly.dto.RoleOption;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 微信登录响应 VO。
 *
 * status 含义：
 *   1 — 完整登录成功（返回双 Token + 用户信息）
 *   2 — 旧协议保留状态
 *   3 — 未绑定系统账号，仅返回临时凭据，需验证并绑定已有账号
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WxLoginVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 登录状态：1=成功 2=旧协议保留 3=需绑定已有账号 */
    private Integer status;

    // ========== status=1 字段 ==========

    private Long userId;
    private String username;
    private String nickname;
    private Long roleId;

    /** @deprecated 兼容旧小程序，内容始终等于 accessToken */
    private String token;

    /** Access Token（短有效期） */
    private String accessToken;

    /** Refresh Token（长有效期） */
    private String refreshToken;

    /** Token 类型，固定为 "Bearer" */
    private String tokenType;

    /** Access Token 有效期（秒） */
    private Long expiresIn;

    /** Refresh Token 有效期（秒） */
    private Long refreshExpiresIn;

    /** 是否必须修改密码 */
    private Integer mustChangePassword;

    /** 密码是否已过期 */
    private Boolean passwordExpired;

    /** 是否需要绑定账号（status=2,3 时为 true） */
    private Boolean needBind;

    // ========== status=2,3 字段 ==========

    /** 临时 token（用于后续绑定接口鉴权，5 分钟有效） */
    private String tempToken;

    /** 旧协议兼容字段；不再允许自行选择角色，始终为空 */
    private List<RoleOption> availableRoles;

    /** 用户信息（status=1 时返回） */
    private UserInfo userInfo;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long userId;
        private String username;
        private String nickname;
        private Long roleId;
        private List<String> permissions;
        private String dataScope;
    }
}
