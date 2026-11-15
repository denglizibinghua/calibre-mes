package com.bikeassembly.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bikeassembly.TestcontainersSupport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Validates the actual initialization/reset SQL against isolated PostgreSQL 16, never a business schema. */
class DemoSeedIntegrationTest {
    static final String SCHEMA="mes_seed_test_"+UUID.randomUUID().toString().replace("-","");
    static final List<String> PRESERVED=List.of("sys_user","sys_role","sys_menu","sys_role_menu","sys_config","sys_user_password_history","user_refresh_token");
    static PostgreSQLContainer<?> postgres;
    static String url,user,password;
    static Connection connection;
    static Path root;
    static Map<String,String> identitySnapshot;

    @BeforeAll
    static void initializeDatabase() throws Exception {
        root=Path.of("..").toAbsolutePath().normalize();
        TestcontainersSupport.configureDockerApiVersion();
        postgres=new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName(SCHEMA);
        postgres.start();
        url=postgres.getJdbcUrl();user=postgres.getUsername();password=postgres.getPassword();
        executeInitScript();
        connection=DriverManager.getConnection(url,user,password);
        assertEquals(SCHEMA,connection.getCatalog());
        // Existing deployments need not use the IDs/nicknames from the original init script.
        connection.createStatement().executeUpdate("UPDATE sys_user SET user_id=user_id+100,nickname=nickname||'（保留验证）' WHERE username IN ('planner','operator01','inspector01','maintainer01')");
        identitySnapshot=fingerprints();
    }

    @BeforeEach
    void resetFixtures() throws Exception { resetDemoSeed();assertEquals(identitySnapshot,fingerprints()); }

    @AfterAll
    static void cleanup() throws Exception {
        if(connection!=null) connection.close();
        if(postgres!=null) postgres.stop();
    }

    /** init_database.sql contains a plpgsql function body, so execute it with psql inside the container. */
    static void executeInitScript() throws Exception {
        Path init=root.resolve("sql/init_database.sql");
        postgres.copyFileToContainer(MountableFile.forHostPath(init),"/tmp/init_database.sql");
        var result=postgres.execInContainer("psql","-v","ON_ERROR_STOP=1","-U",user,"-d",SCHEMA,"-f","/tmp/init_database.sql");
        if(result.getExitCode()!=0) {
            throw new IllegalStateException("init_database.sql failed (exit "+result.getExitCode()+"): "+result.getStderr());
        }
    }

    @Test
    void generatedCountsAndRelationshipsMatchManifest() throws Exception {
        verifyCounts();
        // A fresh JDBC connection must see the same schema regardless of connection settings.
        try(Connection fresh=DriverManager.getConnection(url,user,password)) {
            ScriptUtils.executeSqlScript(fresh,new FileSystemResource(root.resolve("sql/verify_database.sql")));
        }
    }

    @Test
    void repeatedResetRemovesOldRecordsAndPreservesIdentityTables() throws Exception {
        connection.createStatement().executeUpdate("INSERT INTO base_item(item_id,item_code,item_name,item_type,unit,status) VALUES (999,'OLD-FIXTURE','待清理记录','M','件','0')");
        resetDemoSeed();verifyCounts();
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

    /**
     * dev_seed_full.sql contains a plpgsql DO $$ ... $$ guard, which Spring's ScriptUtils cannot split.
     * Run it with psql inside the disposable container instead.
     */
    static void resetDemoSeed() throws Exception {
        Path seed = root.resolve("sql/dev_seed_full.sql");
        postgres.copyFileToContainer(MountableFile.forHostPath(seed), "/tmp/dev_seed_full.sql");
        var result = postgres.execInContainer("psql", "-v", "ON_ERROR_STOP=1",
                "-U", user, "-d", SCHEMA, "-f", "/tmp/dev_seed_full.sql");
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("dev_seed_full.sql failed (exit " + result.getExitCode() + "): " + result.getStderr());
        }
    }

    static void verifyCounts() throws Exception {
        JsonNode manifest=new ObjectMapper().readTree(root.resolve("sql/demo-seed-manifest.json").toFile());
        var entries=manifest.get("counts").fields();
        while(entries.hasNext()) {
            var entry=entries.next();
            try(ResultSet rows=connection.createStatement().executeQuery("SELECT COUNT(*) FROM "+entry.getKey())) {
                rows.next();assertEquals(entry.getValue().asLong(),rows.getLong(1),entry.getKey());
            }
        }
    }

    static Map<String,String> fingerprints() throws Exception {
        Map<String,String> output=new LinkedHashMap<>();
        for(String table:PRESERVED) {
            List<String> values=new ArrayList<>();
            try(ResultSet rows=connection.createStatement().executeQuery("SELECT * FROM "+table)) {
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
