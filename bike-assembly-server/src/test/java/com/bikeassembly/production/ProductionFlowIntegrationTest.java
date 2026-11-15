package com.bikeassembly.production;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bikeassembly.AbstractPostgresContainerIntegrationTest;
import com.bikeassembly.context.BaseContext;
import com.bikeassembly.dto.ProWorkOrderDTO;
import com.bikeassembly.entity.ProWorkOrder;
import com.bikeassembly.entity.ProWorkProcess;
import com.bikeassembly.entity.QmsInspection;
import com.bikeassembly.enums.InspectionStatus;
import com.bikeassembly.enums.QcResult;
import com.bikeassembly.enums.WorkOrderStatus;
import com.bikeassembly.enums.WorkProcessStatus;
import com.bikeassembly.mapper.ProWorkOrderMapper;
import com.bikeassembly.mapper.ProWorkProcessMapper;
import com.bikeassembly.mapper.QmsInspectionMapper;
import com.bikeassembly.service.ProWorkOrderService;
import com.bikeassembly.service.ProWorkProcessService;
import com.bikeassembly.service.QmsInspectionService;
import com.bikeassembly.vo.ProWorkOrderVO;
import com.bikeassembly.vo.ProWorkProcessVO;
import com.bikeassembly.vo.QmsInspectionVO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 生产流程完整集成测试。
 * 覆盖：工单创建→指派→下达→工序生成→开始→报工→质检→返工→完工→关闭
 *
 * 使用容器化 PostgreSQL 16 数据库，每次测试独立创建工单。
 *
 * 执行命令：
 *   mvn test -pl bike-assembly-server -Dtest=ProductionFlowIntegrationTest
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("生产流程完整集成测试")
public class ProductionFlowIntegrationTest extends AbstractPostgresContainerIntegrationTest {

    @BeforeAll
    static void seedProductionFlow() {
        executeSql("sql/test/seed_production_flow.sql");
    }

    private static final Long ADMIN_USER_ID = 1L;
    private static final Long OPERATOR_USER_ID = 1002L;
    private static final Long INSPECTOR_USER_ID = 1003L;
    private static final Long ITEM_ID = 1001L;
    private static final Long ROUTE_ID = 1001L;
    private static final Long WORK_UNIT_ID = 1001L;
    private static final Long PROCESS_1_ID = 2L;   // 车轮安装(无QC) step1
    private static final Long PROCESS_2_ID = 6L;   // 刹车调试(有QC) step2

    @Autowired private ProWorkOrderService proWorkOrderService;
    @Autowired private ProWorkProcessService proWorkProcessService;
    @Autowired private QmsInspectionService qmsInspectionService;
    @Autowired private ProWorkOrderMapper proWorkOrderMapper;
    @Autowired private ProWorkProcessMapper proWorkProcessMapper;
    @Autowired private QmsInspectionMapper qmsInspectionMapper;

    // ========================================================================
    // T01-T16 主流程：单一测试方法保证状态串联
    // ========================================================================

