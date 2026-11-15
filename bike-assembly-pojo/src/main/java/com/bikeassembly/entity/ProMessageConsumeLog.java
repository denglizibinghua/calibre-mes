package com.bikeassembly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息消费记录实体（MQ 消费幂等与审计留痕）。
 * <p>
 * 对应表 pro_message_consume_log，event_id 唯一键保证同一业务事件重复投递只落库一次。
 */
@Data
@TableName("pro_message_consume_log")
public class ProMessageConsumeLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "log_id", type = IdType.AUTO)
    private Long logId;

    /** 事件ID（唯一），由 聚合类型:聚合ID:事件类型:事件时间 生成 */
    @TableField("event_id")
    private String eventId;

    /** 事件类型：CREATE / START / INSPECT / FAULT / ALERT 等 */
    @TableField("event_type")
    private String eventType;

    /** 聚合类型：WORK_ORDER / INSPECTION / INVENTORY / EQUIPMENT / ANDON */
    @TableField("aggregate_type")
    private String aggregateType;

    /** 聚合ID（业务实体主键） */
    @TableField("aggregate_id")
    private Long aggregateId;

    /** 工单ID（可空，仅工单/质检消息有值） */
    @TableField("work_order_id")
    private Long workOrderId;

    /** 消息体快照（JSON） */
    @TableField("message_body")
    private String messageBody;

    /** 消费状态：0=待处理 1=已处理 2=处理失败 */
    @TableField("status")
    private String status;

    /** 消费时间 */
    @TableField("consume_time")
    private LocalDateTime consumeTime;

    /** 备注 */
    @TableField("remark")
    private String remark;

    // ==================== 消费状态常量 ====================

    public static final String STATUS_PENDING = "0";
    public static final String STATUS_HANDLED = "1";
    public static final String STATUS_FAILED = "2";
}
