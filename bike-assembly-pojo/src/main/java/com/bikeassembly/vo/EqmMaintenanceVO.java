package com.bikeassembly.vo;

import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class EqmMaintenanceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 维护ID */
    private Long maintenanceId;
    /** 设备ID */
    private Long equipmentId;
    /** 维护类型 */
    private String maintenanceType;
    /** 计划时间 */
    private LocalDateTime planTime;
    /** 完成时间 */
    private LocalDateTime finishTime;
    /** 责任人 */
    private String responsibleUser;
    /** 状态 */
    private String status;
    /** 备注 */
    private String remark;
}
