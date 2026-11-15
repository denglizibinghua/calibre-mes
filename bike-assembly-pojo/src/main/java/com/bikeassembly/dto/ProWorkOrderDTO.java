package com.bikeassembly.dto;

import com.bikeassembly.validation.OnCreate;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class ProWorkOrderDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 工单ID */
    private Long workOrderId;
    /** 工单编号 */
    @NotBlank(message = "工单号不能为空", groups = OnCreate.class)
    private String workOrderNo;
    /** 订单号 */
    private String orderNo;
    /** 产品ID */
    @NotNull(message = "物料不能为空", groups = OnCreate.class)
    @Positive(message = "物料ID必须大于0", groups = OnCreate.class)
    private Long itemId;
    /** 计划数量 */
    @NotNull(message = "计划数量不能为空", groups = OnCreate.class)
    @Positive(message = "计划数量必须大于0", groups = OnCreate.class)
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
    /** 工单状态 */
    @Size(max = 1, message = "状态长度不能超过1")
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