    @Test
    @Order(1)
    @DisplayName("T01-T16: 完整生产流程 - 创建→指派→下达→开始→报工→质检→返工→完工→关闭")
    void fullProductionFlow() {
        // ========== T01: 创建工单 ==========
        setUser(ADMIN_USER_ID);
        ProWorkOrderDTO dto = new ProWorkOrderDTO();
        dto.setWorkOrderNo("TEST-WO-FLOW-" + System.currentTimeMillis());
        dto.setItemId(ITEM_ID);
        dto.setPlanQty(BigDecimal.TEN);
        dto.setUnit("台");
        dto.setRouteId(ROUTE_ID);
        dto.setWorkUnitId(WORK_UNIT_ID);
        ProWorkOrderVO wo = proWorkOrderService.saveProWorkOrder(dto);
        Long woId = wo.getWorkOrderId();
        assertNotNull(woId);
        assertEquals(WorkOrderStatus.CREATED.getCode(), wo.getStatus(), "T01: 状态=已创建(0)");
        assertEquals(0, BigDecimal.TEN.compareTo(wo.getPlanQty()), "T01: 计划数量=10");
        System.out.println("[T01 PASS] 工单创建: " + woId);

        // ========== T02: 指派操作员 ==========
        wo = proWorkOrderService.assignAssignee(woId, OPERATOR_USER_ID);
        assertEquals(OPERATOR_USER_ID, wo.getAssigneeUserId(), "T02: 指派成功");
        assertEquals(WorkOrderStatus.CREATED.getCode(), wo.getStatus(), "T02: 指派不改变状态");
        System.out.println("[T02 PASS] 操作员指派完成");

        // ========== T03: 下达工单 ==========
        wo = proWorkOrderService.releaseWorkOrder(woId);
        assertEquals(WorkOrderStatus.RELEASED.getCode(), wo.getStatus(), "T03: 状态=已下达(4)");
        List<ProWorkProcess> processes = getProcessesByWorkOrder(woId);
        assertEquals(2, processes.size(), "T03: 生成2道工序");
        ProWorkProcess p1 = processes.get(0);
        ProWorkProcess p2 = processes.get(1);
        assertEquals(WorkProcessStatus.READY.getCode(), p1.getStatus(), "T03: 第1道=9(可开工)");
        assertEquals(1, p1.getStepSeq());
        assertEquals(WorkProcessStatus.PENDING.getCode(), p2.getStatus(), "T03: 第2道=0(待开始)");
        assertEquals(2, p2.getStepSeq());
        System.out.println("[T03 PASS] 工单下达, p1=" + p1.getProcessRecordId() + ", p2=" + p2.getProcessRecordId());

        // ========== T04: 禁止越序开工 ==========
        setUser(OPERATOR_USER_ID);
        assertThrows(Exception.class,
                () -> proWorkProcessService.startProWorkProcess(p2.getProcessRecordId()),
                "T04: 越序开工应失败");
        ProWorkProcess p2After = proWorkProcessMapper.selectById(p2.getProcessRecordId());
        assertEquals(WorkProcessStatus.PENDING.getCode(), p2After.getStatus(), "T04: 第二道仍为待开始");
        System.out.println("[T04 PASS] 越序开工被拒绝");

        // ========== T05: 开始第一道工序 ==========
        ProWorkProcessVO p1Vo = proWorkProcessService.startProWorkProcess(p1.getProcessRecordId());
        assertEquals(WorkProcessStatus.IN_PROGRESS.getCode(), p1Vo.getStatus(), "T05: 工序=进行中(1)");
        assertNotNull(p1Vo.getStartTime(), "T05: 记录开始时间");
        ProWorkOrder orderCheck = proWorkOrderMapper.selectById(woId);
        assertEquals(WorkOrderStatus.IN_PROGRESS.getCode(), orderCheck.getStatus(), "T05: 工单=生产中(1)");
        assertNotNull(orderCheck.getActualStartTime(), "T05: 记录工单实际开始时间");
        p2After = proWorkProcessMapper.selectById(p2.getProcessRecordId());
        assertEquals(WorkProcessStatus.PENDING.getCode(), p2After.getStatus(), "T05: 第二道不变");
        System.out.println("[T05 PASS] 第一道工序开始, 工单进入生产");

        // ========== T06: 第一道部分报工 ==========
        proWorkProcessService.unifiedReport(p1.getProcessRecordId(), new BigDecimal("4"), null);
        ProWorkProcess p1Entity = proWorkProcessMapper.selectById(p1.getProcessRecordId());
        assertEquals(WorkProcessStatus.IN_PROGRESS.getCode(), p1Entity.getStatus(), "T06: 仍为进行中");
        assertEquals(0, new BigDecimal("4").compareTo(p1Entity.getReportQty()), "T06: 累计=4");
        System.out.println("[T06 PASS] 部分报工, 累计=4");

        // ========== T07: 第一道完成报工 ==========
        proWorkProcessService.unifiedReport(p1.getProcessRecordId(), new BigDecimal("6"), null);
        p1Entity = proWorkProcessMapper.selectById(p1.getProcessRecordId());
        assertEquals(WorkProcessStatus.COMPLETED.getCode(), p1Entity.getStatus(), "T07: 工序=已完成(2)");
        assertEquals(0, BigDecimal.TEN.compareTo(p1Entity.getReportQty()), "T07: 累计=10");
        p2After = proWorkProcessMapper.selectById(p2.getProcessRecordId());
        assertEquals(WorkProcessStatus.READY.getCode(), p2After.getStatus(), "T07: 第二道解锁=可开工(9)");
        // 无质检单
        long qcCount1 = countActiveInspections(woId, PROCESS_1_ID);
        assertEquals(0, qcCount1, "T07: 无质检工序不生成质检单");
        System.out.println("[T07 PASS] 第一道完成, 第二道解锁");

        // ========== T08: 开始第二道工序 ==========
        proWorkProcessService.startProWorkProcess(p2.getProcessRecordId());
        ProWorkProcess p2Entity = proWorkProcessMapper.selectById(p2.getProcessRecordId());
        assertEquals(WorkProcessStatus.IN_PROGRESS.getCode(), p2Entity.getStatus(), "T08: 第二道=进行中");
        System.out.println("[T08 PASS] 第二道开始");

        // ========== T09: 第二道满报→待检 ==========
        proWorkProcessService.unifiedReport(p2.getProcessRecordId(), BigDecimal.TEN, "操作员自检OK");
        p2Entity = proWorkProcessMapper.selectById(p2.getProcessRecordId());
        assertEquals(WorkProcessStatus.WAIT_INSPECTION.getCode(), p2Entity.getStatus(), "T09: 需质检工序满报后=待检(3)");
        assertEquals(QcResult.PENDING.getCode(), p2Entity.getQcResult(), "T09: qcResult=0(待检)");
        List<QmsInspection> inspections = qmsInspectionMapper.selectList(
                new LambdaQueryWrapper<QmsInspection>()
                        .eq(QmsInspection::getWorkOrderId, woId)
                        .eq(QmsInspection::getProcessId, PROCESS_2_ID));
        assertEquals(1, inspections.size(), "T09: 自动生成1张质检单");
        Long inspId = inspections.get(0).getInspectionId();
        assertEquals(InspectionStatus.PENDING.getCode(), inspections.get(0).getStatus(), "T09: 质检单=待检(0)");
        orderCheck = proWorkOrderMapper.selectById(woId);
        assertEquals(WorkOrderStatus.IN_PROGRESS.getCode(), orderCheck.getStatus(), "T09: 工单仍为生产中");
        System.out.println("[T09 PASS] 进入待检, 质检单=" + inspId);

        // ========== T10: 防止重复质检单 ==========
        long activeQcCount = countActiveInspections(woId, PROCESS_2_ID);
        assertEquals(1, activeQcCount, "T10: 同一工序同一时刻只有1张活动质检单");
        System.out.println("[T10 PASS] 活动质检单唯一");

        // ========== T11: 质检员录入结果 ==========
        setUser(INSPECTOR_USER_ID);
        com.bikeassembly.dto.QmsInspectionDTO inspDto = new com.bikeassembly.dto.QmsInspectionDTO();
        inspDto.setResult("P");
        inspDto.setDetailsJson("[{\"item\":\"外观检查\",\"result\":\"合格\",\"value\":\"无划痕\"}]");
        inspDto.setInspector("测试质检员");
        inspDto.setInspectorUserId(INSPECTOR_USER_ID);
        inspDto.setInspectionTime(java.time.LocalDateTime.now());
        QmsInspectionVO inspVo = qmsInspectionService.enterInspectionResult(inspId, inspDto);
        assertEquals(InspectionStatus.ENTERED.getCode(), inspVo.getStatus(), "T11: 质检单=已录入(1)");
        p2Entity = proWorkProcessMapper.selectById(p2.getProcessRecordId());
        assertEquals(WorkProcessStatus.WAIT_INSPECTION.getCode(), p2Entity.getStatus(), "T11: 工序仍为待检");
        System.out.println("[T11 PASS] 质检结果录入");

        // ========== T12: 质检确认合格 ==========
        qmsInspectionService.confirmInspection(inspId);
        QmsInspection inspEntity = qmsInspectionMapper.selectById(inspId);
        assertEquals(InspectionStatus.CONFIRMED.getCode(), inspEntity.getStatus(), "T12: 质检单=已确认(2)");
        p2Entity = proWorkProcessMapper.selectById(p2.getProcessRecordId());
        assertEquals(WorkProcessStatus.COMPLETED.getCode(), p2Entity.getStatus(), "T12: 工序=已完成(2)");
        assertEquals(QcResult.PASS.getCode(), p2Entity.getQcResult(), "T12: qcResult=1(合格)");
        orderCheck = proWorkOrderMapper.selectById(woId);
        assertEquals(WorkOrderStatus.COMPLETED.getCode(), orderCheck.getStatus(), "T12: 工单=已完工(2)");
        assertNotNull(orderCheck.getActualEndTime(), "T12: 记录实际完工时间");
        System.out.println("[T12 PASS] 质检合格→工序完成→工单完工");

        // ========== T16: 关闭工单 ==========
        setUser(ADMIN_USER_ID);
        wo = proWorkOrderService.closeWorkOrder(woId);
        assertEquals(WorkOrderStatus.CLOSED.getCode(), wo.getStatus(), "T16: 工单=已关闭(3)");
        System.out.println("[T16 PASS] 工单关闭");

        System.out.println("\n=== 完整生产流程 T01-T16 全部通过! ===");
    }

