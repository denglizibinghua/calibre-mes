package com.bikeassembly.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Validates the actual initialization/reset SQL against isolated MySQL, never a business schema. */
class DemoSeedIntegrationTest {
    static final String SCHEMA="mes_seed_test_"+UUID.randomUUID().toString().replace("-","");
    static final List<String> PRESERVED=List.of("sys_user","sys_role","sys_menu","sys_role_menu","sys_config","sys_user_password_history","user_refresh_token");
    static MySQLContainer<?> mysql;
    static String adminUrl,url,user,password;
    static Connection connection;
    static Path root;
    static Map<String,String> identitySnapshot;

    @BeforeAll
    static void initializeDatabase() throws Exception {
        root=Path.of("..").toAbsolutePath().normalize();
        adminUrl=System.getenv("MES_REVIEW_MYSQL_URL");
        if(adminUrl==null||adminUrl.isBlank()) {
            mysql=new MySQLContainer<>("mysql:8.0").withDatabaseName(SCHEMA);mysql.start();
            url=mysql.getJdbcUrl();user=mysql.getUsername();password=mysql.getPassword();
        } else {
            if(!adminUrl.matches("jdbc:mysql://[^/]+/")) throw new IllegalArgumentException("Use a server URL without a database name");
            user=System.getenv().getOrDefault("MES_REVIEW_MYSQL_USER","root");
            password=System.getenv().getOrDefault("MES_REVIEW_MYSQL_PASSWORD","");
            try(Connection admin=DriverManager.getConnection(adminUrl,user,password)) {
                admin.createStatement().execute("CREATE DATABASE `"+SCHEMA+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            }
            url=adminUrl+SCHEMA+"?serverTimezone=Asia/Shanghai&connectTimeout=5000&socketTimeout=60000";
        }
        connection=DriverManager.getConnection(url,user,password);
        assertEquals(SCHEMA,connection.getCatalog());
        script("init_database.sql");
        // Existing deployments need not use the IDs/nicknames from the original init script.
        connection.createStatement().executeUpdate("UPDATE sys_user SET user_id=user_id+100,nickname=CONCAT(nickname,'（保留验证）') WHERE username IN ('planner','operator01','inspector01','maintainer01')");
        identitySnapshot=fingerprints();
    }

    @BeforeEach
    void resetFixtures() throws Exception { script("dev_seed_full.sql");assertEquals(identitySnapshot,fingerprints()); }

    @AfterAll
    static void cleanup() throws Exception {
        if(connection!=null) connection.close();
        if(mysql!=null) mysql.stop();
        else if(url!=null&&SCHEMA.matches("mes_seed_test_[a-f0-9]{32}")) {
            try(Connection admin=DriverManager.getConnection(adminUrl,user,password)) {
                admin.createStatement().execute("DROP DATABASE `"+SCHEMA+"`");
            }
        }
    }

    @Test
    void generatedCountsAndRelationshipsMatchManifest() throws Exception {
        verifyCounts();
        // A fresh JDBC/mysql session may have a different default connection collation.
        try(Connection fresh=DriverManager.getConnection(url,user,password)) {
            ScriptUtils.executeSqlScript(fresh,new FileSystemResource(root.resolve("sql/verify_database.sql")));
        }
    }

    @Test
    void repeatedResetRemovesOldRecordsAndPreservesIdentityTables() throws Exception {
        connection.createStatement().executeUpdate("INSERT INTO base_item(item_id,item_code,item_name,item_type,unit,status) VALUES (999,'OLD-FIXTURE','待清理记录','M','件','0')");
        script("dev_seed_full.sql");verifyCounts();
        assertEquals(identitySnapshot,fingerprints());
    }

    @ParameterizedTest
    @ValueSource(strings={
            "UPDATE qms_inspection SET process_record_id=NULL WHERE inspection_id=1",
            "UPDATE base_equipment SET running_status='0' WHERE equipment_id=17",
            "UPDATE wms_stock_flow SET after_qty=after_qty+1 WHERE flow_id=1",
            "UPDATE pro_work_process SET report_qty=0 WHERE process_record_id=1"})
    void validationRejectsBrokenRelationshipsAndBalances(String mutation) throws Exception {
        connection.setAutoCommit(false);
        try {
            connection.createStatement().executeUpdate(mutation);
            assertThrows(org.springframework.jdbc.datasource.init.ScriptStatementFailedException.class,()->script("verify_database.sql"));
        } finally {connection.rollback();connection.setAutoCommit(true);}
        script("verify_database.sql");
    }

    static void script(String name) {ScriptUtils.executeSqlScript(connection,new FileSystemResource(root.resolve("sql").resolve(name)));}

    static void verifyCounts() throws Exception {
        JsonNode manifest=new ObjectMapper().readTree(root.resolve("sql/demo-seed-manifest.json").toFile());
        var entries=manifest.get("counts").fields();
        while(entries.hasNext()) {
            var entry=entries.next();
            try(ResultSet rows=connection.createStatement().executeQuery("SELECT COUNT(*) FROM `"+entry.getKey()+"`")) {
                rows.next();assertEquals(entry.getValue().asLong(),rows.getLong(1),entry.getKey());
            }
        }
    }

    static Map<String,String> fingerprints() throws Exception {
        Map<String,String> output=new LinkedHashMap<>();
        for(String table:PRESERVED) {
            List<String> values=new ArrayList<>();
            try(ResultSet rows=connection.createStatement().executeQuery("SELECT * FROM `"+table+"`")) {
                while(rows.next()) {
                    StringBuilder row=new StringBuilder();
                    for(int i=1;i<=rows.getMetaData().getColumnCount();i++) {
                        String value=rows.getString(i);row.append(value==null?"-1:":value.length()+":"+value).append('|');
                    }
                    values.add(row.toString());
                }
            }
            Collections.sort(values);
            output.put(table,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(String.join("\n",values).getBytes(StandardCharsets.UTF_8))));
        }
        return output;
    }
}
