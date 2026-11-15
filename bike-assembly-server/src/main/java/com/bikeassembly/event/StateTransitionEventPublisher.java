package com.bikeassembly.event;

import com.bikeassembly.dto.message.WorkOrderMessageDTO;
import com.bikeassembly.service.MessageProducerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

/**
 * 状态转换事件发布器。
 * <p>
 * 在事务内发布 Spring ApplicationEvent，
 * 由 {@code @TransactionalEventListener(phase = AFTER_COMMIT)} 消费并发送 MQ。
 * 保证：事务回滚不发送消息，发送失败不回滚业务事务。
 * </p>
 */
@Component
@Slf4j
public class StateTransitionEventPublisher {

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    private ObjectProvider<MessageProducerService> messageProducerProvider;

    /**
     * 在事务内发布状态转换事件。
     */
    public void publish(StateTransitionEvent event) {
        applicationEventPublisher.publishEvent(event);
        log.debug("发布状态转换事件：eventType={}, aggregateId={}", event.getEventType(), event.getAggregateId());
    }

    /**
     * 事务提交后消费事件，发送 MQ 消息。
     * 发送失败只记日志，不回滚已提交的业务事务。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleStateTransitionEvent(StateTransitionEvent event) {
        try {
            MessageProducerService producer = messageProducerProvider.getIfAvailable();
            if (producer == null) {
                log.debug("RabbitMQ 未启用，跳过状态转换消息：eventId={}", event.getEventId());
                return;
            }

            WorkOrderMessageDTO message = WorkOrderMessageDTO.builder()
                    .workOrderId(event.getWorkOrderId())
                    .eventType(event.getEventType())
                    .createTime(LocalDateTime.now())
                    .build();

            producer.sendWorkOrderMessage(message);
            log.info("状态转换事件已发送 MQ：eventId={}, eventType={}, aggregateId={}",
                    event.getEventId(), event.getEventType(), event.getAggregateId());
        } catch (Exception e) {
            log.warn("状态转换事件 MQ 发送失败（不影响已提交事务）：eventId={}, eventType={}, error={}",
                    event.getEventId(), event.getEventType(), e.getMessage(), e);
        }
    }
}
