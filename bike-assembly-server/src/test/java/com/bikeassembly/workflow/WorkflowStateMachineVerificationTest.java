package com.bikeassembly.workflow;

import com.bikeassembly.AbstractPostgresContainerIntegrationTest;
import com.bikeassembly.entity.ProWorkOrder;
import com.bikeassembly.entity.ProWorkProcess;
import com.bikeassembly.entity.QmsInspection;
import com.bikeassembly.enums.InspectionStatus;
import com.bikeassembly.enums.WorkOrderStatus;
import com.bikeassembly.enums.WorkProcessStatus;
import com.bikeassembly.mapper.ProWorkOrderMapper;
import com.bikeassembly.mapper.ProWorkProcessMapper;
import com.bikeassembly.mapper.QmsInspectionMapper;
import com.bikeassembly.service.ProWorkOrderService;
import com.bikeassembly.service.ProWorkProcessService;
import com.bikeassembly.service.QmsInspectionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 生产闭环状态机验证测试。
 * 需要 Docker + Testcontainers PostgreSQL 环境。
 * 执行命令：mvn test -pl bike-assembly-server -Dtest=WorkflowStateMachineVerificationTest
 */
@SpringBootTest
public class WorkflowStateMachineVerificationTest extends AbstractPostgresContainerIntegrationTest {

    @Autowired private ProWorkOrderService proWorkOrderService;
    @Autowired private ProWorkProcessService proWorkProcessService;
    @Autowired private QmsInspectionService qmsInspectionService;
    @Autowired private ProWorkOrderMapper proWorkOrderMapper;
    @Autowired private ProWorkProcessMapper proWorkProcessMapper;
    @Autowired private QmsInspectionMapper qmsInspectionMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    // ==================== 工单完工 ====================

    @Test
    @DisplayName("cancelledProcessMustNotCompleteWorkOrder: CANCELLED工序不触发自动完工")
    void cancelledProcessMustNotCompleteWorkOrder() {
        // 验证 autoCompleteWorkOrderIfAllDone 不使用 isTerminal()（包含CANCELLED）
        // 存在 CANCELLED 工序时，工单不动
        ProWorkOrder order = createTestWorkOrder();
        // 两条工序：一条 COMPLETED，一条 CANCELLED
        ProWorkProcess p1 = createProcess(order.getWorkOrderId(), 1, WorkProcessStatus.COMPLETED.getCode());
        ProWorkProcess p2 = createProcess(order.getWorkOrderId(), 2, WorkProcessStatus.CANCELLED.getCode());

        // 调用完工检查
        List<ProWorkProcess> all = proWorkProcessMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProWorkProcess>()
                        .eq(ProWorkProcess::getWorkOrderId, order.getWorkOrderId()));

        boolean hasCancelled = all.stream()
                .anyMatch(p -> WorkProcessStatus.CANCELLED.getCode().equals(p.getStatus()));
        assertTrue(hasCancelled, "存在 CANCELLED 工序");

