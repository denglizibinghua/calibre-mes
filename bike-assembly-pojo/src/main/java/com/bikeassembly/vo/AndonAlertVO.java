package com.bikeassembly.vo;

import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class AndonAlertVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 告警ID */
    private Long alertId;
    /** 告警编号 */
    private String alertNo;
    /** 工单ID */
    private Long workOrderId;
    /** 生产单元ID */
    private Long unitId;
    /** 设备ID */
    private Long equipmentId;
    /** 告警类型 */
    private String alertType;
    /** 告警等级 */
    private String alertLevel;
    /** 状态 */
    private String status;
    /** 上报人ID */
    private Long reporterUserId;
    /** 处理人ID */
    private Long handlerUserId;
    /** 告警时间 */
    private LocalDateTime createTime;
    /** 处理时间 */
    private LocalDateTime handleTime;
    /** 升级状态：0=未升级 1=已升级 */
    private String escalationStatus;
    /** 升级人ID */
    private Long escalatedBy;
    /** 升级时间 */
    private LocalDateTime escalationTime;
    /** 升级备注 */
    private String escalationRemark;
}
