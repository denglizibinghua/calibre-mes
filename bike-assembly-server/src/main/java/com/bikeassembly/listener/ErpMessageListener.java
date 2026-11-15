package com.bikeassembly.listener;

import com.bikeassembly.constant.MessageQueueConstant;
import com.bikeassembly.dto.message.ErpSyncMessageDTO;
import com.bikeassembly.service.ErpSyncLogService;
import com.bikeassembly.service.MessageConsumeLogService;
import com.bikeassembly.service.erp.ErpSyncClient;
import com.bikeassembly.service.erp.ErpSyncPayloadService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * ERP 同步消息消费者。
 * <p>
 * 真实业务消费：tryConsume 幂等占位成功后，读取业务快照 → 调用 ErpSyncClient 同步 →
 * 写 erp_sync_log（uk_erp_sync_log_biz 唯一键，同一业务只保留一条同步记录）。
 * 重复投递只执行一次。
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true")
public class ErpMessageListener {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final MessageConsumeLogService consumeLogService;
    private final ErpSyncClient erpSyncClient;
    private final ErpSyncPayloadService erpSyncPayloadService;
    private final ErpSyncLogService erpSyncLogService;

    @RabbitListener(queues = MessageQueueConstant.ERP_QUEUE)
    public void handleErpSyncMessage(ErpSyncMessageDTO message) {
        log.info(">>> [ERP消息] 收到同步消息: bizType={}, bizId={}, direction={}, time={}",
                message.getBizType(), message.getBizId(), message.getDirection(), message.getCreateTime());

        Long aggregateId;
        try {
            aggregateId = Long.valueOf(message.getBizId());
        } catch (NumberFormatException e) {
            log.warn("ERP消息 bizId 非法，跳过：bizId={}", message.getBizId());
            return;
        }
        boolean first = consumeLogService.tryConsume("ERP_SYNC", aggregateId, null,
                message.getEventType(), message.getCreateTime(), message);
        if (!first) {
            log.debug("重复ERP同步消息，跳过业务处理: bizType={}, bizId={}",
                    message.getBizType(), message.getBizId());
            return;
        }

        Map<String, Object> payload = erpSyncPayloadService.buildPayload(
                message.getBizType(), message.getBizId());
        ErpSyncClient.ErpSyncResult result = erpSyncClient.sync(
                new ErpSyncClient.ErpSyncRequest(message.getBizType(), message.getBizId(), payload));

        String status = result.success() ? "2" : "3";
        erpSyncLogService.recordSync(
                message.getBizType(),
                message.getBizId(),
                message.getDirection(),
                status,
                toJson(payload),
                result.responseData());
        log.info("ERP同步完成：bizType={}, bizId={}, status={}", message.getBizType(), message.getBizId(), status);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            return String.valueOf(payload);
        }
    }
}