    // ========================================================================
    // T13-T15: 质检不合格→返工→复检合格
    // ========================================================================

    @Test
    @Order(2)
    @DisplayName("T13-T15: 质检不合格→返工→复检合格→完工")
    void reworkFlow() {
        setUser(ADMIN_USER_ID);

        // 创建+指派+下达
        ProWorkOrderDTO dto = new ProWorkOrderDTO();
        dto.setWorkOrderNo("TEST-WO-REWORK-" + System.currentTimeMillis());
        dto.setItemId(ITEM_ID);
        dto.setPlanQty(BigDecimal.TEN);
        dto.setUnit("台");
        dto.setRouteId(ROUTE_ID);
        dto.setWorkUnitId(WORK_UNIT_ID);
        ProWorkOrderVO wo = proWorkOrderService.saveProWorkOrder(dto);
        Long woId = wo.getWorkOrderId();
        proWorkOrderService.assignAssignee(woId, OPERATOR_USER_ID);
        proWorkOrderService.releaseWorkOrder(woId);

        // 获取工序
        List<ProWorkProcess> processes = getProcessesByWorkOrder(woId);
        Long p1Id = processes.get(0).getProcessRecordId();
        Long p2Id = processes.get(1).getProcessRecordId();

        // 完成第一道
        setUser(OPERATOR_USER_ID);
        proWorkProcessService.startProWorkProcess(p1Id);
        proWorkProcessService.unifiedReport(p1Id, BigDecimal.TEN, null);
        assertProcessStatus(p1Id, WorkProcessStatus.COMPLETED);

        // 第二道开始→报满→待检
        proWorkProcessService.startProWorkProcess(p2Id);
        proWorkProcessService.unifiedReport(p2Id, BigDecimal.TEN, "请质检");
        assertProcessStatus(p2Id, WorkProcessStatus.WAIT_INSPECTION);

        // 获取质检单
        List<QmsInspection> inspections = qmsInspectionMapper.selectList(
                new LambdaQueryWrapper<QmsInspection>()
                        .eq(QmsInspection::getWorkOrderId, woId)
                        .eq(QmsInspection::getProcessId, PROCESS_2_ID)
                        .eq(QmsInspection::getStatus, InspectionStatus.PENDING.getCode()));
        assertEquals(1, inspections.size());
        Long inspId = inspections.get(0).getInspectionId();

        // ========== T13: 质检不合格 ==========
        setUser(INSPECTOR_USER_ID);
        com.bikeassembly.dto.QmsInspectionDTO inspDto = new com.bikeassembly.dto.QmsInspectionDTO();
        inspDto.setResult("F");
        inspDto.setDetailsJson("[{\"item\":\"外观检查\",\"result\":\"不合格\",\"value\":\"有划痕\"}]");
        inspDto.setInspector("测试质检员");
        inspDto.setInspectorUserId(INSPECTOR_USER_ID);
        inspDto.setInspectionTime(java.time.LocalDateTime.now());
        qmsInspectionService.enterInspectionResult(inspId, inspDto);
        qmsInspectionService.confirmInspection(inspId);

        ProWorkProcess p2Entity = proWorkProcessMapper.selectById(p2Id);
        assertEquals(WorkProcessStatus.REJECTED.getCode(), p2Entity.getStatus(), "T13: 工序=不合格(5)");
        ProWorkOrder orderCheck = proWorkOrderMapper.selectById(woId);
        assertEquals(WorkOrderStatus.IN_PROGRESS.getCode(), orderCheck.getStatus(), "T13: 工单=仍生产中");
        System.out.println("[T13 PASS] 质检不合格");

        // ========== T14: 返工 ==========
        setUser(OPERATOR_USER_ID);
        proWorkProcessService.reworkStart(p2Id);
        p2Entity = proWorkProcessMapper.selectById(p2Id);
        assertEquals(WorkProcessStatus.REWORK.getCode(), p2Entity.getStatus(), "T14: 工序=返工中(6)");
        assertEquals(1, p2Entity.getReworkCount(), "T14: 返工次数=1");

        proWorkProcessService.reworkComplete(p2Id);
        p2Entity = proWorkProcessMapper.selectById(p2Id);
        assertEquals(WorkProcessStatus.WAIT_INSPECTION.getCode(), p2Entity.getStatus(), "T14: 工序=待检(3)");
        assertEquals(QcResult.RECHECK.getCode(), p2Entity.getQcResult(), "T14: qcResult=3(待复检)");

        // 验证复检单是新记录
        List<QmsInspection> reInspections = qmsInspectionMapper.selectList(
                new LambdaQueryWrapper<QmsInspection>()
                        .eq(QmsInspection::getWorkOrderId, woId)
                        .eq(QmsInspection::getProcessId, PROCESS_2_ID)
                        .eq(QmsInspection::getStatus, InspectionStatus.PENDING.getCode()));
        assertEquals(1, reInspections.size(), "T14: 生成复检单");
        assertNotEquals(inspId, reInspections.get(0).getInspectionId(), "T14: 复检单≠初检单");
        System.out.println("[T14 PASS] 返工→复检");

        // ========== T15: 复检合格 ==========
        setUser(INSPECTOR_USER_ID);
        Long reInspId = reInspections.get(0).getInspectionId();
        com.bikeassembly.dto.QmsInspectionDTO reInspDto = new com.bikeassembly.dto.QmsInspectionDTO();
        reInspDto.setResult("P");
        reInspDto.setDetailsJson("[{\"item\":\"外观检查\",\"result\":\"合格\",\"value\":\"已修复\"}]");
        reInspDto.setInspector("测试质检员");
        reInspDto.setInspectorUserId(INSPECTOR_USER_ID);
        reInspDto.setInspectionTime(java.time.LocalDateTime.now());
        qmsInspectionService.enterInspectionResult(reInspId, reInspDto);
        qmsInspectionService.confirmInspection(reInspId);

        p2Entity = proWorkProcessMapper.selectById(p2Id);
        assertEquals(WorkProcessStatus.COMPLETED.getCode(), p2Entity.getStatus(), "T15: 工序=已完成(2)");
        orderCheck = proWorkOrderMapper.selectById(woId);
        assertEquals(WorkOrderStatus.COMPLETED.getCode(), orderCheck.getStatus(), "T15: 工单=已完工(2)");

        // 初检记录保留
        QmsInspection firstInsp = qmsInspectionMapper.selectById(inspId);
        assertEquals(InspectionStatus.CONFIRMED.getCode(), firstInsp.getStatus());
        assertEquals("F", firstInsp.getResult(), "T15: 初检不合格记录保留");

        // 关闭
        setUser(ADMIN_USER_ID);
        proWorkOrderService.closeWorkOrder(woId);
        assertEquals(WorkOrderStatus.CLOSED.getCode(),
                proWorkOrderMapper.selectById(woId).getStatus(), "T15: 关闭成功");
        System.out.println("[T15 PASS] 复检合格→完工→关闭\n");
    }

