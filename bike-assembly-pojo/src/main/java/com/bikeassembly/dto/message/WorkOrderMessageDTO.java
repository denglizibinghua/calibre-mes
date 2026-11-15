package com.bikeassembly.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工单消息 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderMessageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 工单ID */
    private Long workOrderId;
    /** 订单号 */
    private String orderNo;
    /** 产品ID */
    private Long itemId;
    /** 计划数量 */
    private BigDecimal planQty;
    /** 事件类型：CREATE / START / FINISH / CANCEL */
    private String eventType;
    /** 事件时间 */
    private LocalDateTime createTime;
}
