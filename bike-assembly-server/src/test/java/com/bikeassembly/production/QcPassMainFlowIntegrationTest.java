package com.bikeassembly.production;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bikeassembly.AbstractPostgresContainerIntegrationTest;
import com.bikeassembly.context.BaseContext;
import com.bikeassembly.dto.ProWorkOrderDTO;
import com.bikeassembly.dto.QmsInspectionDTO;
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
import com.bikeassembly.service.PermissionService;
import com.bikeassembly.service.ProWorkOrderService;
import com.bikeassembly.service.ProWorkProcessService;
import com.bikeassembly.service.QmsInspectionService;
import com.bikeassembly.vo.ProWorkOrderVO;
import com.bikeassembly.vo.ProWorkProcessVO;
import com.bikeassembly.vo.QmsInspectionVO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 质检合格主流程集成测试（第一道工序需要质检）。
 *
 * 测试数据：
 *   ROUTE-201: step1=PROC-201(需质检) → step2=PROC-202(免质检)
 *   计划数量=10
 *
 * 完整流程：
 *   第一工序 READY → IN_PROGRESS → 部分报工4 → 满额报工6
 *   → WAIT_INSPECTION → 生成PENDING质检单 → 录入合格
 *   → 确认 → COMPLETED → 第二工序 READY
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("质检合格主流程集成测试")
public class QcPassMainFlowIntegrationTest extends AbstractPostgresContainerIntegrationTest {

    @BeforeAll
    static void seedProductionFlow() {
        executeSql("sql/test/seed_production_flow.sql");
    }

    private static final Long ADMIN_USER_ID = 1L;
    private static final Long OPERATOR_USER_ID = 1002L;
    private static final Long INSPECTOR_USER_ID = 1003L;
    private static final Long ITEM_ID = 1001L;
    private static final Long ROUTE_ID = 2001L;    // step1=2001(需质检), step2=2002(免质检)
    private static final Long WORK_UNIT_ID = 1001L;

    @BeforeAll
    static void seedReferenceData() {
        // 容器库需要演示主数据（item 1 / route 1 / unit 1）与生产流程专用种子（item 1001 / route 2001）。
        executeSql("sql/dev_seed_full.sql");
        executeSql("sql/test/seed_production_flow.sql");
    }

    @Autowired private ProWorkOrderService proWorkOrderService;
    @Autowired private ProWorkProcessService proWorkProcessService;
    @Autowired private QmsInspectionService qmsInspectionService;
    @Autowired private ProWorkOrderMapper proWorkOrderMapper;
    @Autowired private ProWorkProcessMapper proWorkProcessMapper;
    @Autowired private QmsInspectionMapper qmsInspectionMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PermissionService permissionService;

    private Long workOrderId;
    private Long p1RecordId;  // 第一道工序 processRecordId
    private Long p2RecordId;  // 第二道工序 processRecordId
    private Long inspectionId; // 质检单ID

    // ========================================================================
    // 测试 1: 完整主流程
    // ========================================================================

