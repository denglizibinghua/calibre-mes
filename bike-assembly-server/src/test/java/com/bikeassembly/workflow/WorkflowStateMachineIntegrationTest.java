package com.bikeassembly.workflow;

import com.bikeassembly.AbstractMySqlContainerIntegrationTest;
import com.bikeassembly.enums.*;
import com.bikeassembly.mapper.*;
import com.bikeassembly.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V3.4 工序流转状态机集成测试。
 * 使用 Testcontainers MySQL 8，Schema 由 init_database.sql / migration 提供。
 * 不得修改数据库结构——只查询、调用接口、断言。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkflowStateMachineIntegrationTest extends AbstractMySqlContainerIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ProWorkProcessMapper proWorkProcessMapper;
    @Autowired private IdempotencyService idempotencyService;

    // ==================== Schema 断言（只读 information_schema） ====================

    @Test
    @Order(1)
    void schema_workOrderHasRequestId() {
        Integer cnt = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.COLUMNS " +
            "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_work_order' AND COLUMN_NAME='request_id'", Integer.class);
        assertTrue(cnt != null && cnt > 0, "pro_work_order 缺少 request_id");
    }

    @Test
    @Order(2)
    void schema_workOrderHasVersion() {
        Integer cnt = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.COLUMNS " +
            "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_work_order' AND COLUMN_NAME='version'", Integer.class);
        assertTrue(cnt != null && cnt > 0, "pro_work_order 缺少 version");
    }

    @Test
    @Order(3)
    void schema_workProcessHasStepSeq() {
        Integer cnt = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.COLUMNS " +
            "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_work_process' AND COLUMN_NAME='step_seq'", Integer.class);
        assertTrue(cnt != null && cnt > 0, "pro_work_process 缺少 step_seq");
    }

    @Test
    @Order(4)
    void schema_workProcessHasVersion() {
        Integer cnt = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.COLUMNS " +
            "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_work_process' AND COLUMN_NAME='version'", Integer.class);
        assertTrue(cnt != null && cnt > 0, "pro_work_process 缺少 version");
    }

    @Test
    @Order(5)
    void schema_stepSeqHasUniqueIndex() {
        Integer cnt = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.STATISTICS " +
            "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_work_process' AND INDEX_NAME='uk_wp_wo_step'", Integer.class);
        assertTrue(cnt != null && cnt > 0, "缺少 (work_order_id, step_seq) 唯一索引");
    }

    @Test
    @Order(6)
    void schema_qmsInspectionHasVersion() {
        Integer cnt = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.COLUMNS " +
            "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='qms_inspection' AND COLUMN_NAME='version'", Integer.class);
        assertTrue(cnt != null && cnt > 0, "qms_inspection 缺少 version");
    }

    @Test
    @Order(7)
    void schema_transitionLogTableExists() {
        Integer cnt = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.TABLES " +
            "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_status_transition_log'", Integer.class);
        assertTrue(cnt != null && cnt > 0, "缺少 pro_status_transition_log 表");
    }

    @Test
    @Order(8)
    void schema_idempotencyTableExists() {
        Integer cnt = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.TABLES " +
            "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_idempotency_record'", Integer.class);
        assertTrue(cnt != null && cnt > 0, "缺少 pro_idempotency_record 表");

        Integer idxCnt = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.STATISTICS " +
            "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_idempotency_record' AND INDEX_NAME='uk_idm_user_op_key'", Integer.class);
        assertTrue(idxCnt != null && idxCnt > 0, "pro_idempotency_record 缺少唯一索引 uk_idm_user_op_key");
    }

    @Test
    @Order(9)
    void schema_messageConsumeLogTableExists() {
        Integer cnt = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.TABLES " +
            "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_message_consume_log'", Integer.class);
        assertTrue(cnt != null && cnt > 0, "缺少 pro_message_consume_log 表");
    }

    // ==================== 状态机转换规则 ====================

    @Test
    @Order(10)
    void transition_workOrderLegalTransitions() {
        assertTrue(StatusTransitionValidator.canTransition(WorkOrderStatus.CREATED, WorkOrderStatus.RELEASED));
        assertTrue(StatusTransitionValidator.canTransition(WorkOrderStatus.CREATED, WorkOrderStatus.CANCELLED));
        assertTrue(StatusTransitionValidator.canTransition(WorkOrderStatus.RELEASED, WorkOrderStatus.IN_PROGRESS));
        assertTrue(StatusTransitionValidator.canTransition(WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.PAUSED));
        assertTrue(StatusTransitionValidator.canTransition(WorkOrderStatus.PAUSED, WorkOrderStatus.IN_PROGRESS));
        assertTrue(StatusTransitionValidator.canTransition(WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.COMPLETED));
        assertTrue(StatusTransitionValidator.canTransition(WorkOrderStatus.COMPLETED, WorkOrderStatus.CLOSED));
    }

    @Test
    @Order(11)
    void transition_workOrderIllegalTransitions() {
        assertFalse(StatusTransitionValidator.canTransition(WorkOrderStatus.CREATED, WorkOrderStatus.COMPLETED));
        assertFalse(StatusTransitionValidator.canTransition(WorkOrderStatus.CANCELLED, WorkOrderStatus.CREATED));
        assertFalse(StatusTransitionValidator.canTransition(WorkOrderStatus.CLOSED, WorkOrderStatus.IN_PROGRESS));
        assertFalse(StatusTransitionValidator.canTransition(WorkOrderStatus.COMPLETED, WorkOrderStatus.IN_PROGRESS));
    }

    @Test
    @Order(12)
    void transition_workProcessLegalTransitions() {
        assertTrue(StatusTransitionValidator.canTransition(WorkProcessStatus.PENDING, WorkProcessStatus.READY));
        assertTrue(StatusTransitionValidator.canTransition(WorkProcessStatus.READY, WorkProcessStatus.IN_PROGRESS));
        assertTrue(StatusTransitionValidator.canTransition(WorkProcessStatus.IN_PROGRESS, WorkProcessStatus.COMPLETED));
        assertTrue(StatusTransitionValidator.canTransition(WorkProcessStatus.IN_PROGRESS, WorkProcessStatus.WAIT_INSPECTION));
        assertTrue(StatusTransitionValidator.canTransition(WorkProcessStatus.WAIT_INSPECTION, WorkProcessStatus.QUALIFIED));
        assertTrue(StatusTransitionValidator.canTransition(WorkProcessStatus.WAIT_INSPECTION, WorkProcessStatus.REJECTED));
        assertTrue(StatusTransitionValidator.canTransition(WorkProcessStatus.REJECTED, WorkProcessStatus.REWORK));
        assertTrue(StatusTransitionValidator.canTransition(WorkProcessStatus.REWORK, WorkProcessStatus.WAIT_INSPECTION));
        assertTrue(StatusTransitionValidator.canTransition(WorkProcessStatus.QUALIFIED, WorkProcessStatus.COMPLETED));
    }

    @Test
    @Order(13)
    void transition_workProcessIllegalTransitions() {
        assertFalse(StatusTransitionValidator.canTransition(WorkProcessStatus.PENDING, WorkProcessStatus.COMPLETED));
        assertFalse(StatusTransitionValidator.canTransition(WorkProcessStatus.READY, WorkProcessStatus.QUALIFIED));
        assertFalse(StatusTransitionValidator.canTransition(WorkProcessStatus.REJECTED, WorkProcessStatus.COMPLETED));
        assertFalse(StatusTransitionValidator.canTransition(WorkProcessStatus.REWORK, WorkProcessStatus.COMPLETED));
        assertFalse(StatusTransitionValidator.canTransition(WorkProcessStatus.COMPLETED, WorkProcessStatus.IN_PROGRESS));
    }

    // ==================== 枚举 ====================

    @Test
    @Order(14)
    void enum_labels() {
        assertEquals("已创建", WorkOrderStatus.fromCode("0").getLabel());
        assertEquals("待开始", WorkProcessStatus.fromCode("0").getLabel());
        assertEquals("待检", QcResult.fromCode("0").getLabel());
        assertEquals("合格", QcResult.fromCode("1").getLabel());
        assertEquals("不合格", QcResult.fromCode("2").getLabel());
        assertEquals("待复检", QcResult.fromCode("3").getLabel());
        assertEquals("待检", InspectionStatus.fromCode("0").getLabel());
        assertEquals("已录入", InspectionStatus.fromCode("1").getLabel());
        assertEquals("已确认", InspectionStatus.fromCode("2").getLabel());
    }

    // ==================== 幂等 ====================

    @Test
    @Order(20)
    void idempotency_sameKeySameHash_returnsMinusOne() {
        String key = UUID.randomUUID().toString();
        String hash = "abc-def-123";
        var r1 = idempotencyService.tryAcquire(key, "WORK_ORDER", "release", hash, 1L);
        assertTrue(r1.isAcquired(), "first acquire should succeed");
        idempotencyService.markSuccess(r1.getRecordId(), 1L, "{}");
        var r2 = idempotencyService.tryAcquire(key, "WORK_ORDER", "release", hash, 1L);
        assertTrue(r2.isDuplicate(), "same key+hash should be duplicate");
    }

    @Test
    @Order(21)
    void idempotency_sameKeyDifferentHash_returnsMinusTwo() {
        String key = UUID.randomUUID().toString();
        var r1 = idempotencyService.tryAcquire(key, "WORK_ORDER", "release", "hash-a", 1L);
        assertTrue(r1.isAcquired());
        idempotencyService.markSuccess(r1.getRecordId(), 1L, "{}");
        assertThrows(com.bikeassembly.exception.IdempotentKeyConflictException.class, () ->
            idempotencyService.tryAcquire(key, "WORK_ORDER", "release", "hash-b", 1L),
            "same key different hash should be conflict");
    }

    @Test
    @Order(22)
    void idempotency_failedKey_allowsRetry() {
        String key = UUID.randomUUID().toString();
        String hash = "retry-test";
        var r1 = idempotencyService.tryAcquire(key, "WORK_PROCESS", "report", hash, 1L);
        assertTrue(r1.isAcquired());
        idempotencyService.markFailure(r1.getRecordId(), 500, "error");
        var r2 = idempotencyService.tryAcquire(key, "WORK_PROCESS", "report", hash, 1L);
        assertTrue(r2.isAcquired(), "failed key should allow retry");
    }

    @Test
    @Order(23)
    void idempotency_concurrentSameKey_onlyOneSucceeds() throws Exception {
        String key = UUID.randomUUID().toString();
        String hash = "concurrent";
        java.util.concurrent.atomic.AtomicInteger success = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger dup = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger processing = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        int N = 3;
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(N);
        java.util.concurrent.ExecutorService exec = java.util.concurrent.Executors.newFixedThreadPool(N);
        for (int i = 0; i < N; i++) {
            exec.submit(() -> {
                try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                try {
                    var r = idempotencyService.tryAcquire(key, "WORK_ORDER", "pause", hash, 1L);
                    if (r.isAcquired()) success.incrementAndGet();
                    else if (r.isDuplicate()) dup.incrementAndGet();
                    else if (r.isProcessing()) processing.incrementAndGet();
                } catch (Exception e) {
                    // conflict也算冲突
                    dup.incrementAndGet();
                }
                done.countDown();
            });
        }
        latch.countDown();
        done.await(10, java.util.concurrent.TimeUnit.SECONDS);
        exec.shutdown();
        assertEquals(1, success.get(), "only one should acquire");
        assertEquals(N - 1, dup.get() + processing.get(), "rest should be duplicates or processing");
    }

    // ==================== stepSeq 查询 ====================

    @Test
    @Order(30)
    void stepSeq_maxForMissingWorkOrder_isZero() {
        int max = proWorkProcessMapper.selectMaxStepSeqByWorkOrderId(-999L);
        assertEquals(0, max);
    }

    // ==================== SQL 语法验证 ====================

    @Test
    @Order(40)
    void sql_accumulateReportQty_noRows() {
        int result = proWorkProcessMapper.accumulateReportQty(-1L, 0, BigDecimal.ONE);
        assertEquals(0, result);
    }
}
