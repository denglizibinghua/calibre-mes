package com.bikeassembly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
@TableName("sys_oper_log")
public class SysOperLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 日志ID */
    @TableId(value = "log_id", type = IdType.AUTO)
    private Long logId;
    /** 模块 */
    @TableField("module")
    private String module;
    /** 操作类型 */
    @TableField("oper_type")
    private String operType;
    /** 操作人 */
    @TableField("oper_user")
    private String operUser;
    /** 操作用户ID */
    @TableField("oper_user_id")
    private Long operUserId;
    /** IP地址 */
    @TableField("oper_ip")
    private String operIp;
    /** 请求方式 */
    @TableField("request_method")
    private String requestMethod;
    /** 状态 */
    @TableField("status")
    private String status;
    /** 操作时间 */
    @TableField("oper_time")
    private LocalDateTime operTime;
    /** 错误信息 */
    @TableField("error_msg")
    private String errorMsg;
}
