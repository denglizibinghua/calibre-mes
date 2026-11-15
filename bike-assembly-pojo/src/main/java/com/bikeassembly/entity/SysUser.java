package com.bikeassembly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
@TableName("sys_user")
public class SysUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户ID（自增主键） */
    @TableId(value = "user_id", type = IdType.AUTO)
    private Long userId;
    /** 登录账号（唯一） */
    @TableField("username")
    private String username;
    /** 密码密文（BCrypt 加密，数据库中不存明文） */
    @TableField("password")
    private String password;
    /** 用户姓名/昵称 */
    @TableField("nickname")
    private String nickname;
    /** 手机号 */
    @TableField("phone")
    private String phone;
    /** 角色ID，关联 sys_role.role_id */
    @TableField("role_id")
    private Long roleId;
    /** 微信 openid（小程序用户唯一标识） */
    @TableField("openid")
    private String openid;
    /** 微信头像 URL */
    @TableField("avatar")
    private String avatar;
    /**
     * [状态判断] 账号状态。
     * "0" = 正常启用，"1" = 已禁用
     */
    @TableField("status")
    private String status;
    /** 密码最后修改时间 */
    @TableField("password_changed_at")
    private LocalDateTime passwordChangedAt;
    /**
     * [业务规则] 密码过期时间。
     * 根据系统配置 password.expire_days 计算，默认 90 天。
     * null 表示永不过期。
     */
    @TableField("password_expire_at")
    private LocalDateTime passwordExpireAt;
    /**
     * [状态判断] 是否必须修改密码。
     * 1 = 首次登录/管理员重置后必须修改密码，0 = 不需要。
     * 拦截器检查此字段，为 1 时只允许访问修改密码和退出接口。
     */
    @TableField("must_change_password")
    private Integer mustChangePassword;
    /**
     * [业务规则] 连续登录失败次数。
     * 登录成功时重置为 0，失败时 +1。
     * 达到 5 次（MAX_FAIL_COUNT）后锁定账号。
     */
    @TableField("login_fail_count")
    private Integer loginFailCount;
    /**
     * [状态判断] 账号锁定截止时间。
     * 登录失败 5 次后设置为 now + 15 分钟。
     * null 或小于当前时间表示未锁定。
     */
    @TableField("locked_until")
    private LocalDateTime lockedUntil;
    /** 最后登录时间 */
    @TableField("last_login_time")
    private LocalDateTime lastLoginTime;
    /** 最后登录 IP 地址 */
    @TableField("last_login_ip")
    private String lastLoginIp;
    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;
    /** 更新时间 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