    // ========================================================================
    // 负面测试
    // ========================================================================

    @Test
    @Order(3)
    @DisplayName("NEG-01: 重复工单号必须失败")
    void negDuplicateWorkOrderNo() {
        setUser(ADMIN_USER_ID);
        ProWorkOrderDTO dto = new ProWorkOrderDTO();
        dto.setWorkOrderNo("TEST-WO-DUP-" + System.currentTimeMillis());
        dto.setItemId(ITEM_ID);
        dto.setPlanQty(BigDecimal.TEN);
        dto.setUnit("台");
        ProWorkOrderVO created = proWorkOrderService.saveProWorkOrder(dto);

        final ProWorkOrderDTO dupDto = new ProWorkOrderDTO();
        dupDto.setWorkOrderNo(created.getWorkOrderNo()); // 重复
        dupDto.setItemId(ITEM_ID);
        dupDto.setPlanQty(BigDecimal.TEN);
        dupDto.setUnit("台");
        assertThrows(Exception.class, () -> proWorkOrderService.saveProWorkOrder(dupDto),
                "NEG-01: 重复工单号应抛异常");
        System.out.println("[NEG-01 PASS] 重复工单号被拒绝");
        proWorkOrderMapper.deleteById(created.getWorkOrderId());
    }

    @Test
    @Order(4)
    @DisplayName("NEG-02: 计划数量0或负必须失败")
    void negInvalidPlanQty() {
        setUser(ADMIN_USER_ID);
        ProWorkOrderDTO dto = new ProWorkOrderDTO();
        dto.setWorkOrderNo("TEST-WO-ZERO-" + System.currentTimeMillis());
        dto.setItemId(ITEM_ID);
        dto.setPlanQty(BigDecimal.ZERO);
        dto.setUnit("台");
        assertThrows(Exception.class, () -> proWorkOrderService.saveProWorkOrder(dto),
                "NEG-02: 计划数量0应抛异常");
        System.out.println("[NEG-02 PASS] 计划数量0被拒绝");
    }

