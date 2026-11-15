package com.bikeassembly.service;

import java.time.LocalDateTime;

/**
 * 消息消费留痕服务。
 * <p>
 * 消费者统一通过 {@link #tryConsume} 获取消费权：首次消费写入 pro_message_consume_log
 * （event_id 唯一键保证），重复投递返回 false 由业务方跳过，实现"重复消息只执行一次"。
 */
public interface MessageConsumeLogService {

    /**
     * 尝试获取消息消费权（消费幂等）。
     *
     * @param aggregateType 聚合类型（WORK_ORDER / INSPECTION / INVENTORY / EQUIPMENT / ANDON）
     * @param aggregateId   聚合ID（业务实体主键）
     * @param workOrderId   关联工单ID（无则传 null）
     * @param eventType     事件类型
     * @param eventTime     事件时间（无则取当前时间）
     * @param messageBody   消息体（序列化为 JSON 快照）
     * @return true=首次消费（业务应继续执行）；false=该事件已消费过（重复投递，业务应跳过）
     */
    boolean tryConsume(String aggregateType, Long aggregateId, Long workOrderId,
                       String eventType, LocalDateTime eventTime, Object messageBody);
}
