package com.bikeassembly.vo;

import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class BaseEquipmentVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 设备ID */
    private Long equipmentId;
    /** 设备编码 */
    private String equipmentCode;
    /** 设备名称 */
    private String equipmentName;
    /** 所属生产单元 */
    private Long unitId;
    /** 规格型号 */
    private String model;
    /** 设备状态: 0=启用 1=禁用 */
    private String status;
    /** 设备运行状态: 0=可运行 1=不可运行 */
    private String runningStatus;
    /** 创建时间 */
    private LocalDateTime createTime;
}
