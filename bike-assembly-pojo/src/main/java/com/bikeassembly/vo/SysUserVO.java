package com.bikeassembly.vo;

import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class SysUserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long userId;
    /** 登录账号 */
    private String username;
    /** 用户姓名 */
    private String nickname;
    /** 手机号 */
    private String phone;
    /** 角色ID */
    private Long roleId;
    /** 微信openid */
    private String openid;
    /** 微信头像 */
    private String avatar;
    /** 状态 */
    private String status;
    /** 创建时间 */
    private LocalDateTime createTime;
}
