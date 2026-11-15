package com.bikeassembly.service.erp;

import com.bikeassembly.dto.message.ErpSyncMessageDTO;
import com.bikeassembly.service.MessageProducerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * ERP 同步事件发布器：业务状态变更时调用，RabbitMQ 启用时发布 ERP 同步消息，
 * 未启用时直接跳过（ERP 集成依赖 MQ）。
 */
@Component
@Slf4j
public class ErpSyncPublisher {

    private final ObjectProvider<MessageProducerService> messageProducerProvider;

    public ErpSyncPublisher(ObjectProvider<MessageProducerService> messageProducerProvider) {
        this.messageProducerProvider = messageProducerProvider;
    }

    /**
     * 发布 ERP 同步消息。
     *
     * @param bizType   业务类型：WORK_ORDER / INVENTORY
     * @param bizId     业务ID
     * @param direction 同步方向：O=出站（MES→ERP）
     */
    public void publish(String bizType, Long bizId, String direction) {
        try {
            MessageProducerService producer = messageProducerProvider.getIfAvailable();
            if (producer == null) {
                log.debug("RabbitMQ 未启用，跳过 ERP 同步消息：bizType={}, bizId={}", bizType, bizId);
                return;
            }
            producer.sendErpSyncMessage(ErpSyncMessageDTO.builder()
                    .bizType(bizType)
                    .bizId(String.valueOf(bizId))
                    .direction(direction)
                    .eventType("SYNC")
                    .createTime(LocalDateTime.now())
                    .build());
            log.info("ERP 同步消息已发布：bizType={}, bizId={}", bizType, bizId);
        } catch (Exception e) {
            log.warn("ERP 同步消息发布失败（不影响主业务）：bizType={}, bizId={}, error={}",
                    bizType, bizId, e.getMessage(), e);
        }
    }
}
