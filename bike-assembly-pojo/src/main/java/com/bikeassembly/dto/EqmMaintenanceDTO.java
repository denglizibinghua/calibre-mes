package com.bikeassembly.dto;

import com.bikeassembly.validation.OnCreate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class EqmMaintenanceDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 维护ID */
    private Long maintenanceId;
    /** 设备ID */
    @NotNull(message = "设备ID不能为空", groups = OnCreate.class)
    private Long equipmentId;
    /** 维护类型(CHAR(1)) */
    @NotBlank(message = "维护类型不能为空", groups = OnCreate.class)
    @Size(max = 1, message = "维护类型长度不能超过1")
    private String maintenanceType;
    /** 计划时间 */
    private LocalDateTime planTime;
    /** 完成时间 */
    private LocalDateTime finishTime;
    /** 责任人 */
    private String responsibleUser;
    /** 状态(CHAR(1)) */
    @Size(max = 1, message = "状态长度不能超过1")
    private String status;
    /** 备注 */
    private String remark;
}
