package com.bikeassembly.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class ProWorkOrderVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 工单ID */
    private Long workOrderId;
    /** 工单编号 */
    private String workOrderNo;
    /** 订单号 */
    private String orderNo;
    /** 产品ID */
    private Long itemId;
    /** 计划数量 */
    private BigDecimal planQty;
    /** 单位 */
    private String unit;
    /** 计划开始时间 */
    private LocalDateTime planStartTime;
    /** 计划结束时间 */
    private LocalDateTime planEndTime;
    /** 派工单元 */
    private Long workUnitId;
    /** 工艺路线ID */
    private Long routeId;
    /** 指派执行人ID */
    private Long assigneeUserId;
    /** 工单状态 */
    private String status;
    /** 乐观锁版本号 */
    private Integer version;
    /** 实际开始时间 */
    private LocalDateTime actualStartTime;
    /** 实际完工时间 */
    private LocalDateTime actualEndTime;
    /** 创建时间 */
    private LocalDateTime createTime;
}