    @Test
    @Order(1)
    @DisplayName("S01-S10: 第一工序READY→报工→待检→录入→确认→完成→第二道解锁")
    void fullQcPassMainFlow() {
        // ==================== S01: 创建工单 ====================
        setUser(ADMIN_USER_ID);
        ProWorkOrderDTO dto = new ProWorkOrderDTO();
        dto.setWorkOrderNo("TEST-QC-MAIN-" + System.currentTimeMillis());
        dto.setItemId(ITEM_ID);
        dto.setPlanQty(BigDecimal.TEN);
        dto.setUnit("台");
        dto.setRouteId(ROUTE_ID);
        dto.setWorkUnitId(WORK_UNIT_ID);
        ProWorkOrderVO wo = proWorkOrderService.saveProWorkOrder(dto);
        workOrderId = wo.getWorkOrderId();
        assertNotNull(workOrderId);
        assertEquals(WorkOrderStatus.CREATED.getCode(), wo.getStatus());
        assertEquals(0, BigDecimal.TEN.compareTo(wo.getPlanQty()));
        System.out.println("[S01 PASS] 工单创建: workOrderId=" + workOrderId + ", planQty=10, status=已创建(0)");

        // ==================== S02: 指派+下达 ====================
        wo = proWorkOrderService.assignAssignee(workOrderId, OPERATOR_USER_ID);
        assertEquals(OPERATOR_USER_ID, wo.getAssigneeUserId());
        wo = proWorkOrderService.releaseWorkOrder(workOrderId);
        assertEquals(WorkOrderStatus.RELEASED.getCode(), wo.getStatus());

        List<ProWorkProcess> processes = getProcesses();
        assertEquals(2, processes.size(), "应生成2道工序");
        ProWorkProcess p1 = processes.get(0);
        ProWorkProcess p2 = processes.get(1);
        p1RecordId = p1.getProcessRecordId();
        p2RecordId = p2.getProcessRecordId();
        assertEquals(1, p1.getStepSeq(), "第一道 stepSeq=1");
        assertEquals(2, p2.getStepSeq(), "第二道 stepSeq=2");
        assertEquals(WorkProcessStatus.READY.getCode(), p1.getStatus(), "S02: 第一道=可开工(9)");
        assertEquals(WorkProcessStatus.PENDING.getCode(), p2.getStatus(), "S02: 第二道=待开始(0)");
        System.out.println("[S02 PASS] 下达: p1=" + p1RecordId + " READY, p2=" + p2RecordId + " PENDING");

        // ==================== S03: 禁止越序开工 ====================
        setUser(OPERATOR_USER_ID);
        assertThrows(Exception.class,
                () -> proWorkProcessService.startProWorkProcess(p2RecordId),
                "S03: 第二道不可越序开工");
        ProWorkProcess p2Check = proWorkProcessMapper.selectById(p2RecordId);
        assertEquals(WorkProcessStatus.PENDING.getCode(), p2Check.getStatus());
        System.out.println("[S03 PASS] 越序开工被拒绝");

        // ==================== S04: 开始第一道工序 ====================
        ProWorkProcessVO p1Vo = proWorkProcessService.startProWorkProcess(p1RecordId);
        assertEquals(WorkProcessStatus.IN_PROGRESS.getCode(), p1Vo.getStatus(), "S04: 工序=进行中(1)");
        assertNotNull(p1Vo.getStartTime(), "S04: 记录开始时间");
        ProWorkOrder orderCheck = proWorkOrderMapper.selectById(workOrderId);
        assertEquals(WorkOrderStatus.IN_PROGRESS.getCode(), orderCheck.getStatus(), "S04: 工单=生产中(1)");
        p2Check = proWorkProcessMapper.selectById(p2RecordId);
        assertEquals(WorkProcessStatus.PENDING.getCode(), p2Check.getStatus(), "S04: 第二道不变");
        System.out.println("[S04 PASS] 开始工序: IN_PROGRESS, 工单=IN_PROGRESS");

        // ==================== S05: 部分报工(4/10) ====================
        proWorkProcessService.unifiedReport(p1RecordId, new BigDecimal("4"), null);
        ProWorkProcess p1Entity = proWorkProcessMapper.selectById(p1RecordId);
        assertEquals(WorkProcessStatus.IN_PROGRESS.getCode(), p1Entity.getStatus(), "S05: 部分报工后仍IN_PROGRESS");
        assertEquals(0, new BigDecimal("4").compareTo(p1Entity.getReportQty()), "S05: reportQty=4");
        // 不应生成质检单
        long qcCount = countInspectionsByProcessRecordId(p1RecordId);
        assertEquals(0, qcCount, "S05: 部分报工不生成质检单");
        p2Check = proWorkProcessMapper.selectById(p2RecordId);
        assertEquals(WorkProcessStatus.PENDING.getCode(), p2Check.getStatus(), "S05: 第二道仍不可开工");
        System.out.println("[S05 PASS] 部分报工: reportQty=4, 仍IN_PROGRESS, 无质检单");

        // ==================== S06: 满额报工(6/10 → total=10) ====================
        proWorkProcessService.unifiedReport(p1RecordId, new BigDecimal("6"), null);
        p1Entity = proWorkProcessMapper.selectById(p1RecordId);
        assertEquals(WorkProcessStatus.WAIT_INSPECTION.getCode(), p1Entity.getStatus(), "S06: 满额后=待检(3)");
        assertEquals(0, BigDecimal.TEN.compareTo(p1Entity.getReportQty()), "S06: reportQty=10");
        assertEquals(QcResult.PENDING.getCode(), p1Entity.getQcResult(), "S06: qcResult=0(待检)");

        // 验证质检单
        List<QmsInspection> inspections = getInspectionsByProcessRecordId(p1RecordId);
        assertEquals(1, inspections.size(), "S06: 生成1张质检单");
        QmsInspection insp = inspections.get(0);
        inspectionId = insp.getInspectionId();
        assertEquals(InspectionStatus.PENDING.getCode(), insp.getStatus(), "S06: 质检单=PENDING(0)");
        assertEquals("0", insp.getResult(), "S06: 质检结果=0(未录入)");
        assertEquals(workOrderId, insp.getWorkOrderId(), "S06: workOrderId一致");
        assertEquals(Long.valueOf(1L), insp.getProcessId(), "S06: processId=1(车架组装)");
        assertEquals(p1RecordId, insp.getProcessRecordId(), "S06: processRecordId绑定正确");

        // 第二道仍不可开工
        p2Check = proWorkProcessMapper.selectById(p2RecordId);
        assertEquals(WorkProcessStatus.PENDING.getCode(), p2Check.getStatus(), "S06: 第二道仍不可开工");
        System.out.println("[S06 PASS] 满额送检: WAIT_INSPECTION, inspectionId=" + inspectionId);

        // ==================== S07: 录入合格质检结果 ====================
        setUser(INSPECTOR_USER_ID);
        QmsInspectionDTO inspDto = new QmsInspectionDTO();
        inspDto.setResult("P");
        inspDto.setDetailsJson("[{\"itemName\":\"外观检查\",\"standard\":\"无划痕\",\"result\":\"P\",\"actual\":\"合格\"}]");
        inspDto.setInspector("测试质检员");
        inspDto.setInspectorUserId(INSPECTOR_USER_ID);
        inspDto.setInspectionTime(LocalDateTime.now());
        QmsInspectionVO inspVo = qmsInspectionService.enterInspectionResult(inspectionId, inspDto);
        assertEquals(InspectionStatus.ENTERED.getCode(), inspVo.getStatus(), "S07: 质检单=ENTERED(1)");
        assertEquals("P", inspVo.getResult(), "S07: result=P");

        // 工序仍为待检
        p1Entity = proWorkProcessMapper.selectById(p1RecordId);
        assertEquals(WorkProcessStatus.WAIT_INSPECTION.getCode(), p1Entity.getStatus(), "S07: 工序仍WAIT_INSPECTION");

        // 第二道仍不可开工
        p2Check = proWorkProcessMapper.selectById(p2RecordId);
        assertEquals(WorkProcessStatus.PENDING.getCode(), p2Check.getStatus(), "S07: 第二道仍不可开工");
        System.out.println("[S07 PASS] 录入合格: ENTERED, 工序仍待检, 第二道仍PENDING");

        // ==================== S08: 确认质检结果 ====================
        qmsInspectionService.confirmInspection(inspectionId);
        QmsInspection inspEntity = qmsInspectionMapper.selectById(inspectionId);
        assertEquals(InspectionStatus.CONFIRMED.getCode(), inspEntity.getStatus(), "S08: 质检单=CONFIRMED(2)");

        // 工序完成
        p1Entity = proWorkProcessMapper.selectById(p1RecordId);
        assertEquals(WorkProcessStatus.COMPLETED.getCode(), p1Entity.getStatus(), "S08: 工序=COMPLETED(2)");
        assertEquals(QcResult.PASS.getCode(), p1Entity.getQcResult(), "S08: qcResult=1(合格)");

        // 第二道解锁
        p2Check = proWorkProcessMapper.selectById(p2RecordId);
        assertEquals(WorkProcessStatus.READY.getCode(), p2Check.getStatus(), "S08: 第二道=READY(9)");

        // 工单仍生产中（还有第二道未完成）
        orderCheck = proWorkOrderMapper.selectById(workOrderId);
        assertEquals(WorkOrderStatus.IN_PROGRESS.getCode(), orderCheck.getStatus(), "S08: 工单仍IN_PROGRESS");
        System.out.println("[S08 PASS] 确认合格: 质检=CONFIRMED, 工序=COMPLETED, 第二道=READY");

        // ==================== S09: 完成第二道工序 ====================
        setUser(OPERATOR_USER_ID);
        proWorkProcessService.startProWorkProcess(p2RecordId);
        ProWorkProcess p2Entity = proWorkProcessMapper.selectById(p2RecordId);
        assertEquals(WorkProcessStatus.IN_PROGRESS.getCode(), p2Entity.getStatus(), "S09: 第二道=IN_PROGRESS");

        // 第二道无需质检，满额后直接完成
        proWorkProcessService.unifiedReport(p2RecordId, BigDecimal.TEN, null);
        p2Entity = proWorkProcessMapper.selectById(p2RecordId);
        assertEquals(WorkProcessStatus.COMPLETED.getCode(), p2Entity.getStatus(), "S09: 第二道=COMPLETED(2)");

        // 工单完工
        orderCheck = proWorkOrderMapper.selectById(workOrderId);
        assertEquals(WorkOrderStatus.COMPLETED.getCode(), orderCheck.getStatus(), "S09: 工单=COMPLETED(2)");
        System.out.println("[S09 PASS] 第二道完成, 工单完工");

        // ==================== S10: 关闭工单 ====================
        setUser(ADMIN_USER_ID);
        wo = proWorkOrderService.closeWorkOrder(workOrderId);
        assertEquals(WorkOrderStatus.CLOSED.getCode(), wo.getStatus(), "S10: 工单=CLOSED(3)");
        System.out.println("[S10 PASS] 工单关闭\n");
        System.out.println("=== 质检合格主流程 S01-S10 全部通过! ===\n");
    }

