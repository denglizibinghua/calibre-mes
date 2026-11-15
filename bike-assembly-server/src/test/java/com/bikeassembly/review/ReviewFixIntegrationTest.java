package com.bikeassembly.review;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.bikeassembly.TestcontainersSupport;
import com.bikeassembly.context.BaseContext;
import com.bikeassembly.dto.WmsStockDocDTO;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.mapper.WmsStockDocMapper;
import com.bikeassembly.service.*;
import com.bikeassembly.service.erp.ErpSyncPublisher;
import com.bikeassembly.service.impl.*;
import com.bikeassembly.util.UserContextHelper;
import com.bikeassembly.vo.ProWorkProcessVO;
import com.bikeassembly.ws.WebSocketNotifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real PostgreSQL services/mappers; external notifications are the only mocked dependencies. */
@SpringJUnitConfig(ReviewFixIntegrationTest.Config.class)
class ReviewFixIntegrationTest {
    private static PostgreSQLContainer<?> postgres;
    private static String url;
    private static String user;
    private static String password;
    private static final String SCHEMA = "mes_review_" + UUID.randomUUID().toString().replace("-", "");
    @Autowired JdbcTemplate jdbc;
    @Autowired WmsStockDocService stock;
    @Autowired ProWorkProcessService processes;
    @Autowired ProWorkOrderService orders;
    @Autowired QmsInspectionService inspections;
    @Autowired IdempotencyService idempotency;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ObjectMapper objectMapper;
    @Autowired WmsStockDocMapper stockMapper;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) throws Exception {
        if (url == null) {
            TestcontainersSupport.configureDockerApiVersion();
            postgres = new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName(SCHEMA);
            postgres.start();
            url = postgres.getJdbcUrl(); user = postgres.getUsername(); password = postgres.getPassword();
            Path schema = Path.of("../sql/init_database.sql");
            postgres.copyFileToContainer(MountableFile.forHostPath(schema), "/tmp/init_database.sql");
            var init = postgres.execInContainer("psql", "-v", "ON_ERROR_STOP=1",
                    "-U", user, "-d", SCHEMA, "-f", "/tmp/init_database.sql");
            if (init.getExitCode() != 0) {
                throw new IllegalStateException("init_database.sql failed (exit " + init.getExitCode() + "): " + init.getStderr());
            }
            try (var connection = DriverManager.getConnection(url, user, password)) {
                assertEquals(SCHEMA, connection.getCatalog(), "Destructive fixture SQL requires our disposable schema");
                connection.createStatement().execute("CREATE TABLE review_tx_probe (id INT PRIMARY KEY)");
            }
        }
        properties.add("review.jdbc.url", () -> url);
        properties.add("review.jdbc.user", () -> user);
        properties.add("review.jdbc.password", () -> password);
    }

    @AfterAll
    static void dropDisposableDatabase() {
        if (postgres != null) postgres.stop();
    }

    @BeforeEach
    void cleanFixtures() {
        for (String table : List.of("qms_inspection", "pro_work_process", "pro_work_order", "wms_stock_flow",
                "wms_stock_doc", "wms_inventory", "wms_location", "base_item", "base_process",
                "pro_idempotency_record", "review_tx_probe")) jdbc.update("DELETE FROM " + table);
        context(null);
    }

    @AfterEach
    void clearContext() { BaseContext.removeAll(); reset(objectMapper); }

    @ParameterizedTest
    @ValueSource(strings = {"I", "O", "T"})
    void concurrentStockExecutionChangesBalancesAndFlowsOnce(String type) throws Exception {
        long item = item(); long source = location(); long target = location();
        jdbc.update("INSERT INTO wms_inventory(item_id,location_id,qty,locked_qty) VALUES (?,?,100,0)", item, source);
        long doc = doc(type, item, source, target, 10);
        List<Boolean> results = parallel(() -> {
            try { stock.executeStockDoc(doc); return true; }
            catch (BaseException expected) { return false; }
        });
        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(type.equals("I") ? 110 : 90, quantity(item, source).intValueExact());
        if (type.equals("T")) assertEquals(10, quantity(item, target).intValueExact());
        assertEquals(type.equals("T") ? 2 : 1,
                jdbc.queryForObject("SELECT COUNT(*) FROM wms_stock_flow WHERE doc_id=?", Integer.class, doc));
        assertEquals("2", stock.getWmsStockDocById(doc).getStatus());
    }

    @Test
    void executedDocumentCannotBeResetEditedOrDeleted() {
        long item = item(), location = location(); long doc = doc("I", item, location, null, 10);
        stock.executeStockDoc(doc);
        WmsStockDocDTO reset = new WmsStockDocDTO(); reset.setStatus("0");
        assertThrows(BaseException.class, () -> stock.updateWmsStockDoc(doc, reset));
        WmsStockDocDTO edit = new WmsStockDocDTO(); edit.setDetailsJson("[]");
        assertThrows(BaseException.class, () -> stock.updateWmsStockDoc(doc, edit));
        assertThrows(BaseException.class, () -> stock.deleteWmsStockDoc(doc));
        assertThrows(BaseException.class, () -> stock.executeStockDoc(doc));
        assertEquals(10, quantity(item, location).intValueExact());
    }

    @Test
    void crudCannotForgeExecutedStateButDraftStillSupportsEditingAndDeletion() {
        WmsStockDocDTO create = new WmsStockDocDTO(); create.setStatus("2");
        assertThrows(BaseException.class, () -> stock.saveWmsStockDoc(create));
        long doc = doc("I", item(), location(), null, 10);
        WmsStockDocDTO edit = new WmsStockDocDTO(); edit.setStatus("2");
        assertThrows(BaseException.class, () -> stock.updateWmsStockDoc(doc, edit));
        edit.setStatus("1"); edit.setRelatedOrderNo("updated");
        assertEquals("updated", stock.updateWmsStockDoc(doc, edit).getRelatedOrderNo());
        stock.deleteWmsStockDoc(doc);
        assertNull(stockMapper.selectById(doc));
    }

    @Test
    void editWaitingForExecutionSeesCommittedExecutedState() throws Exception {
        long doc = doc("I", item(), location(), null, 10);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        List<Future<Boolean>> future = new ArrayList<>();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                assertNotNull(stockMapper.selectByIdForUpdate(doc));
                future.add(executor.submit(() -> {
                    WmsStockDocDTO edit = new WmsStockDocDTO(); edit.setStatus("0");
                    try { stock.updateWmsStockDoc(doc, edit); return true; }
                    catch (BaseException expected) { return false; }
                }));
                stock.executeStockDoc(doc);
            });
            assertFalse(future.get(0).get(10, TimeUnit.SECONDS));
            assertEquals("2", stock.getWmsStockDocById(doc).getStatus());
        } finally { executor.shutdownNow(); }
    }

    @Test
    void failedStockExecutionRollsBackEarlierDetailsAndAllowsRetry() {
        long item = item(), location = location(); long doc = doc("I", item, location, null, 10);
        WmsStockDocDTO edit = new WmsStockDocDTO();
        edit.setDetailsJson("[{\"itemId\":" + item + ",\"locationId\":" + location
                + ",\"qty\":10},{\"itemId\":" + item + ",\"locationId\":" + location + ",\"qty\":-1}]");
        stock.updateWmsStockDoc(doc, edit);
        assertThrows(BaseException.class, () -> stock.executeStockDoc(doc));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wms_inventory", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wms_stock_flow", Integer.class));
        assertEquals("0", stock.getWmsStockDocById(doc).getStatus());
    }

    @Test
    void recentlyRenewedRecordUsesUpdateTimeInsteadOfOldCreationTime() {
        String key = UUID.randomUUID().toString(); long id = acquire(key).getRecordId();
        jdbc.update("UPDATE pro_idempotency_record SET create_time=CURRENT_TIMESTAMP - INTERVAL '10 minutes',updated_at=CURRENT_TIMESTAMP WHERE id=?", id);
        assertTrue(acquire(key).isProcessing());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "2"})
    void expiredOrFailedRecordHasOneConcurrentRetryWinner(String initialStatus) throws Exception {
        String key = UUID.randomUUID().toString(); long id = acquire(key).getRecordId();
        jdbc.update("UPDATE pro_idempotency_record SET process_status=?,updated_at=CURRENT_TIMESTAMP - INTERVAL '10 minutes' WHERE id=?", initialStatus, id);
        List<IdempotentResult> results = parallel(() -> acquire(key));
        assertEquals(1, results.stream().filter(IdempotentResult::isAcquired).count());
        assertEquals(5, results.stream().filter(IdempotentResult::isProcessing).count());
        assertTrue(acquire(key).isProcessing());
    }

    @Test
    void firstAcquisitionAlsoHasOneConcurrentWinner() throws Exception {
        String key = UUID.randomUUID().toString();
        assertEquals(1, parallel(() -> acquire(key)).stream().filter(IdempotentResult::isAcquired).count());
    }

    @Test
    void successSnapshotRollsBackWithBusinessAndRetryCanCommit() {
        String key = UUID.randomUUID().toString(); long id = acquire(key).getRecordId();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            idempotency.lockExecution(id);
            jdbc.update("INSERT INTO review_tx_probe VALUES (1)");
            idempotency.markSuccess(id, 1L, "{\"ok\":true}");
            throw new IllegalStateException("force rollback after success snapshot");
        }));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM review_tx_probe", Integer.class));
        assertEquals("2", jdbc.queryForObject("SELECT process_status FROM pro_idempotency_record WHERE id=?", String.class, id));
        assertNull(idempotency.getExistingResponse(key, "WORK_PROCESS", "review", 1L));
        assertTrue(acquire(key).isAcquired());
        transaction.executeWithoutResult(status -> {
            idempotency.lockExecution(id);
            jdbc.update("INSERT INTO review_tx_probe VALUES (1)");
            idempotency.markSuccess(id, 1L, "{\"ok\":true}");
        });
        assertTrue(acquire(key).isDuplicate());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM review_tx_probe", Integer.class));
    }

    @Test
    void overdueRecordCannotBeTakenOverWhileBusinessTransactionStillHoldsLock() throws Exception {
        String key = UUID.randomUUID().toString(); long id = acquire(key).getRecordId();
        jdbc.update("UPDATE pro_idempotency_record SET updated_at=CURRENT_TIMESTAMP - INTERVAL '10 minutes' WHERE id=?", id);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        List<Future<IdempotentResult>> retry = new ArrayList<>();
        CountDownLatch started = new CountDownLatch(1);
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                idempotency.lockExecution(id);
                retry.add(executor.submit(() -> { started.countDown(); return acquire(key); }));
                try {
                    assertTrue(started.await(5, TimeUnit.SECONDS));
                    assertThrows(TimeoutException.class, () -> retry.get(0).get(200, TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                idempotency.markSuccess(id, 1L, "{\"ok\":true}");
            });
            assertTrue(retry.get(0).get(10, TimeUnit.SECONDS).isDuplicate());
        } finally { executor.shutdownNow(); }
    }

    @Test
    @SuppressWarnings("deprecation")
    void legacyHashApiUsesSameAtomicLeaseRecovery() {
        String key = UUID.randomUUID().toString();
        var first = idempotency.tryAcquire(key, "WORK_PROCESS", "legacy", "existing-hash", 1L);
        jdbc.update("UPDATE pro_idempotency_record SET updated_at=CURRENT_TIMESTAMP - INTERVAL '10 minutes' WHERE id=?", first.getRecordId());
        assertTrue(idempotency.tryAcquire(key, "WORK_PROCESS", "legacy", "existing-hash", 1L).isAcquired());
        assertTrue(idempotency.tryAcquire(key, "WORK_PROCESS", "legacy", "existing-hash", 1L).isProcessing());
    }

    @Test
    void partialReportReplaysTimeFieldsAndNeverAccumulatesTwice() {
        long record = production("1", 0); String key = "client::" + UUID.randomUUID();
        context(key); ProWorkProcessVO first = processes.unifiedReport(record, new BigDecimal("3"), null);
        assertEquals(key, BaseContext.getIdempotencyKey());
        context(key); ProWorkProcessVO replay = processes.unifiedReport(record, new BigDecimal("3"), null);
        assertEquals(first.getStartTime(), replay.getStartTime());
        assertEquals(first.getCreateTime(), replay.getCreateTime());
        assertEquals(first.getVersion(), replay.getVersion());
        assertEquals(3, jdbc.queryForObject("SELECT report_qty FROM pro_work_process WHERE process_record_id=?", BigDecimal.class, record).intValueExact());
        assertEquals("1", jdbc.queryForObject("SELECT process_status FROM pro_idempotency_record", String.class));
    }

    @Test
    void serializationFailureRollsBackPartialReportThenSameKeyCanRetry() throws Exception {
        long record = production("1", 0); String key = UUID.randomUUID().toString();
        doThrow(new com.fasterxml.jackson.databind.JsonMappingException(null, "simulated serialization failure"))
                .when(objectMapper).writeValueAsString(isA(ProWorkProcessVO.class));
        context(key);
        assertThrows(BaseException.class, () -> processes.unifiedReport(record, new BigDecimal("3"), null));
        assertEquals(0, jdbc.queryForObject("SELECT report_qty FROM pro_work_process WHERE process_record_id=?", BigDecimal.class, record).intValueExact());
        assertEquals("2", jdbc.queryForObject("SELECT process_status FROM pro_idempotency_record", String.class));
        reset(objectMapper);
        context(key); processes.unifiedReport(record, new BigDecimal("3"), null);
        assertEquals(3, jdbc.queryForObject("SELECT report_qty FROM pro_work_process WHERE process_record_id=?", BigDecimal.class, record).intValueExact());
    }

    @Test
    void inspectionConfirmationPreservesParentSnapshotAndReplaysAfterQcCallback() {
        long record = production("3", 10);
        long order = jdbc.queryForObject("SELECT work_order_id FROM pro_work_process WHERE process_record_id=?", Long.class, record);
        long process = jdbc.queryForObject("SELECT process_id FROM pro_work_process WHERE process_record_id=?", Long.class, record);
        String inspectionNo = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO qms_inspection(inspection_no,work_order_id,process_id,process_record_id,result,status,inspector_user_id,inspection_time,details_json,version) VALUES (?,?,?,?,'P','1',1,CURRENT_TIMESTAMP,'[]',0)", inspectionNo, order, process, record);
        long id = jdbc.queryForObject("SELECT inspection_id FROM qms_inspection WHERE inspection_no=?", Long.class, inspectionNo);
        String key = "qc::" + UUID.randomUUID();
        context(key); var first = inspections.confirmInspection(id);
        context(key); var replay = inspections.confirmInspection(id);
        assertEquals(first.getInspectionId(), replay.getInspectionId());
        assertEquals(first.getConfirmTime(), replay.getConfirmTime());
        assertEquals("2", replay.getStatus());
        assertEquals("2", jdbc.queryForObject("SELECT status FROM pro_work_process WHERE process_record_id=?", String.class, record));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM pro_idempotency_record", Integer.class));
        assertTrue(jdbc.queryForObject("SELECT response_data FROM pro_idempotency_record", String.class).contains("inspectionId"));
    }

    @Test
    void workOrderPauseCanReplayTimeFieldsAndContextClearsRecordId() {
        long record = production("1", 0);
        long order = jdbc.queryForObject("SELECT work_order_id FROM pro_work_process WHERE process_record_id=?", Long.class, record);
        String key = UUID.randomUUID().toString(); context(key); var first = orders.pauseWorkOrder(order);
        context(key); var replay = orders.pauseWorkOrder(order);
        assertEquals(first.getCreateTime(), replay.getCreateTime());
        assertEquals("5", replay.getStatus());
        BaseContext.removeAll(); assertNull(BaseContext.getIdempotencyRecordId());
    }

    private IdempotentResult acquire(String key) {
        return idempotency.tryAcquire(key, "WORK_PROCESS", "review", Map.of("id", 1), 1L);
    }
    private void context(String key) { BaseContext.removeAll(); BaseContext.setCurrentId(1L); BaseContext.setIdempotencyKey(key); }
    private long item() {
        String code = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO base_item(item_code,item_name,item_type,unit,status) VALUES (?,?,'P','台','0')", code, code);
        return jdbc.queryForObject("SELECT item_id FROM base_item WHERE item_code=?", Long.class, code);
    }
    private long location() {
        String code = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO wms_location(location_code,location_name,location_type,status) VALUES (?,?,'L','0')", code, code);
        return jdbc.queryForObject("SELECT location_id FROM wms_location WHERE location_code=?", Long.class, code);
    }
    private BigDecimal quantity(long item, long location) {
        return jdbc.queryForObject("SELECT qty FROM wms_inventory WHERE item_id=? AND location_id=?", BigDecimal.class, item, location);
    }
    private long doc(String type, long item, long location, Long target, int qty) {
        WmsStockDocDTO dto = new WmsStockDocDTO(); dto.setDocNo(UUID.randomUUID().toString()); dto.setDocType(type);
        dto.setStatus("0"); dto.setDetailsJson("[{\"itemId\":" + item + ",\"locationId\":" + location
                + ",\"qty\":" + qty + (target == null ? "" : ",\"targetLocationId\":" + target) + "}]");
        return stock.saveWmsStockDoc(dto).getDocId();
    }
    private long production(String processStatus, int qty) {
        long item = item(); String code = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO base_process(process_code,process_name,status) VALUES (?,?,'0')", code, code);
        long process = jdbc.queryForObject("SELECT process_id FROM base_process WHERE process_code=?", Long.class, code);
        jdbc.update("INSERT INTO pro_work_order(work_order_no,item_id,plan_qty,unit,status,assignee_user_id,version,create_time) VALUES (?,?,10,'台','1',1,0,CURRENT_TIMESTAMP)", code, item);
        long order = jdbc.queryForObject("SELECT work_order_id FROM pro_work_order WHERE work_order_no=?", Long.class, code);
        jdbc.update("INSERT INTO pro_work_process(work_order_id,process_id,step_seq,status,report_qty,version,start_time,create_time) VALUES (?,?,1,?,?,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", order, process, processStatus, qty);
        return jdbc.queryForObject("SELECT process_record_id FROM pro_work_process WHERE work_order_id=?", Long.class, order);
    }
    private <T> List<T> parallel(Supplier<T> action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(6);
        CountDownLatch ready = new CountDownLatch(6), start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 6; i++) futures.add(executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                context(null); try { return action.get(); } finally { BaseContext.removeAll(); }
            }));
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        } finally { start.countDown(); executor.shutdownNow(); }
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    @MapperScan("com.bikeassembly.mapper")
    @Import({WmsStockDocServiceImpl.class, IdempotencyServiceImpl.class, ProWorkOrderServiceImpl.class,
            ProWorkProcessServiceImpl.class, QmsInspectionServiceImpl.class})
    static class Config {
        @Bean DataSource dataSource(@Value("${review.jdbc.url}") String url,
                                    @Value("${review.jdbc.user}") String user,
                                    @Value("${review.jdbc.password}") String password) {
            return new DriverManagerDataSource(url, user, password);
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }
        @Bean PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(source);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/*.xml"));
            MybatisPlusInterceptor plugins = new MybatisPlusInterceptor(); plugins.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
            factory.setPlugins(plugins); return factory.getObject();
        }
        @Bean ObjectMapper objectMapper() { return spy(Jackson2ObjectMapperBuilder.json().modules(new JavaTimeModule()).build()); }
        @Bean StatusTransitionLogService logs() { return mock(StatusTransitionLogService.class); }
        @Bean PermissionService permissions() { return mock(PermissionService.class); }
        @Bean WebSocketNotifier notifier() { return mock(WebSocketNotifier.class); }
        @Bean ErpSyncPublisher erp() { return mock(ErpSyncPublisher.class); }
        @Bean UserContextHelper userContext() { return mock(UserContextHelper.class); }
    }
}
