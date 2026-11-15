package com.bikeassembly.dto;

import com.bikeassembly.validation.OnCreate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class AndonAlertDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 告警ID */
    private Long alertId;
    /** 告警编号 */
    @NotBlank(message = "告警编号不能为空", groups = OnCreate.class)
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
    @Size(max = 1, message = "告警等级长度不能超过1")
    private String alertLevel;
    /** 状态 */
    @Size(max = 1, message = "状态长度不能超过1")
    private String status;
    /** 上报人ID */
    private Long reporterUserId;
    /** 处理人ID */
    private Long handlerUserId;
    /** 告警时间 */
    private LocalDateTime createTime;
    /** 处理时间 */
    private LocalDateTime handleTime;
}