    // ========================================================================
    // 测试 2: 无质检的工序满额后直接完成（对照）
    // ========================================================================

    @Test
    @Order(2)
    @DisplayName("NEG-01: 无质检工序满额后直接COMPLETED")
    void noQcProcessCompletesDirectly() {
        setUser(ADMIN_USER_ID);
        ProWorkOrderDTO dto = new ProWorkOrderDTO();
        dto.setWorkOrderNo("TEST-NOQC-" + System.currentTimeMillis());
        dto.setItemId(ITEM_ID);
        dto.setPlanQty(BigDecimal.TEN);
        dto.setUnit("台");
        // 使用 route-1001: step1=1001(无QC), step2=1002(有QC)
        dto.setRouteId(1001L);
        dto.setWorkUnitId(WORK_UNIT_ID);
        ProWorkOrderVO wo = proWorkOrderService.saveProWorkOrder(dto);
        Long woId = wo.getWorkOrderId();
        proWorkOrderService.assignAssignee(woId, OPERATOR_USER_ID);
        proWorkOrderService.releaseWorkOrder(woId);

        List<ProWorkProcess> processes = getProcessesByWorkOrder(woId);
        Long p1Id = processes.get(0).getProcessRecordId();

        setUser(OPERATOR_USER_ID);
        proWorkProcessService.startProWorkProcess(p1Id);
        proWorkProcessService.unifiedReport(p1Id, BigDecimal.TEN, null);

        ProWorkProcess p1 = proWorkProcessMapper.selectById(p1Id);
        assertEquals(WorkProcessStatus.COMPLETED.getCode(), p1.getStatus(), "无质检工序满额后直接COMPLETED");

        // 确认没有生成质检单
        long qcCount = countInspectionsByProcessRecordId(p1Id);
        assertEquals(0, qcCount, "无质检工序不生成质检单");
        System.out.println("[NEG-01 PASS] 无质检工序直接完成\n");

        // 清理
        cleanupWorkOrder(woId);
    }

