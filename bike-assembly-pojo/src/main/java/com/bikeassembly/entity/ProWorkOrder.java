package com.bikeassembly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
@TableName("pro_work_order")
public class ProWorkOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 工单ID（自增主键） */
    @TableId(value = "work_order_id", type = IdType.AUTO)
    private Long workOrderId;
    /** 工单编号（唯一） */
    @TableField("work_order_no")
    private String workOrderNo;
    /** 订单号（关联 ERP 订单） */
    @TableField("order_no")
    private String orderNo;
    /** 产品ID（关联 base_item.item_id） */
    @TableField("item_id")
    private Long itemId;
    /** 计划生产数量 */
    @TableField("plan_qty")
    private BigDecimal planQty;
    /** 单位（如"台"、"件"） */
    @TableField("unit")
    private String unit;
    /** 计划开始时间 */
    @TableField("plan_start_time")
    private LocalDateTime planStartTime;
    /** 计划结束时间 */
    @TableField("plan_end_time")
    private LocalDateTime planEndTime;
    /** 派工单元 ID（关联 base_work_unit） */
    @TableField("work_unit_id")
    private Long workUnitId;
    /** 工艺路线 ID（关联 base_route） */
    @TableField("route_id")
    private Long routeId;
    /** 指派执行人 ID（关联 sys_user.user_id，null 表示未指派） */
    @TableField("assignee_user_id")
    private Long assigneeUserId;
    /**
     * [状态判断] 工单状态（CHAR(1)）。
     * 取值见 WorkOrderStatus 枚举：0=已创建, 1=生产中, 2=已完工, 3=已关闭, 4=已下达, 5=已暂停, 6=已取消
     */
    @TableField("status")
    private String status;
    /** 请求幂等键（预留，用于防止重复提交） */
    @TableField("request_id")
    private String requestId;
    /**
     * [并发控制] 乐观锁版本号。
     * MyBatis-Plus @Version 注解：每次 updateById 时自动在 WHERE 中追加 version = #{version}，
     * 并将 version 值 +1。更新影响行数为 0 时表示并发冲突。
     */
    @TableField("version")
    @com.baomidou.mybatisplus.annotation.Version
    private Integer version;
    /** 实际开始时间 */
    @TableField("actual_start_time")
    private LocalDateTime actualStartTime;
    /** 实际完工时间 */
    @TableField("actual_end_time")
    private LocalDateTime actualEndTime;
    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;
}