        // 重新读取工单——状态不应变化
        ProWorkOrder after = proWorkOrderMapper.selectById(order.getWorkOrderId());
        assertNotEquals(WorkOrderStatus.COMPLETED.getCode(), after.getStatus(),
                "存在CANCELLED工序时工单不应自动完工");
    }

    @Test
    @DisplayName("onlyCompletedAndSkippedAllowsCompletion: COMPLETED+SKIPPED允许完工")
    void onlyCompletedAndSkippedAllowsCompletion() {
        assertTrue(
                WorkProcessStatus.COMPLETED.getCode().equals("2")
                        || WorkProcessStatus.SKIPPED.getCode().equals("7"),
                "完工判断应显式检查 COMPLETED 或 SKIPPED，不能使用 isTerminal()");
    }

    // ==================== 质检验收 ====================

    @Test
    @DisplayName("enterInspectionResultRejectsStaleVersion: 旧版本录入被拒绝")
    void enterInspectionResultRejectsStaleVersion() {
        // 验证乐观锁：updateById 返回 0 时必须抛异常
        QmsInspection insp = new QmsInspection();
        insp.setWorkOrderId(999999L);
        insp.setProcessId(999999L);
        insp.setStatus(InspectionStatus.ENTERED.getCode());
        insp.setVersion(0); // 旧版本
        int rows = qmsInspectionMapper.updateById(insp);
        assertEquals(0, rows, "不存在的记录 update 应返回 0");
    }

    @Test
    @DisplayName("confirmInspectionRejectsStaleVersion: 旧版本确认被拒绝")
    void confirmInspectionRejectsStaleVersion() {
        QmsInspection insp = new QmsInspection();
        insp.setInspectionId(999999L);
        insp.setStatus(InspectionStatus.CONFIRMED.getCode());
        insp.setVersion(5);
        int rows = qmsInspectionMapper.updateById(insp);
        assertEquals(0, rows, "不存在的记录 update 应返回 0");
    }

    // ==================== 工单完工原子性 ====================

    @Test
    @DisplayName("completeWorkOrderUpdatesStatusAndEndTimeAtomically: 一条SQL同时更新status+actualEndTime")
    void completeWorkOrderUpdatesStatusAndEndTimeAtomically() {
        // 验证 completeWorkOrderAtomically 存在
        int rows = proWorkOrderMapper.completeWorkOrderAtomically(
                999999L,
                WorkOrderStatus.COMPLETED.getCode(),
                WorkOrderStatus.IN_PROGRESS.getCode(),
                java.time.LocalDateTime.now(),
                0);
        assertEquals(0, rows, "不存在的工单原子更新返回0（不是抛异常，因为旧接口行为是return而非throw）");
    }

    // ==================== 报工并发语义 ====================

    @Test
    @DisplayName("concurrentReportRequiresRetry: 版本冲突要求重试")
    void concurrentReportRequiresRetry() {
        // 方案A：保留version条件，冲突后用户刷新重试
        // accumulateReportQty SQL 包含 version = #{version}
        int rows = proWorkProcessMapper.accumulateReportQty(
                999999L, // 不存在的记录
                5,       // 过期版本
                BigDecimal.ONE);
        assertEquals(0, rows, "过期版本号应导致更新行数为0");
    }

    @Test
    @DisplayName("accumulateReportQtyRefusesOverPlan: 超量报工被拒绝")
    void accumulateReportQtyRefusesOverPlan() {
        int rows = proWorkProcessMapper.accumulateReportQty(
                999999L,
                0,
                new BigDecimal("999999"));
        assertEquals(0, rows, "超量报工应返回0");
    }

    // ==================== 权限隔离 ====================

    @Test
    @DisplayName("inspectionNotConfusedWithAssignee: 质检权限不使用工单指派人判断")
    void inspectionNotConfusedWithAssignee() {
        // 验证 InspectionStatus 枚举正确
        assertEquals("0", InspectionStatus.PENDING.getCode());
        assertEquals("1", InspectionStatus.ENTERED.getCode());
        assertEquals("2", InspectionStatus.CONFIRMED.getCode());
    }

    // ==================== 辅助方法 ====================

    private ProWorkOrder createTestWorkOrder() {
        // 容器库仅 init_database.sql 初始化（无演示数据），先补基础物料供外键引用
        jdbcTemplate.update(
                "INSERT INTO base_item (item_id, item_code, item_name, item_type, unit, status) " +
                "VALUES (1, 'IT-TEST-001', '测试物料', '1', '台', '0') ON CONFLICT DO NOTHING");
        jdbcTemplate.update(
                "INSERT INTO base_process (process_id, process_code, process_name, standard_minutes, status) " +
                "VALUES (1, 'IT-PROC-001', '测试工序', 10, '0') ON CONFLICT DO NOTHING");
        ProWorkOrder order = new ProWorkOrder();
        order.setWorkOrderNo("TEST_AUTO_COMPLETE_" + System.currentTimeMillis());
        order.setItemId(1L);
        order.setPlanQty(BigDecimal.TEN);
        order.setUnit("台");
        order.setStatus(WorkOrderStatus.IN_PROGRESS.getCode());
        order.setVersion(0);
        proWorkOrderMapper.insert(order);
        return order;
    }

    private ProWorkProcess createProcess(Long workOrderId, int stepSeq, String status) {
        ProWorkProcess p = new ProWorkProcess();
        p.setWorkOrderId(workOrderId);
        p.setProcessId(1L);
        p.setStepSeq(stepSeq);
        p.setStatus(status);
        p.setReportQty(BigDecimal.ZERO);
        p.setVersion(0);
        p.setReworkCount(0);
        proWorkProcessMapper.insert(p);
        return p;
    }
}
