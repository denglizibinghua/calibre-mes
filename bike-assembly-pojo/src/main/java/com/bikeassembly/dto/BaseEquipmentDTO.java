package com.bikeassembly.dto;

import com.bikeassembly.validation.OnCreate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class BaseEquipmentDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 设备ID */
    private Long equipmentId;
    /** 设备编码 */
    @NotBlank(message = "设备编码不能为空", groups = OnCreate.class)
    private String equipmentCode;
    /** 设备名称 */
    @NotBlank(message = "设备名称不能为空", groups = OnCreate.class)
    private String equipmentName;
    /** 所属生产单元 */
    private Long unitId;
    /** 规格型号 */
    private String model;
    /** 设备状态: 0=启用 1=禁用 */
    @Size(max = 1, message = "设备状态长度不能超过1")
    private String status;
    /** 设备运行状态: 0=可运行 1=不可运行(故障/维修中/保养中) */
    @Size(max = 1, message = "运行状态长度不能超过1")
    private String runningStatus;
    /** 创建时间 */
    private LocalDateTime createTime;
}
