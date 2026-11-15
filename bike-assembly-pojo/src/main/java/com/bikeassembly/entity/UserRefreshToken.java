package com.bikeassembly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_refresh_token")
public class UserRefreshToken implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户ID，关联 sys_user.user_id */
    @TableField("user_id")
    private Long userId;

    /**
     * [Token] 客户端类型：admin（管理端）或 app（小程序端）
     * 同一用户可在两个端分别拥有独立的 Refresh Token
     */
    @TableField("client_type")
    private String clientType;

    /**
     * [Token] Token 唯一标识（JWT 的 jti claim）
     * 用于 Token 撤销和重放攻击检测
     */
    @TableField("token_jti")
    private String tokenJti;

    /**
     * [Token] Refresh Token 的 SHA-256 哈希值
     * 数据库只存哈希不存明文，防止数据库泄露导致 Token 被盗用
     */
    @TableField("token_hash")
    private String tokenHash;

    /** Token 过期时间 */
    @TableField("expires_at")
    private LocalDateTime expiresAt;

    /**
     * [状态判断] 是否已撤销。
     * 0 = 有效，1 = 已撤销（退出登录/Token 轮换/账号禁用时设置）
     */
    @TableField("revoked")
    private Integer revoked;

    /** 撤销时间 */
    @TableField("revoked_at")
    private LocalDateTime revokedAt;

    /**
     * [Token] 替换此 Token 的新 Token 的 JTI
     * Token Rotation 机制：旧 Token 被撤销时记录新 Token 的 JTI，形成替换链
     * 用于重放攻击检测
     */
    @TableField("replaced_by_jti")
    private String replacedByJti;

    /** 设备信息（预留字段） */
    @TableField("device_info")
    private String deviceInfo;

    /** 登录 IP 地址（预留字段） */
    @TableField("ip_address")
    private String ipAddress;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;
}