    // ========================================================================
    // 测试 3: 待检工序不能继续报工
    // ========================================================================

    @Test
    @Order(3)
    @DisplayName("NEG-02: 待检工序拒绝继续报工")
    void cannotReportWhenWaitingInspection() {
        setUser(ADMIN_USER_ID);
        ProWorkOrderDTO dto = new ProWorkOrderDTO();
        dto.setWorkOrderNo("TEST-NORPT-" + System.currentTimeMillis());
        dto.setItemId(ITEM_ID);
        dto.setPlanQty(BigDecimal.TEN);
        dto.setUnit("台");
        dto.setRouteId(ROUTE_ID);
        dto.setWorkUnitId(WORK_UNIT_ID);
        ProWorkOrderVO wo = proWorkOrderService.saveProWorkOrder(dto);
        Long woId = wo.getWorkOrderId();
        proWorkOrderService.assignAssignee(woId, OPERATOR_USER_ID);
        proWorkOrderService.releaseWorkOrder(woId);

        List<ProWorkProcess> processes = getProcessesByWorkOrder(woId);
        Long p1Id = processes.get(0).getProcessRecordId();

        setUser(OPERATOR_USER_ID);
        proWorkProcessService.startProWorkProcess(p1Id);
        proWorkProcessService.unifiedReport(p1Id, BigDecimal.TEN, null);

        // 确认已进入待检
        ProWorkProcess p1 = proWorkProcessMapper.selectById(p1Id);
        assertEquals(WorkProcessStatus.WAIT_INSPECTION.getCode(), p1.getStatus());

        // 尝试继续报工 → 应失败
        assertThrows(Exception.class,
                () -> proWorkProcessService.unifiedReport(p1Id, BigDecimal.ONE, null),
                "NEG-02: 待检工序不能继续报工");
        System.out.println("[NEG-02 PASS] 待检工序报工被拒绝\n");

        cleanupWorkOrder(woId);
    }

