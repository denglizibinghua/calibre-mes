package com.bikeassembly.scan;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.nio.file.Path;
import java.sql.DriverManager;

@Testcontainers(disabledWithoutDocker = true)
class WorkOrderScanMapperIntegrationTest extends ScanMapperContract {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("bike_mes_scan_test").withUsername("scan_test").withPassword("scan_test");
    @DynamicPropertySource static void datasource(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", MYSQL::getJdbcUrl);
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
    }
    @BeforeAll static void schemaOnlyInContainer() throws Exception {
        Path schema = Path.of("../sql/init_database.sql");
        if (!schema.toFile().exists()) schema = Path.of("sql/init_database.sql");
        // 全量初始化仅在本测试专用容器库执行，绝不用于本地已有数据库。
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(schema));
        }
    }
}
