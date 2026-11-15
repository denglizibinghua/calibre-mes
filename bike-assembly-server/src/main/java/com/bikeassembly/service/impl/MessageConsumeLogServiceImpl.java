package com.bikeassembly.service.impl;

import com.bikeassembly.entity.ProMessageConsumeLog;
import com.bikeassembly.mapper.ProMessageConsumeLogMapper;
import com.bikeassembly.service.MessageConsumeLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class MessageConsumeLogServiceImpl implements MessageConsumeLogService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ProMessageConsumeLogMapper consumeLogMapper;
    private final PlatformTransactionManager transactionManager;

    @Override
    public boolean tryConsume(String aggregateType, Long aggregateId, Long workOrderId,
                              String eventType, LocalDateTime eventTime, Object messageBody) {
        LocalDateTime time = eventTime != null ? eventTime : LocalDateTime.now();
        String eventId = buildEventId(aggregateType, aggregateId, eventType, time);
        try {
            ProMessageConsumeLog entity = new ProMessageConsumeLog();
            entity.setEventId(eventId);
            entity.setEventType(eventType);
            entity.setAggregateType(aggregateType);
            entity.setAggregateId(aggregateId);
            entity.setWorkOrderId(workOrderId);
            entity.setMessageBody(toJson(messageBody));
            entity.setStatus(ProMessageConsumeLog.STATUS_HANDLED);
            entity.setConsumeTime(LocalDateTime.now());
            // PostgreSQL 唯一键冲突会中止整个事务，捕获异常后当前事务无法继续使用。
            // 留痕写入放进独立事务：冲突只回滚该子事务，调用方事务与后续语句不受影响。
            TransactionTemplate logTransaction = new TransactionTemplate(transactionManager);
            logTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            logTransaction.executeWithoutResult(ignored -> consumeLogMapper.insert(entity));
            log.info("消息消费留痕成功（首次消费）: eventId={}", eventId);
            return true;
        } catch (DuplicateKeyException e) {
            // event_id 唯一键冲突：同一事件重复投递，消费幂等直接跳过
            log.debug("重复消息，跳过消费留痕: eventId={}", eventId);
            return false;
        } catch (Exception e) {
            // 留痕失败不阻塞业务消费（避免审计组件故障拖垮消息链路），记录告警日志便于排查
            log.warn("消息消费留痕失败（继续执行业务）: eventId={}, error={}", eventId, e.getMessage(), e);
            return true;
        }
    }

    /**
     * 事件 ID 由 聚合类型:聚合ID:事件类型:事件时间 组成，同一业务事件重复投递时稳定不变。
     */
    private String buildEventId(String aggregateType, Long aggregateId,
                                String eventType, LocalDateTime eventTime) {
        return aggregateType + ":" + aggregateId + ":" + eventType + ":" + eventTime;
    }

    private String toJson(Object messageBody) {
        try {
            return OBJECT_MAPPER.writeValueAsString(messageBody);
        } catch (Exception e) {
            return String.valueOf(messageBody);
        }
    }
}
