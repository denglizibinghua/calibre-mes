package com.bikeassembly.listener;

import com.bikeassembly.constant.MessageQueueConstant;
import com.bikeassembly.dto.message.WorkOrderMessageDTO;
import com.bikeassembly.entity.ProWorkOrder;
import com.bikeassembly.mapper.ProWorkOrderMapper;
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
 * [消息消费] 工单消息消费者。
 * [实现情况] 真实业务消费：tryConsume 幂等占位成功后，查询工单最新状态并推送 WebSocket
 *   通知（管理端全量），重复投递只执行一次。
 *   消息链路：ProWorkOrderServiceImpl.saveProWorkOrder → MessageProducerService.sendWorkOrderMessage
 *     → Exchange(bike.mes.exchange) → RoutingKey(bike.mes.work.order) → Queue(bike.mes.work.order.queue)
 *     → WorkOrderMessageListener.handleWorkOrderMessage
 * RabbitMQ 配置由 @ConditionalOnProperty 控制：app.rabbitmq.enabled=true 时才加载。
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true")
public class WorkOrderMessageListener {

    private final MessageConsumeLogService consumeLogService;
    private final ProWorkOrderMapper proWorkOrderMapper;
    private final WebSocketNotifier webSocketNotifier;

    @RabbitListener(queues = MessageQueueConstant.WORK_ORDER_QUEUE)
    public void handleWorkOrderMessage(WorkOrderMessageDTO message) {
        log.info(">>> [工单消息] 收到消息: workOrderId={}, orderNo={}, eventType={}, planQty={}, time={}",
                message.getWorkOrderId(),
                message.getOrderNo(),
                message.getEventType(),
                message.getPlanQty(),
                message.getCreateTime());
        boolean first = consumeLogService.tryConsume("WORK_ORDER", message.getWorkOrderId(),
                message.getWorkOrderId(), message.getEventType(), message.getCreateTime(), message);
        if (!first) {
            log.debug("重复工单消息，跳过业务处理: workOrderId={}, eventType={}",
                    message.getWorkOrderId(), message.getEventType());
            return;
        }

        ProWorkOrder wo = proWorkOrderMapper.selectById(message.getWorkOrderId());
        if (wo == null) {
            log.warn("工单消息对应工单不存在，跳过推送: workOrderId={}", message.getWorkOrderId());
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("workOrderId", wo.getWorkOrderId());
        data.put("workOrderNo", wo.getWorkOrderNo());
        data.put("status", wo.getStatus());
        webSocketNotifier.pushToAdmin(WsMessageDTO.BIZ_WORK_ORDER, message.getEventType(), data);
    }
}
