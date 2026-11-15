package com.bikeassembly.dashboard;

import com.bikeassembly.context.BaseContext;
import com.bikeassembly.dto.DashboardScreenQueryDTO;
import com.bikeassembly.service.DashboardScreenService;
import com.bikeassembly.vo.DashboardScreenVO;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Actual MySQL 8 mapper queries; unique fixtures are rolled back after each test. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DashboardScreenIntegrationTest {
    @Autowired private DashboardScreenService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Validator validator;
    private final long owner = 908870001L;
    private String prefix;
    private long rootUnit;
    private long station;
    private long item;
    private long process;
    private long activeOrder;
    private long otherOrder;
    private LocalDateTime now;

    @BeforeEach
    void seed() {
        prefix = "SCR_" + UUID.randomUUID().toString().substring(0, 8);
        now = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).withNano(0);
        rootUnit = unit("W", 0L, "车间");
        long line = unit("L", rootUnit, "产线");
        station = unit("S", line, "工位");
        jdbc.update("INSERT INTO base_item(item_code,item_name,item_type,unit,status) VALUES (?,?, 'P','台','0')", prefix, "看板测试产品");
        item = id("base_item", "item_id", "item_code", prefix);
        jdbc.update("INSERT INTO base_process(process_code,process_name,status) VALUES (?,?,'0')", prefix, "看板测试工序");
        process = id("base_process", "process_id", "process_code", prefix);
        long yesterday = order("past", "2", owner, now.minusDays(1));
        long today = order("today", "2", owner, now);
        activeOrder = order("active", "1", owner, null);
        otherOrder = order("other", "1", owner + 1, null);
        order("future", "2", owner, now.plusDays(1));
        long available = equipment("ok", "0", "0");
        long fault = equipment("fault", "0", "1");
        equipment("disabled", "1", "0");
        step(activeOrder, 1, "2", available);
        step(activeOrder, 2, "1", available);
        step(otherOrder, 1, "6", fault);
        // Multiple steps must not multiply completed work-order counts.
        step(today, 1, "2", available);
        step(today, 2, "2", available);
        inspection(yesterday, "P", 1, "2", now.minusDays(1));
        inspection(activeOrder, "F", 1, "2", now.minusDays(1));
        inspection(activeOrder, "P", 2, "2", now.minusDays(1));
        inspection(activeOrder, "P", 1, "1", now.minusDays(1));
        inspection(activeOrder, "P", 1, "2", now.plusDays(1));
        inspection(activeOrder, "P", 1, "2", now.minusDays(40));
        jdbc.update("INSERT INTO qms_defect(work_order_id,defect_type,qty,status,create_time) VALUES (?, '制动异常',2,'0',?)", activeOrder, now.minusDays(1));
        jdbc.update("INSERT INTO andon_alert(alert_no,work_order_id,unit_id,alert_type,alert_level,status,reporter_user_id,create_time) VALUES (?,?,?,'缺料','2','0',?,?)", prefix, activeOrder, station, owner, now);
        BaseContext.setCurrentDataScope("ALL");
        BaseContext.setCurrentId(owner);
    }

    @AfterEach
    void clearContext() { BaseContext.removeAll(); }

    @Test
    void parentSelectionIncludesDescendantsAndUsesWorkOrdersNotProcessRows() {
        DashboardScreenVO screen = screen(rootUnit);
        assertEquals(2, screen.getSummary().getActiveWorkOrders());
        assertEquals(1, screen.getSummary().getTodayCompletedWorkOrders());
        assertEquals(2, screen.getTrend().stream().mapToLong(DashboardScreenVO.Trend::getCompleted).sum());
        assertEquals(7, screen.getTrend().size());
        assertEquals(screen.getStartDate().plusDays(6), screen.getEndDate());
        assertEquals(0, screen.getTrend().get(0).getCompleted());
        assertEquals(1, screen.getProcesses().size());
        assertEquals(1, screen.getProcesses().get(0).getRunning());
        assertEquals(1, screen.getProcesses().get(0).getAbnormal());
        assertEquals(2, screen.getWorkOrders().stream().filter(o -> o.getWorkOrderId() == activeOrder).findFirst().orElseThrow().getTotalSteps());
        assertEquals(1, screen.getAlerts().size());
        assertEquals(1, screen.getDefects().get(0).getValue());
    }

    @Test
    void initialQualityExcludesReinspectionUnconfirmedAndFutureSamples() {
        DashboardScreenVO screen = screen(rootUnit);
        assertEquals(2, screen.getQuality().getInspected());
        assertEquals(1, screen.getQuality().getPassed());
        assertEquals(new BigDecimal("50.0"), screen.getQuality().getFirstPassRate());
        assertEquals(2, screen.getTrend().stream().mapToLong(DashboardScreenVO.Trend::getInspected).sum());
        assertEquals(new BigDecimal("50.0"), screen.getEquipment().getAvailabilityRate());
        assertEquals(1, screen.getEquipment().getDisabled());
    }

    @Test
    void selfScopeCannotSeeOtherUsersOrdersOrTheirDevices() {
        BaseContext.setCurrentDataScope("SELF");
        DashboardScreenVO screen = screen(rootUnit);
        assertEquals("SELF", screen.getDataScope());
        assertEquals(1, screen.getSummary().getActiveWorkOrders());
        assertTrue(screen.getWorkOrders().stream().noneMatch(o -> o.getWorkOrderId() == otherOrder));
        assertEquals(0, screen.getProcesses().get(0).getAbnormal());
        assertEquals(0, screen.getEquipment().getFault());
        assertEquals(new BigDecimal("100.0"), screen.getEquipment().getAvailabilityRate());
    }

    @Test
    void missingIdentityFailsClosedAndEmptyRatiosAreNotOneHundredPercent() {
        BaseContext.removeAll();
        DashboardScreenVO screen = screen(rootUnit);
        assertEquals(0, screen.getSummary().getActiveWorkOrders());
        assertEquals(0, screen.getSummary().getOpenAlerts());
        assertTrue(screen.getWorkOrders().isEmpty());
        assertTrue(screen.getWorkUnits().isEmpty());
        assertTrue(screen.getProcesses().isEmpty());
        assertNull(screen.getQuality().getFirstPassRate());
        assertNull(screen.getEquipment().getAvailabilityRate());
    }

    @Test
    void aDataScopeWithoutAnAuthenticatedIdentityDoesNotGrantAllRecords() {
        BaseContext.removeCurrentId();
        assertEquals(0, screen(rootUnit).getSummary().getActiveWorkOrders());
    }

    @Test
    void unknownUnitProducesCalendarWithZeroValuesAndNoLeakedRows() {
        DashboardScreenVO screen = screen(Long.MAX_VALUE);
        assertEquals(7, screen.getTrend().size());
        assertTrue(screen.getTrend().stream().allMatch(p -> p.getCompleted() == 0 && p.getPlanned() == 0));
        assertEquals(0, screen.getSummary().getPendingInspections());
        assertTrue(screen.getAlerts().isEmpty());
        assertTrue(screen.getDefects().isEmpty());
    }

    @Test
    void queryRejectsUnboundedRangesAndNonPositiveUnits() {
        DashboardScreenQueryDTO query = new DashboardScreenQueryDTO();
        query.setDays(31);
        query.setWorkUnitId(0L);
        assertEquals(2, validator.validate(query).size());
        query.setDays(0);
        query.setWorkUnitId(null);
        assertEquals(1, validator.validate(query).size());
        query.setDays(30);
        assertTrue(validator.validate(query).isEmpty());
    }

    private DashboardScreenVO screen(Long unit) {
        DashboardScreenQueryDTO query = new DashboardScreenQueryDTO();
        query.setWorkUnitId(unit);
        return service.screen(query);
    }
    private long unit(String type, Long parent, String suffix) {
        String code = prefix + type;
        jdbc.update("INSERT INTO base_work_unit(unit_code,unit_name,unit_type,parent_id,status) VALUES (?,?,?,?,'0')", code, prefix + suffix, type, parent);
        return id("base_work_unit", "unit_id", "unit_code", code);
    }
    private long order(String suffix, String status, long assigned, LocalDateTime finished) {
        String code = prefix + suffix;
        jdbc.update("INSERT INTO pro_work_order(work_order_no,item_id,plan_qty,unit,work_unit_id,assignee_user_id,status,plan_end_time,actual_end_time) VALUES (?,?,10,'台',?,?,?,?,?)",
                code, item, station, assigned, status, now.minusHours(2), finished);
        return id("pro_work_order", "work_order_id", "work_order_no", code);
    }
    private long equipment(String suffix, String status, String running) {
        String code = prefix + suffix;
        jdbc.update("INSERT INTO base_equipment(equipment_code,equipment_name,unit_id,status,running_status) VALUES (?,?,?,?,?)", code, suffix, station, status, running);
        return id("base_equipment", "equipment_id", "equipment_code", code);
    }
    private void step(long order, int sequence, String status, long equipment) {
        jdbc.update("INSERT INTO pro_work_process(work_order_id,process_id,equipment_id,step_seq,status,report_qty,end_time) VALUES (?,?,?,?,?,10,?)", order, process, equipment, sequence, status, now);
    }
    private void inspection(long order, String result, int round, String status, LocalDateTime time) {
        jdbc.update("INSERT INTO qms_inspection(inspection_no,work_order_id,process_id,result,inspection_round,status,inspection_time,details_json) VALUES (?,?,?,?,?,?,?,'[]')", prefix + UUID.randomUUID().toString().substring(0, 8), order, process, result, round, status, time);
    }
    private long id(String table, String column, String key, String value) {
        // Identifiers are internal constants; fixture values are always bound parameters.
        return jdbc.queryForObject("SELECT " + column + " FROM " + table + " WHERE " + key + " = ?", Long.class, value);
    }
}
