package com.bikeassembly;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 统一 Testcontainers MySQL 8 集成测试基类。
 * <p>
 * 所有需要数据库的 @SpringBootTest 测试继承此类以使用容器化 MySQL，
 * 不再依赖 localhost:3306。
 * <p>
 * 用法：
 * <pre>
 * &#064;SpringBootTest
 * class MyTest extends AbstractMySqlContainerIntegrationTest { ... }
 * </pre>
 */
public abstract class AbstractMySqlContainerIntegrationTest {

    /** 共享 MySQL 容器（静态 = 所有测试类共享，避免重复启动） */
    private static final MySQLContainer<?> MYSQL;
    private static final GenericContainer<?> REDIS;

    /** 标记 Schema 是否已初始化（跨测试类只执行一次） */
    private static volatile boolean schemaInitialized = false;

    static {
        MYSQL = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("bike_mes_it_test")
                .withUsername("test")
                .withPassword("test");
        MYSQL.start();
        REDIS = new GenericContainer<>("redis:7-alpine")
                .withExposedPorts(6379);
        REDIS.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (MYSQL.isRunning()) MYSQL.stop();
            if (REDIS.isRunning()) REDIS.stop();
        }));
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
    }

    @BeforeAll
    static void initSchemaOnce() throws Exception {
        if (!schemaInitialized) {
            synchronized (AbstractMySqlContainerIntegrationTest.class) {
                if (!schemaInitialized) {
                    System.out.println("[IT Base] Initializing shared MySQL schema...");
                    executeSql("sql/init_database.sql");
                    schemaInitialized = true;
                    System.out.println("[IT Base] Schema ready (database=" + MYSQL.getDatabaseName() + ")");
                }
            }
        }
    }

    @AfterAll
    static void cleanupData() throws Exception {
        // 不删除 Schema，只清理测试数据便于后续测试类复用
        try (var conn = java.sql.DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var stmt = conn.createStatement()) {
            stmt.execute("SET FOREIGN_KEY_CHECKS = 0");
            stmt.execute("DELETE FROM pro_idempotency_record");
            stmt.execute("DELETE FROM pro_status_transition_log");
            stmt.execute("DELETE FROM pro_message_consume_log");
            stmt.execute("DELETE FROM qms_inspection");
            stmt.execute("DELETE FROM qms_defect");
            stmt.execute("DELETE FROM pro_work_process");
            stmt.execute("DELETE FROM pro_work_order");
            stmt.execute("DELETE FROM user_refresh_token");
            stmt.execute("DELETE FROM sys_oper_log");
            stmt.execute("DELETE FROM base_bom");
            stmt.execute("DELETE FROM base_route");
            stmt.execute("DELETE FROM base_equipment");
            stmt.execute("DELETE FROM qms_inspection_item");
            stmt.execute("DELETE FROM andon_alert");
            stmt.execute("DELETE FROM eqm_maintenance");
            stmt.execute("DELETE FROM eqm_repair_order");
            stmt.execute("DELETE FROM wms_stock_doc");
            stmt.execute("DELETE FROM wms_inventory");
            stmt.execute("DELETE FROM wms_stock_flow");
            stmt.execute("DELETE FROM wms_location");
            stmt.execute("DELETE FROM base_item");
            stmt.execute("DELETE FROM base_process");
            stmt.execute("DELETE FROM base_work_unit");
            stmt.execute("DELETE FROM erp_sync_log");
            stmt.execute("DELETE FROM saf_training");
            stmt.execute("DELETE FROM saf_safety_event");
            stmt.execute("SET FOREIGN_KEY_CHECKS = 1");
        } catch (Exception e) {
            System.err.println("[IT Base] cleanup warning: " + e.getMessage());
        }
    }

    // ==================== SQL 执行工具 ====================

    static void executeSql(String resourcePath) throws Exception {
        Path sqlFile = resolveSqlPath(resourcePath);
        System.out.println("[IT Base] Executing: " + sqlFile.getFileName() + " (" + Files.size(sqlFile) + " bytes)");
        MountableFile mf = MountableFile.forHostPath(sqlFile);
        MYSQL.copyFileToContainer(mf, "/tmp/it_exec.sql");
        var result = MYSQL.execInContainer(
                "mysql", "-u", MYSQL.getUsername(), "-p" + MYSQL.getPassword(),
                "--default-character-set=utf8mb4", MYSQL.getDatabaseName(),
                "-e", "source /tmp/it_exec.sql");
        String stderr = result.getStderr();
        if (stderr != null && !stderr.isEmpty()
                && !stderr.contains("Using a password on the command line interface can be insecure")) {
            System.err.println("[IT Base] stderr: " + stderr);
        }
        if (result.getExitCode() != 0) {
            throw new RuntimeException("[IT Base] SQL FAILED (exit " + result.getExitCode() + "): " + stderr);
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
