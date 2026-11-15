package com.bikeassembly.dto;

import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class SysOperLogDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 日志ID */
    private Long logId;
    /** 模块 */
    private String module;
    /** 操作类型 */
    private String operType;
    /** 操作人 */
    private String operUser;
    /** IP地址 */
    private String operIp;
    /** 请求方式 */
    private String requestMethod;
    /** 状态 */
    private String status;
    /** 操作时间 */
    private LocalDateTime operTime;
    /** 错误信息 */
    private String errorMsg;
}