    @Test
    @Order(5)
    @DisplayName("NEG-03: 重复指派必须失败")
    void negDuplicateAssign() {
        setUser(ADMIN_USER_ID);
        ProWorkOrderDTO dto = new ProWorkOrderDTO();
        dto.setWorkOrderNo("TEST-WO-ASSIGN-" + System.currentTimeMillis());
        dto.setItemId(ITEM_ID);
        dto.setPlanQty(BigDecimal.TEN);
        dto.setUnit("台");
        ProWorkOrderVO wo = proWorkOrderService.saveProWorkOrder(dto);
        proWorkOrderService.assignAssignee(wo.getWorkOrderId(), OPERATOR_USER_ID);

        assertThrows(Exception.class,
                () -> proWorkOrderService.assignAssignee(wo.getWorkOrderId(), OPERATOR_USER_ID),
                "NEG-03: 重复指派应抛异常");
        System.out.println("[NEG-03 PASS] 重复指派被拒绝");
        proWorkOrderMapper.deleteById(wo.getWorkOrderId());
    }

    @Test
    @Order(6)
    @DisplayName("NEG-04: 非完工工单不能直接关闭")
    void negCannotCloseNonCompleted() {
        setUser(ADMIN_USER_ID);
        ProWorkOrderDTO dto = new ProWorkOrderDTO();
        dto.setWorkOrderNo("TEST-WO-CLOSE-FAIL-" + System.currentTimeMillis());
        dto.setItemId(ITEM_ID);
        dto.setPlanQty(BigDecimal.TEN);
        dto.setUnit("台");
        ProWorkOrderVO wo = proWorkOrderService.saveProWorkOrder(dto);

        assertThrows(Exception.class,
                () -> proWorkOrderService.closeWorkOrder(wo.getWorkOrderId()),
                "NEG-04: 非完工工单不能关闭");
        System.out.println("[NEG-04 PASS] 非完工工单关闭被拒绝");
        proWorkOrderMapper.deleteById(wo.getWorkOrderId());
    }

