package com.bikeassembly.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 微信绑定响应 VO — 绑定/注册成功后返回双 Token。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WxBindVO implements Serializable {

    private static final long serialVersionUID = 1L;

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

    /** 权限码列表 */
    private List<String> permissions;

    /** 数据范围 */
    private String dataScope;
}
