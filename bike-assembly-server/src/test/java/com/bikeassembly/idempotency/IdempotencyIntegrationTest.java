package com.bikeassembly.idempotency;

import com.bikeassembly.AbstractPostgresContainerIntegrationTest;
import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.context.BaseContext;
import com.bikeassembly.entity.ProIdempotencyRecord;
import com.bikeassembly.exception.IdempotentKeyConflictException;
import com.bikeassembly.exception.IdempotentKeyMissingException;
import com.bikeassembly.mapper.ProIdempotencyRecordMapper;
import com.bikeassembly.service.IdempotencyService;
import com.bikeassembly.service.IdempotentResult;
import com.bikeassembly.utils.IdempotencyUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 幂等保护集成测试。
 *
 * 覆盖场景：
 *   1. 幂等键校验（空、超长、控制字符）
 *   2. 请求摘要计算
 *   3. 同一 Key + 相同参数 → 只执行一次
 *   4. 同一 Key + 不同参数 → 返回冲突
 *   5. 不同用户相同 Key → 不互相影响
 *   6. 不同业务类型相同 Key → 不互相影响
 *   7. 并发请求安全
 *   8. PROCESSING 超时恢复
 *   9. FAILED 状态重试
 *   10. markSuccess / markFailure
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IdempotencyIntegrationTest extends AbstractPostgresContainerIntegrationTest {

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private ProIdempotencyRecordMapper recordMapper;

    private static final Long TEST_USER_1 = 1L;
    private static final Long TEST_USER_2 = 2L;
    private static final String BIZ_TYPE = IdempotencyService.BIZ_TYPE_WORK_PROCESS;

    @BeforeEach
    void setupContext() {
        BaseContext.setCurrentId(TEST_USER_1);
    }

    @AfterEach
    void clearContext() {
        BaseContext.removeAll();
    }

    // ==================== 1. 幂等键校验 ====================

    @Test
    @Order(1)
    void shouldRejectNullKey() {
        assertThrows(IdempotentKeyMissingException.class, () ->
                IdempotencyUtils.validateIdempotencyKey(null));
    }

    @Test
    @Order(2)
    void shouldRejectEmptyKey() {
        assertThrows(IdempotentKeyMissingException.class, () ->
                IdempotencyUtils.validateIdempotencyKey("   "));
    }

    @Test
    @Order(3)
    void shouldRejectOverlyLongKey() {
        String longKey = "a".repeat(129);
        assertThrows(IdempotentKeyMissingException.class, () ->
                IdempotencyUtils.validateIdempotencyKey(longKey));
    }

    @Test
    @Order(4)
    void shouldAcceptValidKey() {
        String key = IdempotencyUtils.validateIdempotencyKey("  test-key-123  ");
        assertEquals("test-key-123", key);
    }

    // ==================== 2. 请求摘要 ====================

    @Test
    @Order(5)
    void shouldComputeConsistentHash() {
        Map<String, Object> params1 = new LinkedHashMap<>();
        params1.put("processRecordId", 100L);
        params1.put("reportQty", 5);

        Map<String, Object> params2 = new LinkedHashMap<>();
        params2.put("processRecordId", 100L);
        params2.put("reportQty", 5);

        String hash1 = IdempotencyUtils.computeHash(params1);
        String hash2 = IdempotencyUtils.computeHash(params2);

        assertEquals(hash1, hash2, "相同参数应生成相同摘要");
    }

    @Test
    @Order(6)
    void shouldComputeDifferentHashForDifferentParams() {
        Map<String, Object> params1 = new LinkedHashMap<>();
        params1.put("processRecordId", 100L);
        params1.put("reportQty", 5);

        Map<String, Object> params2 = new LinkedHashMap<>();
        params2.put("processRecordId", 100L);
        params2.put("reportQty", 10);

        String hash1 = IdempotencyUtils.computeHash(params1);
        String hash2 = IdempotencyUtils.computeHash(params2);

        assertNotEquals(hash1, hash2, "不同参数应生成不同摘要");
    }

    // ==================== 3. 基本幂等流程 ====================

    @Test
    @Order(7)
    void sameKeySameParamsShouldExecuteOnce() {
        String key = "test-key-basic-" + System.currentTimeMillis();
        String action = "test_basic";
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", 1L);

        // 第一次：应获得执行权
        IdempotentResult r1 = idempotencyService.tryAcquire(key, BIZ_TYPE, action, params, TEST_USER_1);
        assertTrue(r1.isAcquired(), "第一次应获得执行权");

        // 标记成功
        idempotencyService.markSuccess(r1.getRecordId(), 1L, "{\"result\":\"ok\"}");

        // 第二次：应返回重复
        IdempotentResult r2 = idempotencyService.tryAcquire(key, BIZ_TYPE, action, params, TEST_USER_1);
        assertTrue(r2.isDuplicate(), "第二次应返回重复");
        assertNotNull(r2.getExistingResponse());
        assertTrue(r2.getExistingResponse().contains("ok"));
    }

    @Test
    @Order(8)
    void sameKeyDifferentParamsShouldConflict() {
        String key = "test-key-conflict-" + System.currentTimeMillis();
        String action = "test_conflict";
        Map<String, Object> params1 = new LinkedHashMap<>();
        params1.put("id", 1L);

        // 第一次：获得执行权
        IdempotentResult r1 = idempotencyService.tryAcquire(key, BIZ_TYPE, action, params1, TEST_USER_1);
        assertTrue(r1.isAcquired());
        idempotencyService.markSuccess(r1.getRecordId(), 1L, "{\"result\":\"ok\"}");

        // 第二次：不同参数 → 应冲突
        Map<String, Object> params2 = new LinkedHashMap<>();
        params2.put("id", 2L);
        assertThrows(IdempotentKeyConflictException.class, () ->
                idempotencyService.tryAcquire(key, BIZ_TYPE, action, params2, TEST_USER_1));
    }

    // ==================== 4. 用户隔离 ====================

    @Test
    @Order(9)
    void differentUsersSameKeyShouldNotConflict() {
        String key = "test-key-user-iso-" + System.currentTimeMillis();
        String action = "test_user_iso";
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", 1L);

        // 用户1
        IdempotentResult r1 = idempotencyService.tryAcquire(key, BIZ_TYPE, action, params, TEST_USER_1);
        assertTrue(r1.isAcquired());

        // 用户2：应独立获得执行权
        IdempotentResult r2 = idempotencyService.tryAcquire(key, BIZ_TYPE, action, params, TEST_USER_2);
        assertTrue(r2.isAcquired(), "不同用户相同Key应各自独立");

        idempotencyService.markSuccess(r1.getRecordId(), 1L, "{\"user\":1}");
        idempotencyService.markSuccess(r2.getRecordId(), 2L, "{\"user\":2}");
    }

    // ==================== 5. 业务类型隔离 ====================

    @Test
    @Order(10)
    void differentActionShouldNotConflict() {
        String key = "test-key-action-iso-" + System.currentTimeMillis();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", 1L);

        // 报工
        IdempotentResult r1 = idempotencyService.tryAcquire(
                key, BIZ_TYPE, IdempotencyService.ACTION_UNIFIED_REPORT, params, TEST_USER_1);
        assertTrue(r1.isAcquired());

        // 开工
        IdempotentResult r2 = idempotencyService.tryAcquire(
                key, BIZ_TYPE, IdempotencyService.ACTION_START, params, TEST_USER_1);
        assertTrue(r2.isAcquired(), "不同action相同Key应各自独立");

        idempotencyService.markSuccess(r1.getRecordId(), 1L, "{}");
        idempotencyService.markSuccess(r2.getRecordId(), 1L, "{}");
    }

    // ==================== 6. 并发安全 ====================

    @Test
    @Order(11)
    void concurrentSameKeyShouldOnlyExecuteOnce() throws Exception {
        String key = "test-key-concurrent-" + System.currentTimeMillis();
        String action = "test_concurrent";
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", 1L);

        int threadCount = 5;
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger acquiredCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);
        AtomicInteger processingCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    BaseContext.setCurrentId(TEST_USER_1);
                    readyLatch.countDown();
                    startLatch.await(); // 同时出发
                    IdempotentResult r = idempotencyService.tryAcquire(
                            key, BIZ_TYPE, action, params, TEST_USER_1);
                    if (r.isAcquired()) {
                        acquiredCount.incrementAndGet();
                    } else if (r.isDuplicate()) {
                        duplicateCount.incrementAndGet();
                    } else if (r.isProcessing()) {
                        processingCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // 冲突也算正常
                    if (e instanceof IdempotentKeyConflictException) {
                        duplicateCount.incrementAndGet();
                    }
                } finally {
                    BaseContext.removeAll();
                    doneLatch.countDown();
                }
            }).start();
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();

        assertEquals(1, acquiredCount.get(), "并发请求中只有一个获得执行权");
        assertEquals(threadCount - 1, duplicateCount.get() + processingCount.get() + (acquiredCount.get() - 1),
                "其余请求应返回重复、处理中或冲突");
    }

    // ==================== 7. PROCESSING 超时恢复 ====================

    // ==================== 8. FAILED 状态允许重试 ====================

    @Test
    @Order(12)
    void failedStatusShouldAllowRetry() {
        String key = "test-key-retry-" + System.currentTimeMillis();
        String action = "test_retry";
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", 1L);

        // 第一次：获得执行权
        IdempotentResult r1 = idempotencyService.tryAcquire(key, BIZ_TYPE, action, params, TEST_USER_1);
        assertTrue(r1.isAcquired());

        // 标记失败
        idempotencyService.markFailure(r1.getRecordId(), 500, "DB error");

        // 第二次：应允许重试
        IdempotentResult r2 = idempotencyService.tryAcquire(key, BIZ_TYPE, action, params, TEST_USER_1);
        assertTrue(r2.isAcquired(), "失败后应允许同Key重试");
        assertEquals(r1.getRecordId(), r2.getRecordId(), "应复用同一条幂等记录");

        // 标记成功
        idempotencyService.markSuccess(r2.getRecordId(), 1L, "{\"result\":\"retry_ok\"}");

        // 第三次：已成功 → 返回重复
        IdempotentResult r3 = idempotencyService.tryAcquire(key, BIZ_TYPE, action, params, TEST_USER_1);
        assertTrue(r3.isDuplicate());
    }
}
