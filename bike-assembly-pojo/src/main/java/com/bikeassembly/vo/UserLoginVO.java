package com.bikeassembly.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long id;

    /** 登录账号 */
    private String username;

    /** 用户姓名 */
    private String nickname;

    /** 角色ID */
    private Long roleId;

    /** @deprecated 请使用 accessToken。此字段内容始终等于 accessToken，保留用于向后兼容 */
    private String token;

    /** [Token] Access Token（短有效期 JWT），用于接口鉴权 */
    private String accessToken;

    /** [Token] Refresh Token（长有效期 JWT），用于刷新 Access Token */
    private String refreshToken;

    /** [Token] Token 类型，固定为 "Bearer" */
    private String tokenType;

    /** [Token] Access Token 有效期（秒），从配置文件 bike.jwt.admin.access-token-ttl 读取 */
    private Long expiresIn;

    /** [Token] Refresh Token 有效期（秒），从配置文件 bike.jwt.admin.refresh-token-ttl 读取 */
    private Long refreshExpiresIn;

    /** [状态判断] 是否必须修改密码：1 = 必须修改（首次登录/管理员重置后），0 = 不需要 */
    private Integer mustChangePassword;

    /** [状态判断] 密码是否已过期（超过 password.expire_days 天未修改） */
    private Boolean passwordExpired;

    /** [权限控制] 用户拥有的权限码列表，如 ["system:user:list", "pro:workOrder:add"] */
    private List<String> permissions;

    /**
     * [权限控制] 用户数据范围。
     * "ALL" = 全部数据，"DEPT" = 本部门数据，"SELF" = 仅本人数据
     * 从 sys_role.data_scope 字段读取
     */
    private String dataScope;
}
