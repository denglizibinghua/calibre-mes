package com.bikeassembly.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 当前用户信息 VO — /app/user/me 接口返回。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long userId;
    /** 登录账号 */
    private String username;
    /** 用户姓名 */
    private String nickname;
    /** 角色编码列表 */
    private List<String> roles;
    /** 权限编码列表 */
    private List<String> permissions;
    /** 数据范围：SELF / ALL */
    private String dataScope;
    /** 是否必须修改密码 */
    private Integer mustChangePassword;
    /** 密码是否已过期 */
    private Boolean passwordExpired;
}
