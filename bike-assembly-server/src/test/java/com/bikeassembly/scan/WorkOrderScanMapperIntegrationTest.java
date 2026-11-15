package com.bikeassembly.scan;

import com.bikeassembly.TestcontainersSupport;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;
import java.nio.file.Path;

@Testcontainers(disabledWithoutDocker = true)
class WorkOrderScanMapperIntegrationTest extends ScanMapperContract {
    static {
        TestcontainersSupport.configureDockerApiVersion();
    }
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("bike_mes_scan_test").withUsername("scan_test").withPassword("scan_test");
    @DynamicPropertySource static void datasource(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }
    @BeforeAll static void schemaOnlyInContainer() throws Exception {
        Path schema = Path.of("../sql/init_database.sql");
        if (!schema.toFile().exists()) schema = Path.of("sql/init_database.sql");
        // 全量初始化仅在本测试专用容器库执行，绝不用于本地已有数据库。
        POSTGRES.copyFileToContainer(MountableFile.forHostPath(schema), "/tmp/scan_schema.sql");
        var result = POSTGRES.execInContainer("psql", "-v", "ON_ERROR_STOP=1",
                "-U", POSTGRES.getUsername(), "-d", POSTGRES.getDatabaseName(), "-f", "/tmp/scan_schema.sql");
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("init_database.sql failed (exit " + result.getExitCode()
                    + "): " + result.getStderr());
        }
    }
}
