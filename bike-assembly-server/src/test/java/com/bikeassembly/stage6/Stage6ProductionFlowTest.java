package com.bikeassembly.stage6;

import com.bikeassembly.entity.*;
import com.bikeassembly.enums.WorkProcessStatus;
import com.bikeassembly.mapper.*;
import com.bikeassembly.service.impl.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 阶段6 生产闭环单元测试（Mockito，无数据库依赖）。
 */
@DisplayName("阶段6 — 生产核心闭环")
class Stage6ProductionFlowTest {

    // ========== 工序查询 mine 口径 ==========
    @Test @DisplayName("1. mine 查询只使用 assigneeUserId（不按operator查询）")
    void mineQueryUsesOnlyAssigneeUserId() {
        var processMapper = mock(ProWorkProcessMapper.class);
        var orderMapper = mock(ProWorkOrderMapper.class);
        var processService = new ProWorkProcessServiceImpl();
        setField(processService, "proWorkProcessMapper", processMapper);
        setField(processService, "proWorkOrderMapper", orderMapper);

        var mockPage = mock(com.baomidou.mybatisplus.core.metadata.IPage.class);
        when(mockPage.getRecords()).thenReturn(java.util.Collections.emptyList());
        // BaseContext.getCurrentId() returns null in test, so assigneeUserId will be null
        when(processMapper.selectPageByCondition(any(), isNull(), isNull())).thenReturn(mockPage);
        when(processMapper.selectPage(any(), isNull())).thenReturn(mockPage);

        processService.listProWorkProcesses(1L, 15L, "mine");
        verify(processMapper).selectPageByCondition(any(), isNull(), isNull());
    }

    // ========== autoCreateInspection 写入 processRecordId ==========
    @Test @DisplayName("2. 自动质检任务写入 processRecordId（含轮次和父质检单）")
    void autoCreateInspectionWritesProcessRecordId() {
        var processMapper = mock(ProWorkProcessMapper.class);
        var orderMapper = mock(ProWorkOrderMapper.class);
        var qmsMapper = mock(QmsInspectionMapper.class);
        var processService = new ProWorkProcessServiceImpl();
        setField(processService, "proWorkProcessMapper", processMapper);
        setField(processService, "proWorkOrderMapper", orderMapper);
        setField(processService, "qmsInspectionMapper", qmsMapper);

        when(qmsMapper.selectCount(any())).thenReturn(0L);
        // 历史查询：无历史质检单（初检）
        when(qmsMapper.selectOne(any())).thenReturn(null);

        var captor = ArgumentCaptor.forClass(QmsInspection.class);
        when(qmsMapper.insert(captor.capture())).thenReturn(1);

        // 调用 private autoCreateInspection 通过反射
        invokeAutoCreateInspection(processService, 10L, 5L, 42L);

        var inspection = captor.getValue();
        assertNotNull(inspection.getProcessRecordId(), "must set processRecordId");
        assertEquals(Long.valueOf(42L), inspection.getProcessRecordId());
        assertEquals("0", inspection.getStatus(), "must be PENDING");
        assertEquals(Long.valueOf(10L), inspection.getWorkOrderId());
        assertEquals(Integer.valueOf(1), inspection.getInspectionRound(), "初检轮次应为1");
        assertNull(inspection.getParentInspectionId(), "初检无父质检单");
    }

    // ========== deleteProWorkProcess 被拒 ==========
    @Test @DisplayName("3. 工序物理删除被拒")
    void physicalDeleteRejected() {
        var processMapper = mock(ProWorkProcessMapper.class);
        var processService = new ProWorkProcessServiceImpl();
        setField(processService, "proWorkProcessMapper", processMapper);

        var ex = assertThrows(com.bikeassembly.exception.BaseException.class,
                () -> processService.deleteProWorkProcess(1L));
        assertTrue(ex.getMessage().contains("不支持物理删除"));
    }

    // ========== 旧 report-complete 端点废弃 ==========
    @Test @DisplayName("4. 旧 report-complete 端点存在且返回废弃错误")
    void oldReportCompleteEndpointReturnsDeprecatedError() {
        var processService = mock(com.bikeassembly.service.ProWorkProcessService.class);
        var controller = new com.bikeassembly.controller.app.AppProWorkProcessController();
        setField(controller, "proWorkProcessService", processService);

        var ex = assertThrows(com.bikeassembly.exception.BaseException.class,
                () -> controller.reportComplete(1L, java.math.BigDecimal.ONE));
        assertTrue(ex.getMessage().contains("已废弃"));
    }

    // ========== App手工创建工序被拒 ==========
    @Test @DisplayName("6. App手工创建工序被拒")
    void appManualCreateRejected() {
        var processService = mock(com.bikeassembly.service.ProWorkProcessService.class);
        var controller = new com.bikeassembly.controller.app.AppProWorkProcessController();
        setField(controller, "proWorkProcessService", processService);

        var ex = assertThrows(com.bikeassembly.exception.BaseException.class,
                () -> { var dto = new com.bikeassembly.dto.ProWorkProcessDTO(); dto.setWorkOrderId(1L); dto.setProcessId(1L); controller.create(dto); });
        assertTrue(ex.getMessage().contains("不支持手工创建"));
    }

    // ========== Admin手工创建工序被拒 ==========
    @Test @DisplayName("7. Admin手工创建工序被拒")
    void adminManualCreateRejected() {
        var processService = mock(com.bikeassembly.service.ProWorkProcessService.class);
        var controller = new com.bikeassembly.controller.admin.ProWorkProcessController();
        setField(controller, "proWorkProcessService", processService);

        var ex = assertThrows(com.bikeassembly.exception.BaseException.class,
                () -> controller.save(null));
        assertTrue(ex.getMessage().contains("不支持手工创建"));
    }