    @Test
    @Order(7)
    @DisplayName("NEG-05: 报工数量0或负必须失败")
    void negInvalidReportQty() {
        setUser(ADMIN_USER_ID);
        ProWorkOrderDTO dto = new ProWorkOrderDTO();
        dto.setWorkOrderNo("TEST-WO-RPT-NEG-" + System.currentTimeMillis());
        dto.setItemId(ITEM_ID);
        dto.setPlanQty(BigDecimal.TEN);
        dto.setUnit("台");
        dto.setRouteId(ROUTE_ID);
        ProWorkOrderVO wo = proWorkOrderService.saveProWorkOrder(dto);
        proWorkOrderService.assignAssignee(wo.getWorkOrderId(), OPERATOR_USER_ID);
        proWorkOrderService.releaseWorkOrder(wo.getWorkOrderId());
        List<ProWorkProcess> processes = getProcessesByWorkOrder(wo.getWorkOrderId());
        Long p1Id = processes.get(0).getProcessRecordId();

        setUser(OPERATOR_USER_ID);
        proWorkProcessService.startProWorkProcess(p1Id);

        assertThrows(Exception.class,
                () -> proWorkProcessService.unifiedReport(p1Id, BigDecimal.ZERO, null),
                "NEG-05: 报工数量0应抛异常");
        System.out.println("[NEG-05 PASS] 报工数量0被拒绝");

        // 清理
        proWorkProcessMapper.delete(new LambdaQueryWrapper<ProWorkProcess>()
                .eq(ProWorkProcess::getWorkOrderId, wo.getWorkOrderId()));
        proWorkOrderMapper.deleteById(wo.getWorkOrderId());
    }

