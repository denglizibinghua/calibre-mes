package com.bikeassembly.event;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 状态转换领域事件。
 * 在事务内发布，由 {@code @TransactionalEventListener(phase = AFTER_COMMIT)}
 * 消费后发送 MQ 消息，保证事务提交后才发送。
 */
@Getter
@Builder
@ToString
public class StateTransitionEvent {

    /** 事件唯一 ID */
    @Builder.Default
    private final String eventId = UUID.randomUUID().toString();

    /** 事件类型，如 WORK_ORDER_RELEASED */
    private final String eventType;

    /** 聚合类型：WORK_ORDER / WORK_PROCESS / INSPECTION */
    private final String aggregateType;

    /** 聚合 ID */
    private final Long aggregateId;

    /** 关联工单 ID */
    private final Long workOrderId;

    /** 关联工序 ID */
    private final Long workProcessId;

    /** 变更前状态码 */
    private final String oldStatus;

    /** 变更后状态码 */
    private final String newStatus;

    /** 操作人 ID */
    private final Long operatorId;

    /** 操作人姓名 */
    private final String operatorName;

    /** 请求幂等键 */
    private final String requestId;

    /** 发生时间 */
    @Builder.Default
    private final LocalDateTime occurredAt = LocalDateTime.now();

    /** 消息体版本 */
    @Builder.Default
    private final String payloadVersion = "1.0";

    /** 备注 */
    private final String remark;
}