    // ========================================================================
    // 测试 4: 报工数量0或负必须失败
    // ========================================================================

    @Test
    @Order(4)
    @DisplayName("NEG-03: 报工数量<=0被拒绝")
    void invalidReportQtyRejected() {
        setUser(ADMIN_USER_ID);
        ProWorkOrderDTO dto = new ProWorkOrderDTO();
        dto.setWorkOrderNo("TEST-ZEROQTY-" + System.currentTimeMillis());
        dto.setItemId(ITEM_ID);
        dto.setPlanQty(BigDecimal.TEN);
        dto.setUnit("台");
        dto.setRouteId(ROUTE_ID);
        dto.setWorkUnitId(WORK_UNIT_ID);
        ProWorkOrderVO wo = proWorkOrderService.saveProWorkOrder(dto);
        Long woId = wo.getWorkOrderId();
        proWorkOrderService.assignAssignee(woId, OPERATOR_USER_ID);
        proWorkOrderService.releaseWorkOrder(woId);

        List<ProWorkProcess> processes = getProcessesByWorkOrder(woId);
        Long p1Id = processes.get(0).getProcessRecordId();

        setUser(OPERATOR_USER_ID);
        proWorkProcessService.startProWorkProcess(p1Id);

        assertThrows(Exception.class,
                () -> proWorkProcessService.unifiedReport(p1Id, BigDecimal.ZERO, null),
                "NEG-03: 报工数量0被拒绝");
        assertThrows(Exception.class,
                () -> proWorkProcessService.unifiedReport(p1Id, new BigDecimal("-1"), null),
                "NEG-03: 负报工数量被拒绝");
        System.out.println("[NEG-03 PASS] 无效报工数量被拒绝\n");

        cleanupWorkOrder(woId);
    }

    // ========================================================================
    // 测试 5: 确认已确认的质检单被拒绝（防重复确认）
    // ========================================================================

