package com.bikeassembly.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bikeassembly.AbstractPostgresContainerIntegrationTest;
import com.bikeassembly.constant.MessageQueueConstant;
import com.bikeassembly.dto.message.AndonMessageDTO;
import com.bikeassembly.dto.message.WorkOrderMessageDTO;
import com.bikeassembly.entity.AndonAlert;
import com.bikeassembly.entity.ProMessageConsumeLog;
import com.bikeassembly.entity.ProWorkOrder;
import com.bikeassembly.mapper.AndonAlertMapper;
import com.bikeassembly.mapper.ProMessageConsumeLogMapper;
import com.bikeassembly.mapper.ProWorkOrderMapper;
import com.bikeassembly.ws.WebSocketNotifier;
import com.bikeassembly.ws.WsMessageDTO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * RabbitMQ 真实消费集成测试。
 * <p>
 * 验证：消息经 Exchange/RoutingKey 到达消费者后，
 *   1. tryConsume 幂等占位（pro_message_consume_log 唯一键）只成功一次；
 *   2. 消费者执行业务（WebSocket 推送）只执行一次；
 *   3. 重复投递同一事件被幂等跳过。
 * <p>
 * 使用 Testcontainers RabbitMQ + PostgreSQL，数据库由 init_database.sql + dev_seed_full.sql 提供。
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "app.rabbitmq.enabled=true",
        "spring.rabbitmq.listener.simple.auto-startup=true"
})
@DisplayName("RabbitMQ 真实消费集成测试")
class RabbitMqConsumerIntegrationTest extends AbstractPostgresContainerIntegrationTest {

    @BeforeAll
    static void seedDemoData() {
        executeSql("sql/dev_seed_full.sql");
    }

    @Container
    static final RabbitMQContainer RABBIT =
            new RabbitMQContainer("rabbitmq:3-management");

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private ProMessageConsumeLogMapper consumeLogMapper;
    @Autowired
    private ProWorkOrderMapper proWorkOrderMapper;
    @Autowired
    private AndonAlertMapper andonAlertMapper;

    @SpyBean
    private WebSocketNotifier webSocketNotifier;

    @Test
    @DisplayName("工单消息：首次消费执行并推送 WebSocket，重复投递只执行一次")
    void workOrderMessage_consumedOnceAndPushWebSocket() {
        ProWorkOrder wo = proWorkOrderMapper.selectOne(
                new LambdaQueryWrapper<ProWorkOrder>().last("LIMIT 1"));
        assertNotNull(wo, "本地库需存在工单（seed_production_flow.sql）");

        LocalDateTime eventTime = LocalDateTime.now().withNano(0);
        String eventType = "TEST_RELEASE";
        WorkOrderMessageDTO message = WorkOrderMessageDTO.builder()
                .workOrderId(wo.getWorkOrderId())
                .orderNo(wo.getWorkOrderNo())
                .eventType(eventType)
                .createTime(eventTime)
                .build();

        // 同一事件投递两次（模拟重复投递/重试）
        rabbitTemplate.convertAndSend(MessageQueueConstant.EXCHANGE,
                MessageQueueConstant.WORK_ORDER_KEY, message);
        rabbitTemplate.convertAndSend(MessageQueueConstant.EXCHANGE,
                MessageQueueConstant.WORK_ORDER_KEY, message);

        String eventId = "WORK_ORDER:" + wo.getWorkOrderId() + ":" + eventType + ":" + eventTime;
        awaitUntil(() -> countConsumeLog(eventId) == 1);

        assertEquals(1, countConsumeLog(eventId), "重复投递只能留痕一次");
        verify(webSocketNotifier, timeout(5000).times(1))
                .pushToAdmin(eq(WsMessageDTO.BIZ_WORK_ORDER), eq(eventType), anyMap());
    }

    @Test
    @DisplayName("安灯消息：真实消费并推送管理端 + 相关用户，重复投递幂等")
    void andonMessage_consumedAndPushUsers() {
        AndonAlert alert = andonAlertMapper.selectOne(
                new LambdaQueryWrapper<AndonAlert>().last("LIMIT 1"));
        assertNotNull(alert, "本地库需存在安灯告警（dev_seed_full.sql）");

        LocalDateTime eventTime = LocalDateTime.now().withNano(0);
        String eventType = "TEST_ESCALATE";
        AndonMessageDTO message = AndonMessageDTO.builder()
                .alertId(alert.getAlertId())
                .alertType(alert.getAlertType())
                .level(alert.getAlertLevel())
                .eventType(eventType)
                .createTime(eventTime)
                .build();

        rabbitTemplate.convertAndSend(MessageQueueConstant.EXCHANGE,
                MessageQueueConstant.ANDON_KEY, message);
        rabbitTemplate.convertAndSend(MessageQueueConstant.EXCHANGE,
                MessageQueueConstant.ANDON_KEY, message);

        String eventId = "ANDON:" + alert.getAlertId() + ":" + eventType + ":" + eventTime;
        awaitUntil(() -> countConsumeLog(eventId) == 1);

        assertEquals(1, countConsumeLog(eventId), "重复投递只能留痕一次");
        verify(webSocketNotifier, timeout(5000).times(1))
                .pushToAdmin(eq(WsMessageDTO.BIZ_ANDON), eq(eventType), anyMap());
    }

    private long countConsumeLog(String eventId) {
        return consumeLogMapper.selectCount(
                new LambdaQueryWrapper<ProMessageConsumeLog>()
                        .eq(ProMessageConsumeLog::getEventId, eventId));
    }

    private void awaitUntil(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("等待消息消费被中断");
            }
        }
        fail("等待消息消费超时");
    }
}
