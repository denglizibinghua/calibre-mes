package com.bikeassembly.stage7;

import com.bikeassembly.context.BaseContext;
import com.bikeassembly.dto.CompleteRepairDTO;
import com.bikeassembly.entity.*;
import com.bikeassembly.mapper.*;
import com.bikeassembly.service.impl.EqmRepairOrderServiceImpl;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("阶段7 — 设备维修闭环")
class Stage7RepairFlowTest {

    private static final Long USER_ID = 100L;

    @Test @DisplayName("1.创建强制status=0+设备故障")
    void createForcesPendingAndMarksFault() {
        try (MockedStatic<BaseContext> bc = mockStatic(BaseContext.class)) {
            bc.when(BaseContext::getCurrentId).thenReturn(USER_ID);
            var svc = mockSvc();
            var captor = ArgumentCaptor.forClass(EqmRepairOrder.class);
            var eq = new BaseEquipment(); eq.setEquipmentId(1L); eq.setRunningStatus("0");
            when(svc.bm.selectByIdForUpdate(1L)).thenReturn(eq);
            doReturn(1).when(svc.rm).insert(captor.capture());
            doReturn(1).when(svc.bm).update(isNull(), any());

            var dto = new com.bikeassembly.dto.EqmRepairOrderDTO();
            dto.setEquipmentId(1L); dto.setFaultDesc("test");
            dto.setStatus("2"); dto.setHandlerUserId(999L);

            svc.saveEqmRepairOrder(dto);
            var saved = captor.getValue();
            assertEquals("0", saved.getStatus());
            assertNull(saved.getHandlerUserId());
            assertNull(saved.getFinishTime());
        }
    }

    @Test @DisplayName("2.非当前处理人不能完成")
    void nonHandlerCannotComplete() {
        try (MockedStatic<BaseContext> bc = mockStatic(BaseContext.class)) {
            bc.when(BaseContext::getCurrentId).thenReturn(USER_ID);
            var svc = mockSvc();
            when(svc.rm.selectById(1L)).thenReturn(eqm("1", 99L));
            assertThrows(RuntimeException.class,
                    () -> svc.completeEqmRepairOrderWithDto(1L, new CRD("test")));
        }
    }

    @Test @DisplayName("3.待维修不能直接完成")
    void pendingCannotComplete() {
        try (MockedStatic<BaseContext> bc = mockStatic(BaseContext.class)) {
            bc.when(BaseContext::getCurrentId).thenReturn(USER_ID);
            var svc = mockSvc();
            when(svc.rm.selectById(1L)).thenReturn(eqm("0", USER_ID));
            assertThrows(RuntimeException.class,
                    () -> svc.completeEqmRepairOrderWithDto(1L, new CRD("test")));
        }
    }

    @Test @DisplayName("4.已完成不能重复")
    void completedCannotRecomplete() {
        try (MockedStatic<BaseContext> bc = mockStatic(BaseContext.class)) {
            bc.when(BaseContext::getCurrentId).thenReturn(USER_ID);
            var svc = mockSvc();
            when(svc.rm.selectById(1L)).thenReturn(eqm("2", USER_ID));
            assertThrows(RuntimeException.class,
                    () -> svc.completeEqmRepairOrderWithDto(1L, new CRD("test")));
        }
    }

