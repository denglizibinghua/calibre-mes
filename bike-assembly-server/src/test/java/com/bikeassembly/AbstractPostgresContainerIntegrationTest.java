package com.bikeassembly;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 统一 Testcontainers PostgreSQL 16 集成测试基类。
 * <p>
 * 所有需要数据库的 @SpringBootTest 测试继承此类以使用容器化 PostgreSQL，
 * 不再依赖任何宿主机数据库。
 * <p>
 * 用法：
 * <pre>
 * &#064;SpringBootTest
 * class MyTest extends AbstractPostgresContainerIntegrationTest { ... }
 * </pre>
 */
public abstract class AbstractPostgresContainerIntegrationTest {

    /** 共享 PostgreSQL 16 容器（静态 = 所有测试类共享，避免重复启动） */
    private static final PostgreSQLContainer<?> POSTGRES;
    private static final GenericContainer<?> REDIS;

    /** 标记 Schema 是否已初始化（跨测试类只执行一次） */
    private static volatile boolean schemaInitialized = false;

    static {
        TestcontainersSupport.configureDockerApiVersion();
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("bike_mes_it_test")
                .withUsername("test")
                .withPassword("test");
        POSTGRES.start();
        REDIS = new GenericContainer<>("redis:7-alpine")
                .withExposedPorts(6379);
        REDIS.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (POSTGRES.isRunning()) POSTGRES.stop();
            if (REDIS.isRunning()) REDIS.stop();
        }));
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        // Integration tests never call the model provider; Spring AI still requires a non-blank key at startup.
        registry.add("spring.ai.openai.api-key", () -> "test-only-no-provider");
        registry.add("spring.ai.openai.embedding.api-key", () -> "test-only-no-provider");
    }

    @BeforeAll
    static void initSchemaOnce() {
        if (!schemaInitialized) {
            synchronized (AbstractPostgresContainerIntegrationTest.class) {
                if (!schemaInitialized) {
                    System.out.println("[IT Base] Initializing shared PostgreSQL schema...");
                    executeSql("sql/init_database.sql");
                    schemaInitialized = true;
                    System.out.println("[IT Base] Schema ready (database=" + POSTGRES.getDatabaseName() + ")");
                }
            }
        }
    }

    @AfterAll
    static void cleanupData() {
        // 不删除 Schema，只清理测试数据便于后续测试类复用。
        // 单条 TRUNCATE ... RESTART IDENTITY CASCADE 由 PostgreSQL 保证原子性与外键顺序。
        try (var conn = java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE "
                    + "pro_idempotency_record, pro_status_transition_log, pro_message_consume_log, "
                    + "qms_inspection, qms_defect, pro_work_process, pro_work_order, "
                    + "user_refresh_token, sys_oper_log, base_bom, base_route, base_equipment, "
                    + "qms_inspection_item, andon_alert, eqm_maintenance, eqm_repair_order, "
                    + "wms_stock_doc, wms_inventory, wms_stock_flow, wms_location, "
                    + "base_item, base_process, base_work_unit, erp_sync_log, saf_training, saf_safety_event "
                    + "RESTART IDENTITY CASCADE");
        } catch (Exception e) {
            System.err.println("[IT Base] cleanup warning: " + e.getMessage());
        }
    }

    // ==================== SQL 执行工具 ====================

    /**
     * 在容器内用 psql 执行 SQL 文件（支持 init_database.sql 里的 plpgsql 函数体）。
     */
    protected static void executeSql(String resourcePath) {
        Path sqlFile = resolveSqlPath(resourcePath);
        try {
            System.out.println("[IT Base] Executing: " + sqlFile.getFileName() + " (" + Files.size(sqlFile) + " bytes)");
            MountableFile mf = MountableFile.forHostPath(sqlFile);
            POSTGRES.copyFileToContainer(mf, "/tmp/it_exec.sql");
            var result = POSTGRES.execInContainer(
                    "psql", "-v", "ON_ERROR_STOP=1",
                    "-U", POSTGRES.getUsername(),
                    "-d", POSTGRES.getDatabaseName(),
                    "-f", "/tmp/it_exec.sql");
            String stderr = result.getStderr();
            if (stderr != null && !stderr.isEmpty()) {
                System.err.println("[IT Base] stderr: " + stderr);
            }
            if (result.getExitCode() != 0) {
                throw new RuntimeException("[IT Base] SQL FAILED (exit " + result.getExitCode() + "): " + stderr);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("[IT Base] SQL execution failed: " + resourcePath, e);
        }
    }

    static Path resolveSqlPath(String resourcePath) {
        Path c = Paths.get(resourcePath);
        if (Files.exists(c)) return c.toAbsolutePath().normalize();
        c = Paths.get("../", resourcePath);
        if (Files.exists(c)) return c.toAbsolutePath().normalize();
        return Paths.get(resourcePath).toAbsolutePath().normalize();
    }
}
