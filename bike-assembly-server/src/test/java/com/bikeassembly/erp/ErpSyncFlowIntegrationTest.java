package com.bikeassembly.erp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bikeassembly.AbstractPostgresContainerIntegrationTest;
import com.bikeassembly.dto.message.ErpSyncMessageDTO;
import com.bikeassembly.entity.ErpSyncLog;
import com.bikeassembly.entity.ProWorkOrder;
import com.bikeassembly.mapper.ErpSyncLogMapper;
import com.bikeassembly.mapper.ProWorkOrderMapper;
import com.bikeassembly.service.ErpSyncLogService;
import com.bikeassembly.vo.ErpSyncLogVO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

/**
 * ERP 同步全链路集成测试。
 * <p>
 * 验证：ERP 同步消息经 MQ 到达消费者 → 幂等消费 → 调用 ErpSyncClient（MOCK）→
 * erp_sync_log 唯一键闭环（同一业务仅一条记录），重复投递只执行一次，重试接口可用。
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "app.rabbitmq.enabled=true",
        "spring.rabbitmq.listener.simple.auto-startup=true"
})
@DisplayName("ERP 同步全链路集成测试")
class ErpSyncFlowIntegrationTest extends AbstractPostgresContainerIntegrationTest {

    @BeforeAll
    static void seedProductionFlow() {
        executeSql("sql/test/seed_production_flow.sql");
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
    private ErpSyncLogMapper erpSyncLogMapper;
    @Autowired
    private ProWorkOrderMapper proWorkOrderMapper;
    @Autowired
    private ErpSyncLogService erpSyncLogService;

    @Test
    @DisplayName("工单同步：MQ 消费→MOCK 同步→日志闭环，重复投递幂等，重试可用")
    void workOrderSync_recordedOnce_andRetryWorks() {
        ProWorkOrder wo = proWorkOrderMapper.selectOne(
                new LambdaQueryWrapper<ProWorkOrder>().last("LIMIT 1"));
        assertNotNull(wo, "本地库需存在工单（seed_production_flow.sql）");
        String bizId = String.valueOf(wo.getWorkOrderId());

        LocalDateTime eventTime = LocalDateTime.now().withNano(0);
        ErpSyncMessageDTO message = ErpSyncMessageDTO.builder()
                .bizType("WORK_ORDER")
                .bizId(bizId)
                .direction("O")
                .eventType("SYNC")
                .createTime(eventTime)
                .build();

        // 同一同步事件投递两次（模拟重复投递/重试）
        rabbitTemplate.convertAndSend("bike.mes.exchange", "bike.mes.erp.sync", message);
        rabbitTemplate.convertAndSend("bike.mes.exchange", "bike.mes.erp.sync", message);

        awaitUntil(() -> countErpLog(bizId) == 1);
        assertEquals(1, countErpLog(bizId), "同一业务只保留一条 ERP 同步记录");

        ErpSyncLog log = erpSyncLogMapper.selectOne(
                new LambdaQueryWrapper<ErpSyncLog>()
                        .eq(ErpSyncLog::getBizType, "WORK_ORDER")
                        .eq(ErpSyncLog::getBizId, bizId)
                        .eq(ErpSyncLog::getSyncDirection, "O"));
        assertNotNull(log, "ERP 同步日志应已落库");
        assertEquals("2", log.getSyncStatus(), "MOCK 同步应成功");
        assertNotNull(log.getRequestData());
        assertNotNull(log.getResponseData());

        // 重试接口：仍成功且不新增记录
        ErpSyncLogVO retried = erpSyncLogService.retrySync(log.getSyncId());
        assertEquals("2", retried.getSyncStatus(), "重试后应成功");
        assertEquals(1, countErpLog(bizId), "重试不应新增记录");
    }

    private long countErpLog(String bizId) {
        return erpSyncLogMapper.selectCount(
                new LambdaQueryWrapper<ErpSyncLog>()
                        .eq(ErpSyncLog::getBizType, "WORK_ORDER")
                        .eq(ErpSyncLog::getBizId, bizId)
                        .eq(ErpSyncLog::getSyncDirection, "O"));
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
                fail("等待 ERP 同步消费被中断");
            }
        }
        fail("等待 ERP 同步消费超时");
    }
}