    // ========== DuplicateKeyException 精确识别 ==========
    @Test @DisplayName("8. 非目标索引 DuplicateKeyException 重新抛出")
    void duplicateKeyExceptionRethrowsForNonTargetIndex() {
        // 模拟：inspection_no 冲突不应被吞掉
        var nonTargetDup = new org.springframework.dao.DuplicateKeyException(
                "Duplicate entry 'QC001' for key 'uk_qms_inspection_inspection_no'");

        var qmsMapper = mock(QmsInspectionMapper.class);
        var processMapper = mock(ProWorkProcessMapper.class);
        var orderMapper = mock(ProWorkOrderMapper.class);
        var processService = new ProWorkProcessServiceImpl();
        setField(processService, "proWorkProcessMapper", processMapper);
        setField(processService, "proWorkOrderMapper", orderMapper);
        setField(processService, "qmsInspectionMapper", qmsMapper);

        when(qmsMapper.selectCount(any())).thenReturn(0L);
        when(qmsMapper.selectOne(any())).thenReturn(null);
        when(qmsMapper.insert(any(QmsInspection.class))).thenThrow(nonTargetDup);

        assertThrows(org.springframework.dao.DuplicateKeyException.class,
                () -> invokeAutoCreateInspection(processService, 1L, 1L, 1L));
    }

    // ========== 质检确认按 processRecordId 查工序 ==========
    @Test @DisplayName("9. 质检确认按 processRecordId 精确查找")
    void inspectionConfirmUsesProcessRecordId() {
        var processMapper = mock(ProWorkProcessMapper.class);
        var qmsMapper = mock(QmsInspectionMapper.class);
        var processService = mock(com.bikeassembly.service.ProWorkProcessService.class);
        var qmsService = new QmsInspectionServiceImpl();
        setField(qmsService, "proWorkProcessMapper", processMapper);
        setField(qmsService, "qmsInspectionMapper", qmsMapper);
        setField(qmsService, "proWorkProcessService", processService);

        // 构造质检单
        var inspection = new QmsInspection();
        inspection.setInspectionId(1L);
        inspection.setInspectionNo("QC001");
        inspection.setWorkOrderId(10L);
        inspection.setProcessId(5L);
        inspection.setProcessRecordId(42L);
        inspection.setResult("P");
        inspection.setStatus("1");

        var process = new ProWorkProcess();
        process.setProcessRecordId(42L);
        process.setWorkOrderId(10L);
        process.setReportQty(java.math.BigDecimal.TEN);
        process.setStatus(WorkProcessStatus.WAIT_INSPECTION.getCode());

        when(processMapper.selectById(42L)).thenReturn(process);

        // 调用 private syncProcessStatusAfterConfirm 通过反射
        invokeSyncProcessStatus(qmsService, inspection);

        // 验证是按 processRecordId 查询，不是按 workOrderId+processId
        verify(processMapper).selectById(42L);
    }

    // ========== 返工完成创建复检绑定同一 processRecordId ==========
    @Test @DisplayName("10. 返工复检绑定同一 processRecordId（复检轮次递增）")
    void reworkReinspectionBindsSameProcessRecordId() {
        var qmsMapper = mock(QmsInspectionMapper.class);
        var processMapper = mock(ProWorkProcessMapper.class);
        var orderMapper = mock(ProWorkOrderMapper.class);
        var processService = new ProWorkProcessServiceImpl();
        setField(processService, "proWorkProcessMapper", processMapper);
        setField(processService, "proWorkOrderMapper", orderMapper);
        setField(processService, "qmsInspectionMapper", qmsMapper);

        when(qmsMapper.selectCount(any())).thenReturn(0L);

        // 模拟已有一次历史质检（初检，round=1）
        var lastInspection = new QmsInspection();
        lastInspection.setInspectionId(99L);
        lastInspection.setInspectionRound(1);
        when(qmsMapper.selectOne(any())).thenReturn(lastInspection);

        var captor = ArgumentCaptor.forClass(QmsInspection.class);
        when(qmsMapper.insert(captor.capture())).thenReturn(1);

        invokeAutoCreateInspection(processService, 10L, 5L, 42L);

        var inspection = captor.getValue();
        assertEquals(Long.valueOf(42L), inspection.getProcessRecordId());
        assertEquals(Integer.valueOf(2), inspection.getInspectionRound(), "复检轮次应为2");
        assertEquals(Long.valueOf(99L), inspection.getParentInspectionId(), "应关联初检质检单");
    }

    // ---- helpers ----
    private static void invokeAutoCreateInspection(ProWorkProcessServiceImpl svc,
            Long workOrderId, Long processId, Long processRecordId) {
        try {
            var m = ProWorkProcessServiceImpl.class.getDeclaredMethod(
                    "autoCreateInspection", Long.class, Long.class, Long.class);
            m.setAccessible(true);
            m.invoke(svc, workOrderId, processId, processRecordId);
        } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException) throw (RuntimeException) e.getCause();
            throw new RuntimeException(e);
        }
    }

    private static void invokeSyncProcessStatus(QmsInspectionServiceImpl svc,
            QmsInspection inspection) {
        try {
            var m = QmsInspectionServiceImpl.class.getDeclaredMethod(
                    "syncProcessStatusAfterConfirm", QmsInspection.class);
            m.setAccessible(true);
            m.invoke(svc, inspection);
        } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException) throw (RuntimeException) e.getCause();
            throw new RuntimeException(e);
        }
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException e) {
            try {
                var field = target.getClass().getSuperclass().getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
            } catch (Exception e2) { throw new RuntimeException(e2); }
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