    @Test
    @Order(8)
    @DisplayName("NEG-06: 待检工序不能继续普通报工")
    void negCannotReportWaitingInspection() {
        setUser(ADMIN_USER_ID);
        ProWorkOrderDTO dto = new ProWorkOrderDTO();
        dto.setWorkOrderNo("TEST-WO-NORPT-" + System.currentTimeMillis());
        dto.setItemId(ITEM_ID);
        dto.setPlanQty(BigDecimal.TEN);
        dto.setUnit("台");
        dto.setRouteId(ROUTE_ID);
        ProWorkOrderVO wo = proWorkOrderService.saveProWorkOrder(dto);
        proWorkOrderService.assignAssignee(wo.getWorkOrderId(), OPERATOR_USER_ID);
        proWorkOrderService.releaseWorkOrder(wo.getWorkOrderId());
        List<ProWorkProcess> processes = getProcessesByWorkOrder(wo.getWorkOrderId());
        Long p1Id = processes.get(0).getProcessRecordId();
        Long p2Id = processes.get(1).getProcessRecordId();

        setUser(OPERATOR_USER_ID);
        proWorkProcessService.startProWorkProcess(p1Id);
        proWorkProcessService.unifiedReport(p1Id, BigDecimal.TEN, null);
        proWorkProcessService.startProWorkProcess(p2Id);
        proWorkProcessService.unifiedReport(p2Id, BigDecimal.TEN, "待检");

        // 验证工序已进入待检
        ProWorkProcess p2 = proWorkProcessMapper.selectById(p2Id);
        assertEquals(WorkProcessStatus.WAIT_INSPECTION.getCode(), p2.getStatus());

        // 尝试继续报工
        assertThrows(Exception.class,
                () -> proWorkProcessService.unifiedReport(p2Id, BigDecimal.ONE, null),
                "NEG-06: 待检工序不能继续报工");
        System.out.println("[NEG-06 PASS] 待检工序报工被拒绝");

        // 清理
        qmsInspectionMapper.delete(new LambdaQueryWrapper<QmsInspection>()
                .eq(QmsInspection::getWorkOrderId, wo.getWorkOrderId()));
        proWorkProcessMapper.delete(new LambdaQueryWrapper<ProWorkProcess>()
                .eq(ProWorkProcess::getWorkOrderId, wo.getWorkOrderId()));
        proWorkOrderMapper.deleteById(wo.getWorkOrderId());
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    private void setUser(Long userId) {
        BaseContext.removeAll();
        BaseContext.setCurrentId(userId);
        if (userId.equals(ADMIN_USER_ID) || userId.equals(INSPECTOR_USER_ID)) {
            BaseContext.setCurrentDataScope("ALL");
        } else {
            BaseContext.setCurrentDataScope("SELF");
        }
    }

    private List<ProWorkProcess> getProcessesByWorkOrder(Long workOrderId) {
        return proWorkProcessMapper.selectList(
                new LambdaQueryWrapper<ProWorkProcess>()
                        .eq(ProWorkProcess::getWorkOrderId, workOrderId)
                        .orderByAsc(ProWorkProcess::getStepSeq));
    }

    private long countActiveInspections(Long workOrderId, Long processId) {
        return qmsInspectionMapper.selectCount(
                new LambdaQueryWrapper<QmsInspection>()
                        .eq(QmsInspection::getWorkOrderId, workOrderId)
                        .eq(QmsInspection::getProcessId, processId)
                        .in(QmsInspection::getStatus,
                                InspectionStatus.PENDING.getCode(), InspectionStatus.ENTERED.getCode()));
    }

    private void assertProcessStatus(Long processRecordId, WorkProcessStatus expected) {
        ProWorkProcess p = proWorkProcessMapper.selectById(processRecordId);
        assertNotNull(p, "工序记录不应为空: " + processRecordId);
        assertEquals(expected.getCode(), p.getStatus(),
                String.format("工序[%d]状态应为%s", processRecordId, expected.getLabel()));
    }

    @BeforeEach
    void setUp() {
        BaseContext.removeAll();
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeAll();
    }
}
