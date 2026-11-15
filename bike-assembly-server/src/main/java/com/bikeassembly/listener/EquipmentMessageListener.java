package com.bikeassembly.listener;

import com.bikeassembly.constant.MessageQueueConstant;
import com.bikeassembly.dto.message.EquipmentMessageDTO;
import com.bikeassembly.entity.EqmRepairOrder;
import com.bikeassembly.mapper.EqmRepairOrderMapper;
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
 * 设备维修消息消费者。
 * <p>
 * 真实业务消费：tryConsume 幂等占位成功后，
 *   携带 repairId 的消息 → 查询维修单最新状态并推送 WebSocket 通知（REPAIR）；
 *   纯设备消息（无 repairId）→ 仅留痕与日志（设备台账页暂无实时订阅）。
 * 重复投递只执行一次。
 * <p>
 * 由 {@code app.rabbitmq.enabled} 控制是否加载。
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true")
public class EquipmentMessageListener {

    private final MessageConsumeLogService consumeLogService;
    private final EqmRepairOrderMapper eqmRepairOrderMapper;
    private final WebSocketNotifier webSocketNotifier;

    @RabbitListener(queues = MessageQueueConstant.EQUIPMENT_QUEUE)
    public void handleEquipmentMessage(EquipmentMessageDTO message) {
        log.info(">>> [设备消息] 收到消息: equipmentId={}, equipmentCode={}, eventType={}, time={}",
                message.getEquipmentId(),
                message.getEquipmentCode(),
                message.getEventType(),
                message.getCreateTime());
        boolean first = consumeLogService.tryConsume("EQUIPMENT", message.getEquipmentId(), null,
                message.getEventType(), message.getCreateTime(), message);
        if (!first) {
            log.debug("重复设备消息，跳过业务处理: equipmentId={}, eventType={}",
                    message.getEquipmentId(), message.getEventType());
            return;
        }

        if (message.getRepairId() == null) {
            // 纯设备消息（如设备创建）：无前端实时订阅，留痕即完成
            log.debug("设备消息（无维修单）已消费留痕: equipmentId={}", message.getEquipmentId());
            return;
        }
        EqmRepairOrder repair = eqmRepairOrderMapper.selectById(message.getRepairId());
        if (repair == null) {
            log.warn("设备消息对应维修单不存在，跳过推送: repairId={}", message.getRepairId());
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("repairId", repair.getRepairId());
        data.put("equipmentId", repair.getEquipmentId());
        data.put("status", repair.getStatus());
        data.put("reporterUserId", repair.getReporterUserId());
        data.put("handlerUserId", repair.getHandlerUserId());
        webSocketNotifier.pushToAdmin(WsMessageDTO.BIZ_REPAIR, message.getEventType(), data);
        if (repair.getReporterUserId() != null) {
            webSocketNotifier.pushToUser(repair.getReporterUserId(), WsMessageDTO.BIZ_REPAIR,
                    message.getEventType(), data);
        }
        if (repair.getHandlerUserId() != null) {
            webSocketNotifier.pushToUser(repair.getHandlerUserId(), WsMessageDTO.BIZ_REPAIR,
                    message.getEventType(), data);
        }
    }
}