    @Test
    @Order(5)
    @DisplayName("NEG-04: 重复确认被拒绝")
    void duplicateConfirmRejected() {
        setUser(ADMIN_USER_ID);
        ProWorkOrderDTO dto = new ProWorkOrderDTO();
        dto.setWorkOrderNo("TEST-DUPCONF-" + System.currentTimeMillis());
        dto.setItemId(ITEM_ID);
        dto.setPlanQty(BigDecimal.TEN);
        dto.setUnit("台");
        dto.setRouteId(ROUTE_ID);
        dto.setWorkUnitId(WORK_UNIT_ID);
        ProWorkOrderVO wo = proWorkOrderService.saveProWorkOrder(dto);
        Long woId = wo.getWorkOrderId();
        proWorkOrderService.assignAssignee(woId, OPERATOR_USER_ID);
        proWorkOrderService.releaseWorkOrder(woId);

        List<ProWorkProcess> processes = getProcessesByWorkOrder(woId);
        Long p1Id = processes.get(0).getProcessRecordId();

        setUser(OPERATOR_USER_ID);
        proWorkProcessService.startProWorkProcess(p1Id);
        proWorkProcessService.unifiedReport(p1Id, BigDecimal.TEN, null);

        List<QmsInspection> inspections = getInspectionsByProcessRecordId(p1Id);
        assertEquals(1, inspections.size());
        Long inspId = inspections.get(0).getInspectionId();

        setUser(INSPECTOR_USER_ID);
        QmsInspectionDTO inspDto = new QmsInspectionDTO();
        inspDto.setResult("P");
        inspDto.setDetailsJson("[{\"itemName\":\"test\",\"standard\":\"-\",\"result\":\"P\",\"actual\":\"ok\"}]");
        inspDto.setInspector("测试质检员");
        inspDto.setInspectorUserId(INSPECTOR_USER_ID);
        inspDto.setInspectionTime(LocalDateTime.now());
        qmsInspectionService.enterInspectionResult(inspId, inspDto);

        // 第一次确认 → 成功
        qmsInspectionService.confirmInspection(inspId);
        assertEquals(InspectionStatus.CONFIRMED.getCode(),
                qmsInspectionMapper.selectById(inspId).getStatus());

        // 第二次确认 → 应失败
        assertThrows(Exception.class,
                () -> qmsInspectionService.confirmInspection(inspId),
                "NEG-04: 重复确认被拒绝");
        System.out.println("[NEG-04 PASS] 重复确认被拒绝\n");

        cleanupWorkOrder(woId);
    }

    // ========================================================================
    // 测试 6: 质检单关联错误的processRecordId被拒绝
    // ========================================================================

    @Test
    @Order(6)
    @DisplayName("NEG-05: 质检单关联错误processRecordId时确认失败")
    void wrongProcessRecordIdRejectedOnConfirm() {
        setUser(ADMIN_USER_ID);
        ProWorkOrderDTO dto = new ProWorkOrderDTO();
        dto.setWorkOrderNo("TEST-WRONGPR-" + System.currentTimeMillis());
        dto.setItemId(ITEM_ID);
        dto.setPlanQty(BigDecimal.TEN);
        dto.setUnit("台");
        dto.setRouteId(ROUTE_ID);
        dto.setWorkUnitId(WORK_UNIT_ID);
        ProWorkOrderVO wo = proWorkOrderService.saveProWorkOrder(dto);
        Long woId = wo.getWorkOrderId();
        proWorkOrderService.assignAssignee(woId, OPERATOR_USER_ID);
        proWorkOrderService.releaseWorkOrder(woId);

        List<ProWorkProcess> processes = getProcessesByWorkOrder(woId);
        Long p1Id = processes.get(0).getProcessRecordId();
        Long p2Id = processes.get(1).getProcessRecordId();

        setUser(OPERATOR_USER_ID);
        proWorkProcessService.startProWorkProcess(p1Id);
        proWorkProcessService.unifiedReport(p1Id, BigDecimal.TEN, null);

        List<QmsInspection> inspections = getInspectionsByProcessRecordId(p1Id);
        assertEquals(1, inspections.size());
        Long inspId = inspections.get(0).getInspectionId();

        // 篡改质检单的processRecordId指向第二道工序
        QmsInspection insp = qmsInspectionMapper.selectById(inspId);
        insp.setProcessRecordId(p2Id); // 指向第二道（状态=PENDING，不是WAIT_INSPECTION）
        qmsInspectionMapper.updateById(insp);

        setUser(INSPECTOR_USER_ID);
        QmsInspectionDTO inspDto = new QmsInspectionDTO();
        inspDto.setResult("P");
        inspDto.setDetailsJson("[{\"itemName\":\"test\",\"standard\":\"-\",\"result\":\"P\",\"actual\":\"ok\"}]");
        inspDto.setInspector("测试质检员");
        inspDto.setInspectorUserId(INSPECTOR_USER_ID);
        inspDto.setInspectionTime(LocalDateTime.now());

        // 录入可能成功（只检查inspection状态），但确认应失败
        // 先修复processRecordId正确指向以录入
        insp.setProcessRecordId(p1Id);
        qmsInspectionMapper.updateById(insp);
        qmsInspectionService.enterInspectionResult(inspId, inspDto);

        // 篡改processRecordId为第二道（PENDING状态）
        insp = qmsInspectionMapper.selectById(inspId);
        insp.setProcessRecordId(p2Id);
        qmsInspectionMapper.updateById(insp);

        // 确认应因"工序不是WAIT_INSPECTION"而失败
        assertThrows(Exception.class,
                () -> qmsInspectionService.confirmInspection(inspId),
                "NEG-05: 错误processRecordId确认应失败");
        System.out.println("[NEG-05 PASS] 错误processRecordId确认被拒绝\n");

        cleanupWorkOrder(woId);
    }

