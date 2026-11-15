package com.bikeassembly.service.impl;

import com.bikeassembly.constant.MessageQueueConstant;
import com.bikeassembly.dto.message.AndonMessageDTO;
import com.bikeassembly.dto.message.EquipmentMessageDTO;
import com.bikeassembly.dto.message.ErpSyncMessageDTO;
import com.bikeassembly.dto.message.InventoryMessageDTO;
import com.bikeassembly.dto.message.QualityMessageDTO;
import com.bikeassembly.dto.message.WorkOrderMessageDTO;
import com.bikeassembly.service.MessageProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * [答辩入口] RabbitMQ 消息生产者。
 * [消息发送] 使用 RabbitTemplate.convertAndSend 发送消息到 Topic Exchange（bike.mes.exchange）
 * [核心业务] 5 类消息：工单(WORK_ORDER_KEY)、库存(INVENTORY_KEY)、质检(QUALITY_KEY)、设备(EQUIPMENT_KEY)、安灯(ANDON_KEY)
 * [实现情况] @ConditionalOnProperty 控制：app.rabbitmq.enabled=false 时该 Bean 不会被加载，ProWorkOrderServiceImpl 中有安全降级（发送失败不影响主业务）
 * [调用方] ProWorkOrderServiceImpl.saveProWorkOrder（工单创建后发送）
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true")
public class MessageProducerServiceImpl implements MessageProducerService {

    private final RabbitTemplate rabbitTemplate;

    /** [消息发送] 发送工单消息：Exchange=bike.mes.exchange, RoutingKey=bike.mes.work.order */
    @Override
    public void sendWorkOrderMessage(WorkOrderMessageDTO message) {
        log.info("发送工单消息 -> exchange={}, key={}, payload={}",
                MessageQueueConstant.EXCHANGE, MessageQueueConstant.WORK_ORDER_KEY, message);
        rabbitTemplate.convertAndSend(
                MessageQueueConstant.EXCHANGE,
                MessageQueueConstant.WORK_ORDER_KEY,
                message
        );
    }

    @Override
    public void sendInventoryMessage(InventoryMessageDTO message) {
        log.info("发送库存消息 -> exchange={}, key={}, payload={}",
                MessageQueueConstant.EXCHANGE, MessageQueueConstant.INVENTORY_KEY, message);
        rabbitTemplate.convertAndSend(
                MessageQueueConstant.EXCHANGE,
                MessageQueueConstant.INVENTORY_KEY,
                message
        );
    }

    @Override
    public void sendQualityMessage(QualityMessageDTO message) {
        log.info("发送质检消息 -> exchange={}, key={}, payload={}",
                MessageQueueConstant.EXCHANGE, MessageQueueConstant.QUALITY_KEY, message);
        rabbitTemplate.convertAndSend(
                MessageQueueConstant.EXCHANGE,
                MessageQueueConstant.QUALITY_KEY,
                message
        );
    }

    @Override
    public void sendEquipmentMessage(EquipmentMessageDTO message) {
        log.info("发送设备消息 -> exchange={}, key={}, payload={}",
                MessageQueueConstant.EXCHANGE, MessageQueueConstant.EQUIPMENT_KEY, message);
        rabbitTemplate.convertAndSend(
                MessageQueueConstant.EXCHANGE,
                MessageQueueConstant.EQUIPMENT_KEY,
                message
        );
    }

    @Override
    public void sendAndonMessage(AndonMessageDTO message) {
        log.info("发送安灯消息 -> exchange={}, key={}, payload={}",
                MessageQueueConstant.EXCHANGE, MessageQueueConstant.ANDON_KEY, message);
        rabbitTemplate.convertAndSend(
                MessageQueueConstant.EXCHANGE,
                MessageQueueConstant.ANDON_KEY,
                message
        );
    }

    @Override
    public void sendErpSyncMessage(ErpSyncMessageDTO message) {
        log.info("发送ERP同步消息 -> exchange={}, key={}, payload={}",
                MessageQueueConstant.EXCHANGE, MessageQueueConstant.ERP_KEY, message);
        rabbitTemplate.convertAndSend(
                MessageQueueConstant.EXCHANGE,
                MessageQueueConstant.ERP_KEY,
                message
        );
    }
}
