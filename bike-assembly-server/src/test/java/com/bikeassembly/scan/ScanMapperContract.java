package com.bikeassembly.scan;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bikeassembly.config.MybatisPlusConfig;
import com.bikeassembly.mapper.ProWorkOrderMapper;
import com.bikeassembly.mapper.ProWorkProcessMapper;
import com.bikeassembly.mapper.QmsInspectionMapper;
import org.junit.jupiter.api.*;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** 同一组真实Mapper契约分别由容器库和可选本地库执行，所有测试数据随事务回滚。 */
@SpringJUnitConfig(ScanMapperContract.Config.class)
@TestPropertySource(properties = {"mybatis-plus.mapper-locations=classpath*:mapper/*.xml",
        "mybatis-plus.configuration.map-underscore-to-camel-case=true"})
@Transactional
abstract class ScanMapperContract {
    @Configuration
    @EnableTransactionManagement
    @Import({DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class, MybatisPlusAutoConfiguration.class, MybatisPlusConfig.class})
    @MapperScan("com.bikeassembly.mapper")
    static class Config {}

    @Autowired JdbcTemplate jdbc;
    @Autowired ProWorkOrderMapper orders;
    @Autowired QmsInspectionMapper inspections;
    @Autowired ProWorkProcessMapper processes;
    long owner, otherInspector, orderId, otherOrder, processId;
    String orderNo;

    @BeforeEach void fixtures() {
        String unique = UUID.randomUUID().toString().replace("-", "");
        String hash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("Scan@Test123456");
        jdbc.update("INSERT INTO sys_user(username,password,nickname) VALUES (?,?,?)", "scan_a_" + unique, hash, "扫码测试本人");
        owner = jdbc.queryForObject("SELECT user_id FROM sys_user WHERE username=?", Long.class, "scan_a_" + unique);
        jdbc.update("INSERT INTO sys_user(username,password,nickname) VALUES (?,?,?)", "scan_b_" + unique, hash, "扫码测试他人");
        otherInspector = jdbc.queryForObject("SELECT user_id FROM sys_user WHERE username=?", Long.class, "scan_b_" + unique);
        jdbc.update("INSERT INTO base_item(item_code,item_name,item_type,unit) VALUES (?,?,'P','台')", "scan_" + unique, "扫码集成测试产品");
        long item = jdbc.queryForObject("SELECT item_id FROM base_item WHERE item_code=?", Long.class, "scan_" + unique);
        jdbc.update("INSERT INTO base_process(process_code,process_name) VALUES (?,?)", "scan_" + unique, "扫码测试工序");
        processId = jdbc.queryForObject("SELECT process_id FROM base_process WHERE process_code=?", Long.class, "scan_" + unique);
        orderNo = "SCAN-" + unique;
        jdbc.update("INSERT INTO pro_work_order(work_order_no,item_id,plan_qty,unit,assignee_user_id) VALUES (?,?,1,'台',?)", orderNo, item, owner);
        orderId = orders.selectByWorkOrderNo(orderNo).getWorkOrderId();
        jdbc.update("INSERT INTO pro_work_order(work_order_no,item_id,plan_qty,unit,assignee_user_id) VALUES (?,?,1,'台',?)", orderNo + "-B", item, otherInspector);
        otherOrder = orders.selectByWorkOrderNo(orderNo + "-B").getWorkOrderId();
        addInspection(orderNo + "-open", orderId, null, "0");
        addInspection(orderNo + "-mine", orderId, owner, "0");
        addInspection(orderNo + "-other", orderId, otherInspector, "0");
        addInspection(orderNo + "-done", orderId, owner, "1");
        addInspection(orderNo + "-B-open", otherOrder, null, "0");
        jdbc.update("INSERT INTO pro_work_process(work_order_id,process_id) VALUES (?,?)", orderId, processId);
        jdbc.update("INSERT INTO pro_work_process(work_order_id,process_id) VALUES (?,?)", otherOrder, processId);
    }
    private void addInspection(String no, long workOrder, Long inspector, String status) {
        jdbc.update("INSERT INTO qms_inspection(inspection_no,work_order_id,process_id,inspector_user_id,status,details_json) VALUES (?,?,?,?,?,'[]')",
                no, workOrder, processId, inspector, status);
    }
    @Test void exactNumberNeverMatchesPrefixOrInjection() {
        assertEquals(orderId, orders.selectByWorkOrderNo(orderNo).getWorkOrderId());
        assertNull(orders.selectByWorkOrderNo(orderNo.substring(0, orderNo.length()-1)));
        assertNull(orders.selectByWorkOrderNo("' OR 1=1 --"));
    }
    @Test void ownerCannotSeeTaskClaimedByOtherInspector() {
        var page = inspections.selectAppPageByCondition(new Page<>(1,20), owner, false, false, false, orderId, null, "0");
        assertEquals(2, page.getTotal());
        assertTrue(page.getRecords().stream().noneMatch(task -> otherInspector == (task.getInspectorUserId() == null ? 0 : task.getInspectorUserId())));
        assertEquals(2, inspections.countAppPending(orderId, owner, false, false));
    }
    @Test void unassignedPoolNeedsPermissionAndIsLimitedToTargetOrder() {
        assertEquals(0, inspections.countAppPending(otherOrder, owner, false, false));
        assertEquals(1, inspections.countAppPending(otherOrder, owner, false, true));
        var page = inspections.selectAppPageByCondition(new Page<>(1,20), owner, false, true, true, otherOrder, processId, "0");
        assertEquals(1, page.getTotal());
        assertTrue(page.getRecords().stream().allMatch(task -> task.getWorkOrderId().equals(otherOrder)));
    }
    @Test void viewAllStillFiltersOrderStateAndUsesRealPagination() {
        var page = inspections.selectAppPageByCondition(new Page<>(1,1), owner, true, false, false, orderId, processId, "0");
        assertEquals(3, page.getTotal());
        assertEquals(1, page.getRecords().size());
        var next = inspections.selectAppPageByCondition(new Page<>(2,1), owner, true, false, false, orderId, processId, "0");
        assertNotEquals(page.getRecords().get(0).getInspectionId(), next.getRecords().get(0).getInspectionId());
        assertEquals(3, inspections.countAppPending(orderId, owner, true, false));
    }
    @Test void processFilterAndOwnershipAreBothInSql() {
        assertEquals(1, processes.selectAppPageByCondition(new Page<>(1,20), owner, orderId).getTotal());
        assertEquals(0, processes.selectAppPageByCondition(new Page<>(1,20), owner, otherOrder).getTotal());
        assertEquals(1, processes.selectAppPageByCondition(new Page<>(1,20), null, otherOrder).getTotal());
    }
}