    // ========================================================================
    // 测试 7: scope=mine 对质检员可见未分配的待检质检单
    // ========================================================================

    @Test
    @Order(7)
    @DisplayName("NEG-06: scope=mine返回未分配待检质检单")
    void inspectorCanSeeUnassignedInspections() {
        setUser(ADMIN_USER_ID);
        ProWorkOrderDTO dto = new ProWorkOrderDTO();
        dto.setWorkOrderNo("TEST-SCOPE-" + System.currentTimeMillis());
        dto.setItemId(ITEM_ID);
        dto.setPlanQty(BigDecimal.TEN);
        dto.setUnit("台");
        dto.setRouteId(ROUTE_ID);
        dto.setWorkUnitId(WORK_UNIT_ID);
        ProWorkOrderVO wo = proWorkOrderService.saveProWorkOrder(dto);
        Long woId = wo.getWorkOrderId();
        proWorkOrderService.assignAssignee(woId, OPERATOR_USER_ID);
        proWorkOrderService.releaseWorkOrder(woId);

        List<ProWorkProcess> processes = getProcessesByWorkOrder(woId);
        Long p1Id = processes.get(0).getProcessRecordId();

        // 操作员报工送检
        setUser(OPERATOR_USER_ID);
        proWorkProcessService.startProWorkProcess(p1Id);
        proWorkProcessService.unifiedReport(p1Id, BigDecimal.TEN, null);

        // 验证自动生成了质检单且 inspector_user_id 为 NULL
        List<QmsInspection> inspections = getInspectionsByProcessRecordId(p1Id);
        assertEquals(1, inspections.size());
        assertNull(inspections.get(0).getInspectorUserId(), "自动生成的质检单inspectorUserId应为NULL");

        // 切换到质检员身份，用 scope=mine 查询 — 应能查到（因为includePending=true）
        setUser(INSPECTOR_USER_ID);
        var pageResult = qmsInspectionService.listQmsInspections(
                1L, 9999L, null, null, null, "mine", null);
        List<QmsInspectionVO> mineList = pageResult.getRecords();

        // 应包含未分配的待检质检单
        boolean found = mineList.stream()
                .anyMatch(v -> v.getInspectionId().equals(inspections.get(0).getInspectionId()));
        assertTrue(found, "NEG-06: scope=mine 应包含未分配的待检质检单");

        System.out.println("[NEG-06 PASS] scope=mine正确返回未分配待检质检单\n");

        cleanupWorkOrder(woId);
    }

    // ========================================================================
    // 测试 8: route_id=1 使用 processCode + stepSeq 格式下达
    // ========================================================================

