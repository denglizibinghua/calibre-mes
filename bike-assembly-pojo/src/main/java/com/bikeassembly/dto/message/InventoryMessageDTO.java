package com.bikeassembly.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存变更消息 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryMessageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 库存ID */
    private Long inventoryId;
    /** 物料ID */
    private Long itemId;
    /** 库位ID */
    private Long locationId;
    /** 变更数量 */
    private BigDecimal quantity;
    /** 事件类型：IN / OUT / ADJUST */
    private String eventType;
    /** 事件时间 */
    private LocalDateTime createTime;
}