    @Test @DisplayName("5.@Transactional on completeEqmRepairOrderWithDto")
    void hasTransactionalOnComplete() throws Exception {
        assertTrue(EqmRepairOrderServiceImpl.class
                .getMethod("completeEqmRepairOrderWithDto", Long.class, CompleteRepairDTO.class)
                .isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class));
    }

    @Test @DisplayName("6.@Transactional on saveEqmRepairOrder")
    void hasTransactionalOnSave() throws Exception {
        assertTrue(EqmRepairOrderServiceImpl.class
                .getMethod("saveEqmRepairOrder", com.bikeassembly.dto.EqmRepairOrderDTO.class)
                .isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class));
    }

    @Test @DisplayName("7.普通update拒绝修改resultDesc")
    void normalUpdateRejectsResultDesc() {
        var svc = mockSvc();
        var r = new EqmRepairOrder(); r.setRepairId(1L); r.setStatus("1");
        r.setResultDesc("old"); r.setReporterUserId(USER_ID);
        when(svc.rm.selectById(1L)).thenReturn(r);
        doReturn(1).when(svc.rm).updateById(any(EqmRepairOrder.class));
        var dto = new com.bikeassembly.dto.EqmRepairOrderDTO();
        dto.setEquipmentId(1L); dto.setResultDesc("bypass");
        assertThrows(RuntimeException.class, () -> svc.updateEqmRepairOrder(1L, dto));
    }

    @Test @DisplayName("8.普通update拒绝修改status")
    void normalUpdateRejectsStatus() {
        var svc = mockSvc();
        var r = new EqmRepairOrder(); r.setRepairId(1L); r.setStatus("1"); r.setReporterUserId(USER_ID);
        when(svc.rm.selectById(1L)).thenReturn(r);
        doReturn(1).when(svc.rm).updateById(any(EqmRepairOrder.class));
        var dto = new com.bikeassembly.dto.EqmRepairOrderDTO();
        dto.setEquipmentId(1L); dto.setStatus("2");
        assertThrows(RuntimeException.class, () -> svc.updateEqmRepairOrder(1L, dto));
    }

    @Test @DisplayName("9.当前处理人可完成+设备恢复")
    void handlerCanCompleteAndRestore() {
        try (MockedStatic<BaseContext> bc = mockStatic(BaseContext.class)) {
            bc.when(BaseContext::getCurrentId).thenReturn(USER_ID);
            var svc = mockSvc();
            when(svc.rm.selectById(1L)).thenReturn(eqm("1", USER_ID));
            doReturn(1).when(svc.rm).update(isNull(), any());
            when(svc.rm.selectCount(any())).thenReturn(0L);
            when(svc.mm.selectCount(any())).thenReturn(0L);
            BaseEquipment eq = new BaseEquipment(); eq.setEquipmentId(1L); eq.setStatus("0");
            when(svc.bm.selectByIdForUpdate(1L)).thenReturn(eq);
            doReturn(1).when(svc.bm).update(isNull(), any());

            assertDoesNotThrow(() -> svc.completeEqmRepairOrderWithDto(1L, new CRD("done")));
        }
    }

    @Test @DisplayName("10.有其他待维修单时不恢复设备")
    void noRestoreWhenOtherPending() {
        try (MockedStatic<BaseContext> bc = mockStatic(BaseContext.class)) {
            bc.when(BaseContext::getCurrentId).thenReturn(USER_ID);
            var svc = mockSvc();
            when(svc.rm.selectById(1L)).thenReturn(eqm("1", USER_ID));
            doReturn(1).when(svc.rm).update(isNull(), any());
            when(svc.rm.selectCount(any())).thenReturn(1L);
            when(svc.bm.selectByIdForUpdate(1L)).thenReturn(new BaseEquipment(){{setStatus("0");}});

            svc.completeEqmRepairOrderWithDto(1L, new CRD("done"));
            verify(svc.bm, never()).update(isNull(), any());
        }
    }

    @Test @DisplayName("11.有进行中保养时不恢复设备")
    void noRestoreWhenActiveMaintenance() {
        try (MockedStatic<BaseContext> bc = mockStatic(BaseContext.class)) {
            bc.when(BaseContext::getCurrentId).thenReturn(USER_ID);
            var svc = mockSvc();
            when(svc.rm.selectById(1L)).thenReturn(eqm("1", USER_ID));
            doReturn(1).when(svc.rm).update(isNull(), any());
            when(svc.rm.selectCount(any())).thenReturn(0L);
            when(svc.mm.selectCount(any())).thenReturn(1L);
            when(svc.bm.selectByIdForUpdate(1L)).thenReturn(new BaseEquipment(){{setStatus("0");}});

            svc.completeEqmRepairOrderWithDto(1L, new CRD("done"));
            verify(svc.bm, never()).update(isNull(), any());
        }
    }

    @Test @DisplayName("12.设备禁用时不恢复")
    void noRestoreWhenDisabled() {
        try (MockedStatic<BaseContext> bc = mockStatic(BaseContext.class)) {
            bc.when(BaseContext::getCurrentId).thenReturn(USER_ID);
            var svc = mockSvc();
            when(svc.rm.selectById(1L)).thenReturn(eqm("1", USER_ID));
            doReturn(1).when(svc.rm).update(isNull(), any());
            when(svc.rm.selectCount(any())).thenReturn(0L);
            when(svc.mm.selectCount(any())).thenReturn(0L);
            BaseEquipment eq = new BaseEquipment(); eq.setEquipmentId(1L); eq.setStatus("1");
            when(svc.bm.selectByIdForUpdate(1L)).thenReturn(eq);

            svc.completeEqmRepairOrderWithDto(1L, new CRD("done"));
            verify(svc.bm, never()).update(isNull(), any());
        }
    }

    // helpers
    static class CRD extends CompleteRepairDTO { CRD(String s) { setResultDesc(s); } }

    static class TSvc extends EqmRepairOrderServiceImpl {
        EqmRepairOrderMapper rm = mock(EqmRepairOrderMapper.class);
        BaseEquipmentMapper bm = mock(BaseEquipmentMapper.class);
        EqmMaintenanceMapper mm = mock(EqmMaintenanceMapper.class);
        TSvc() {
            try { setF("eqmRepairOrderMapper",rm); setF("baseEquipmentMapper",bm);
                setF("eqmMaintenanceMapper",mm);
                setF("permissionService", mock(com.bikeassembly.service.PermissionService.class));
                setF("webSocketNotifier", mock(com.bikeassembly.ws.WebSocketNotifier.class));
            } catch (Exception e) { throw new RuntimeException(e); }
        }
        void setF(String n, Object v) throws Exception {
            var f = EqmRepairOrderServiceImpl.class.getDeclaredField(n);
            f.setAccessible(true); f.set(this, v);
        }
    }

    private static TSvc mockSvc() { return new TSvc(); }
    private static EqmRepairOrder eqm(String st, Long hId) {
        var r = new EqmRepairOrder(); r.setRepairId(1L); r.setEquipmentId(1L);
        r.setStatus(st); r.setHandlerUserId(hId); return r;
    }
}
