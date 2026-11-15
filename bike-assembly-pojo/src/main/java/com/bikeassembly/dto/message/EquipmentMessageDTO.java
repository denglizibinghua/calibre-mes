package com.bikeassembly.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 设备维修消息 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EquipmentMessageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 设备ID */
    private Long equipmentId;
    /** 设备编码 */
    private String equipmentCode;
    /** 维修单ID（维修状态变更消息携带，纯设备消息为空） */
    private Long repairId;
    /** 维修单状态（0=待维修 1=维修中 2=已完成） */
    private String status;
    /** 事件类型：FAULT / REPAIR / MAINTAIN */
    private String eventType;
    /** 事件时间 */
    private LocalDateTime createTime;
}
