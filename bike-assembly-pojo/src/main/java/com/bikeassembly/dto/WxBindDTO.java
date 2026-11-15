package com.bikeassembly.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 微信账号绑定请求 DTO。
 */
@Data
public class WxBindDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 绑定类型：
     * "existing" — 绑定已有数据库账号（需提供 username + password）
     * 不再支持自行注册或选择角色，身份沿用已有系统账号
     */
    private String bindType;

    // ========== bindType = "existing" ==========

    /** 已有账号的用户名 */
    private String username;

    /** 已有账号的密码 */
    @lombok.ToString.Exclude
    private String password;

    // ========== bindType = "new" ==========

    /** 新账号的昵称 */
    private String nickname;

    /** 新账号的角色 ID */
    private Long roleId;
}
