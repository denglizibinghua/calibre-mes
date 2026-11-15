package com.bikeassembly.scan;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

/** 显式启用的本地回滚验证；复用现有dev配置，不重建表、不修改既有记录。 */
@EnabledIfEnvironmentVariable(named = "MES_SCAN_LOCAL_DB_TEST", matches = "true")
class WorkOrderScanLocalDatabaseTest extends ScanMapperContract {
    @DynamicPropertySource static void datasource(DynamicPropertyRegistry registry) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-dev.yml"));
        var properties = yaml.getObject();
        for (String field : new String[]{"url", "username", "password", "driver-class-name"}) {
            String key = "spring.datasource." + field;
            registry.add(key, () -> properties.getProperty(key));
        }
    }

    @Test void recordsExistingBusinessMetadataWithoutCredentials() throws Exception {
        var evidence = new LinkedHashMap<String, Object>();
        evidence.put("orders", jdbc.queryForList("SELECT wo.work_order_id,wo.work_order_no,wo.assignee_user_id,wo.status," +
                "(SELECT COUNT(*) FROM qms_inspection qi WHERE qi.work_order_id=wo.work_order_id AND qi.status='0') AS pending_count " +
                "FROM pro_work_order wo WHERE wo.work_order_no NOT LIKE 'SCAN-%' ORDER BY pending_count DESC,wo.work_order_id LIMIT 12"));
        evidence.put("accounts", jdbc.queryForList("SELECT u.user_id,u.username,u.nickname,u.role_id,r.role_key,u.status,u.must_change_password " +
                "FROM sys_user u LEFT JOIN sys_role r ON r.role_id=u.role_id WHERE u.username NOT LIKE 'scan\\_%' ORDER BY u.user_id"));
        evidence.put("permissions", jdbc.queryForList("SELECT DISTINCT r.role_key,m.perms FROM sys_role r " +
                "JOIN sys_role_menu rm ON rm.role_id=r.role_id JOIN sys_menu m ON m.menu_id=rm.menu_id " +
                "WHERE m.perms LIKE 'app:%' ORDER BY r.role_key,m.perms"));
        Path root = Path.of("").toAbsolutePath();
        if (root.getFileName().toString().equals("bike-assembly-server")) root = root.getParent();
        Path output = root.resolve(".run/work-order-scan/business-metadata.json");
        Files.createDirectories(output.getParent());
        new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.toFile(), evidence);
    }
}
