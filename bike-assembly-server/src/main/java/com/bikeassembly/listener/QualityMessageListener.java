package com.bikeassembly.listener;

import com.bikeassembly.constant.MessageQueueConstant;
import com.bikeassembly.dto.message.QualityMessageDTO;
import com.bikeassembly.entity.QmsInspection;
import com.bikeassembly.mapper.QmsInspectionMapper;
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
 * 质检消息消费者。
 * <p>
 * 真实业务消费：tryConsume 幂等占位成功后，查询质检单最新状态并推送 WebSocket 通知
 * （管理端全量），重复投递只执行一次。
 * <p>
 * 由 {@code app.rabbitmq.enabled} 控制是否加载。
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true")
public class QualityMessageListener {

    private final MessageConsumeLogService consumeLogService;
    private final QmsInspectionMapper qmsInspectionMapper;
    private final WebSocketNotifier webSocketNotifier;

    @RabbitListener(queues = MessageQueueConstant.QUALITY_QUEUE)
    public void handleQualityMessage(QualityMessageDTO message) {
        log.info(">>> [质检消息] 收到消息: inspectionId={}, workOrderId={}, result={}, eventType={}, time={}",
                message.getInspectionId(),
                message.getWorkOrderId(),
                message.getResult(),
                message.getEventType(),
                message.getCreateTime());
        boolean first = consumeLogService.tryConsume("INSPECTION", message.getInspectionId(),
                message.getWorkOrderId(), message.getEventType(), message.getCreateTime(), message);
        if (!first) {
            log.debug("重复质检消息，跳过业务处理: inspectionId={}, eventType={}",
                    message.getInspectionId(), message.getEventType());
            return;
        }

        QmsInspection inspection = qmsInspectionMapper.selectById(message.getInspectionId());
        if (inspection == null) {
            log.warn("质检消息对应质检单不存在，跳过推送: inspectionId={}", message.getInspectionId());
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("inspectionId", inspection.getInspectionId());
        data.put("inspectionNo", inspection.getInspectionNo());
        data.put("workOrderId", inspection.getWorkOrderId());
        data.put("result", inspection.getResult());
        data.put("status", inspection.getStatus());
        webSocketNotifier.pushToAdmin(WsMessageDTO.BIZ_QUALITY, message.getEventType(), data);
    }
}
