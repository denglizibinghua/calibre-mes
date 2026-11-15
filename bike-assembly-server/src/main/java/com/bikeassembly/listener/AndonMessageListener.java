package com.bikeassembly.listener;

import com.bikeassembly.constant.MessageQueueConstant;
import com.bikeassembly.dto.message.AndonMessageDTO;
import com.bikeassembly.entity.AndonAlert;
import com.bikeassembly.mapper.AndonAlertMapper;
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
 * 安灯告警消息消费者。
 * <p>
 * 真实业务消费：tryConsume 幂等占位成功后，查询安灯最新状态并推送 WebSocket 通知
 * （管理端全量 + 上报人/处理人 app 端），重复投递只执行一次。
 * <p>
 * 由 {@code app.rabbitmq.enabled} 控制是否加载。
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true")
public class AndonMessageListener {

    private final MessageConsumeLogService consumeLogService;
    private final AndonAlertMapper andonAlertMapper;
    private final WebSocketNotifier webSocketNotifier;

    @RabbitListener(queues = MessageQueueConstant.ANDON_QUEUE)
    public void handleAndonMessage(AndonMessageDTO message) {
        log.info(">>> [安灯消息] 收到消息: alertId={}, alertType={}, level={}, eventType={}, time={}",
                message.getAlertId(),
                message.getAlertType(),
                message.getLevel(),
                message.getEventType(),
                message.getCreateTime());
        boolean first = consumeLogService.tryConsume("ANDON", message.getAlertId(), null,
                message.getEventType(), message.getCreateTime(), message);
        if (!first) {
            log.debug("重复安灯消息，跳过业务处理: alertId={}, eventType={}",
                    message.getAlertId(), message.getEventType());
            return;
        }

        AndonAlert alert = andonAlertMapper.selectById(message.getAlertId());
        if (alert == null) {
            log.warn("安灯消息对应告警不存在，跳过推送: alertId={}", message.getAlertId());
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("alertId", alert.getAlertId());
        data.put("alertNo", alert.getAlertNo());
        data.put("alertType", alert.getAlertType());
        data.put("alertLevel", alert.getAlertLevel());
        data.put("status", alert.getStatus());
        data.put("workOrderId", alert.getWorkOrderId());
        data.put("unitId", alert.getUnitId());
        data.put("equipmentId", alert.getEquipmentId());
        data.put("reporterUserId", alert.getReporterUserId());
        data.put("handlerUserId", alert.getHandlerUserId());
        data.put("escalationStatus", alert.getEscalationStatus());
        webSocketNotifier.pushToAdmin(WsMessageDTO.BIZ_ANDON, message.getEventType(), data);
        if (alert.getReporterUserId() != null) {
            webSocketNotifier.pushToUser(alert.getReporterUserId(), WsMessageDTO.BIZ_ANDON,
                    message.getEventType(), data);
        }
        if (alert.getHandlerUserId() != null) {
            webSocketNotifier.pushToUser(alert.getHandlerUserId(), WsMessageDTO.BIZ_ANDON,
                    message.getEventType(), data);
        }
    }
}
