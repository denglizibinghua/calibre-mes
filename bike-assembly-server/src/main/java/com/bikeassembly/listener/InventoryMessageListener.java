package com.bikeassembly.listener;

import com.bikeassembly.constant.MessageQueueConstant;
import com.bikeassembly.dto.message.InventoryMessageDTO;
import com.bikeassembly.entity.WmsStockDoc;
import com.bikeassembly.mapper.WmsStockDocMapper;
import com.bikeassembly.service.MessageConsumeLogService;
import com.bikeassembly.ws.WebSocketNotifier;
import com.bikeassembly.ws.WsMessageDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 库存变更消息消费者。
 * <p>
 * 真实业务消费：tryConsume 幂等占位成功后，查询单据最新状态并推送 WebSocket 通知
 * （管理端全量，INVENTORY），重复投递只执行一次。
 * <p>
 * 由 {@code app.rabbitmq.enabled} 控制是否加载。
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true")
public class InventoryMessageListener {

    private final MessageConsumeLogService consumeLogService;
    private final WmsStockDocMapper wmsStockDocMapper;
    private final WebSocketNotifier webSocketNotifier;

    @RabbitListener(queues = MessageQueueConstant.INVENTORY_QUEUE)
    public void handleInventoryMessage(InventoryMessageDTO message) {
        log.info(">>> [库存消息] 收到消息: inventoryId={}, itemId={}, locationId={}, quantity={}, eventType={}, time={}",
                message.getInventoryId(),
                message.getItemId(),
                message.getLocationId(),
                message.getQuantity(),
                message.getEventType(),
                message.getCreateTime());
        boolean first = consumeLogService.tryConsume("INVENTORY", message.getInventoryId(), null,
                message.getEventType(), message.getCreateTime(), message);
        if (!first) {
            log.debug("重复库存消息，跳过业务处理: inventoryId={}, eventType={}",
                    message.getInventoryId(), message.getEventType());
            return;
        }

        WmsStockDoc doc = wmsStockDocMapper.selectById(message.getInventoryId());
        if (doc == null) {
            log.warn("库存消息对应单据不存在，跳过推送: docId={}", message.getInventoryId());
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("docId", doc.getDocId());
        data.put("docNo", doc.getDocNo());
        data.put("docType", doc.getDocType());
        data.put("status", doc.getStatus());
        webSocketNotifier.pushToAdmin(WsMessageDTO.BIZ_INVENTORY, message.getEventType(), data);
    }
}