    @Test
    @Order(8)
    @DisplayName("NEG-07: route使用processCode+stepSeq格式正常下达")
    void routeWithProcessCodeAndStepSeqWorks() {
        setUser(ADMIN_USER_ID);
        ProWorkOrderDTO dto = new ProWorkOrderDTO();
        dto.setWorkOrderNo("TEST-CODECOMPAT-" + System.currentTimeMillis());
        dto.setItemId(1L);  // route_id=1 关联的产品是 item_id=1(山地自行车)
        dto.setPlanQty(BigDecimal.TEN);
        dto.setUnit("台");
        dto.setRouteId(1L);  // route_id=1 使用 processCode + stepSeq 格式
        dto.setWorkUnitId(1L);  // unit_id=1 存在于数据库中
        ProWorkOrderVO wo = proWorkOrderService.saveProWorkOrder(dto);
        Long woId = wo.getWorkOrderId();
        proWorkOrderService.assignAssignee(woId, OPERATOR_USER_ID);

        // 下达应成功（之前会因为找不到 processId 而抛异常）
        wo = proWorkOrderService.releaseWorkOrder(woId);
        assertEquals(WorkOrderStatus.RELEASED.getCode(), wo.getStatus(),
                "NEG-07: processCode+stepSeq格式的路由应正常下达");

        // 验证生成的工序数量（当前演示种子 route_id=1 有 8 步）
        List<ProWorkProcess> processes = getProcessesByWorkOrder(woId);
        assertEquals(8, processes.size(), "NEG-07: 应生成8道工序");
        assertEquals(WorkProcessStatus.READY.getCode(), processes.get(0).getStatus(),
                "NEG-07: 第一道=READY");
        assertEquals(1, processes.get(0).getStepSeq(), "NEG-07: 第一道stepSeq=1");
        assertEquals(8, processes.get(7).getStepSeq(), "NEG-07: 最后一道stepSeq=8");

        System.out.println("[NEG-07 PASS] processCode+stepSeq格式路由正常下达，生成" + processes.size() + "道工序\n");

        cleanupWorkOrder(woId);
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

    private List<ProWorkProcess> getProcesses() {
        return proWorkProcessMapper.selectList(
                new LambdaQueryWrapper<ProWorkProcess>()
                        .eq(ProWorkProcess::getWorkOrderId, workOrderId)
                        .orderByAsc(ProWorkProcess::getStepSeq));
    }

    private List<ProWorkProcess> getProcessesByWorkOrder(Long woId) {
        return proWorkProcessMapper.selectList(
                new LambdaQueryWrapper<ProWorkProcess>()
                        .eq(ProWorkProcess::getWorkOrderId, woId)
                        .orderByAsc(ProWorkProcess::getStepSeq));
    }

    private List<QmsInspection> getInspectionsByProcessRecordId(Long processRecordId) {
        return qmsInspectionMapper.selectList(
                new LambdaQueryWrapper<QmsInspection>()
                        .eq(QmsInspection::getProcessRecordId, processRecordId));
    }

    private long countInspectionsByProcessRecordId(Long processRecordId) {
        return qmsInspectionMapper.selectCount(
                new LambdaQueryWrapper<QmsInspection>()
                        .eq(QmsInspection::getProcessRecordId, processRecordId));
    }

    private void cleanupWorkOrder(Long woId) {
        qmsInspectionMapper.delete(new LambdaQueryWrapper<QmsInspection>()
                .eq(QmsInspection::getWorkOrderId, woId));
        proWorkProcessMapper.delete(new LambdaQueryWrapper<ProWorkProcess>()
                .eq(ProWorkProcess::getWorkOrderId, woId));
        proWorkOrderMapper.deleteById(woId);
    }

    @BeforeEach
    void setUp() {
        BaseContext.removeAll();
        // init seed 未给 inspector 授予 app:inspection:view-pending，scope=mine 的公共待检池依赖该权限。
        jdbcTemplate.update("INSERT INTO sys_menu(menu_id,parent_id,menu_name,path,component,icon,visible,keep_alive,redirect,perms,menu_type,order_num,status) "
                + "VALUES (9901,400,'查看待处理',NULL,NULL,NULL,'0','0',NULL,'app:inspection:view-pending','F',5,'0') ON CONFLICT (menu_id) DO NOTHING");
        jdbcTemplate.update("INSERT INTO sys_role_menu(role_id,menu_id) VALUES (4,9901) ON CONFLICT DO NOTHING");
        permissionService.evictUserCache(INSPECTOR_USER_ID);
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeAll();
    }
}
