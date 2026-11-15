package com.bikeassembly.stage5;

import com.bikeassembly.entity.*;
import com.bikeassembly.mapper.*;
import com.bikeassembly.service.impl.*;
import com.bikeassembly.utils.RedisService;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 阶段5 并发和原子性单元测试（Mockito，无 MySQL/Spring 依赖）。
 *
 * 注意：因 LambdaUpdateWrapper 在纯 Mockito 环境下无法初始化 MP TableInfo 缓存，
 * 涉及内部创建 LambdaUpdateWrapper 的 Service 方法（如 accept/complete/close）
 * 需要在 Spring 环境下测试。以下仅覆盖可直接 Mockito 测试的规则。
 */
@DisplayName("阶段5 — 并发和原子性")
class Stage5ConcurrencyTest {

    // ==================== 维修创建 ====================
    @Test @DisplayName("1. 维修创建强制 status=0 并清空处理字段")
    void repairCreateForcesPendingStatus() {
        var repairMapper = mock(EqmRepairOrderMapper.class);
        var equipMapper = mock(BaseEquipmentMapper.class);
        var eq = new BaseEquipment(); eq.setEquipmentId(1L); eq.setRunningStatus("0");
        when(equipMapper.selectByIdForUpdate(1L)).thenReturn(eq);
        when(equipMapper.selectById(any())).thenReturn(new BaseEquipment());

        var repairService = new EqmRepairOrderServiceImpl();
        setField(repairService, "eqmRepairOrderMapper", repairMapper);
        setField(repairService, "baseEquipmentMapper", equipMapper);
        setField(repairService, "webSocketNotifier", mock(com.bikeassembly.ws.WebSocketNotifier.class));

        var dto = new com.bikeassembly.dto.EqmRepairOrderDTO();
        dto.setEquipmentId(1L);
        dto.setFaultDesc("test");
        dto.setStatus("2"); dto.setHandlerUserId(999L);

        var captor = ArgumentCaptor.forClass(EqmRepairOrder.class);
        when(repairMapper.insert(captor.capture())).thenReturn(1);

        repairService.saveEqmRepairOrder(dto);

        var saved = captor.getValue();
        assertEquals("0", saved.getStatus(), "status must be forced to 0");
        assertNull(saved.getHandlerUserId(), "handlerUserId must be null");
        assertNull(saved.getFinishTime(), "finishTime must be null");
        assertNull(saved.getResultDesc(), "resultDesc must be null");
    }

    // ==================== 安灯创建 ====================
    @Test @DisplayName("2. 安灯创建强制 status=0 并清空处理字段")
    void andonCreateForcesPendingStatus() {
        var andonMapper = mock(AndonAlertMapper.class);
        var equipMapper = mock(BaseEquipmentMapper.class);
        var unitMapper = mock(BaseWorkUnitMapper.class);
        when(equipMapper.selectById(any())).thenReturn(new BaseEquipment());
        when(unitMapper.selectById(any())).thenReturn(new BaseWorkUnit());

        var andonService = new AndonAlertServiceImpl();
        setField(andonService, "andonAlertMapper", andonMapper);
        setField(andonService, "baseEquipmentMapper", equipMapper);
        setField(andonService, "baseWorkUnitMapper", unitMapper);
        setField(andonService, "webSocketNotifier", mock(com.bikeassembly.ws.WebSocketNotifier.class));

        var dto = new com.bikeassembly.dto.AndonAlertDTO();
        dto.setAlertType("设备异常");
        dto.setAlertLevel("1");
        dto.setStatus("2"); // attacker value
        dto.setHandlerUserId(999L);

        var captor = ArgumentCaptor.forClass(AndonAlert.class);
        when(andonMapper.insert(captor.capture())).thenReturn(1);

        andonService.saveAndonAlert(dto);

        var saved = captor.getValue();
        assertEquals("0", saved.getStatus(), "status must be forced to 0");
        assertNull(saved.getHandlerUserId(), "handlerUserId must be null");
        assertNull(saved.getHandleTime(), "handleTime must be null");
    }

    // ==================== 维修完成非当前处理人 ====================
    @Test @DisplayName("3. 非当前处理人维修完成被拒")
    void repairCompleteRejectsNonHandler() {
        var repairMapper = mock(EqmRepairOrderMapper.class);
        var repair = new EqmRepairOrder();
        repair.setRepairId(1L);
        repair.setStatus("1");
        repair.setHandlerUserId(99L);

        when(repairMapper.selectById(1L)).thenReturn(repair);

        var repairService = new EqmRepairOrderServiceImpl();
        setField(repairService, "eqmRepairOrderMapper", repairMapper);
        setField(repairService, "webSocketNotifier", mock(com.bikeassembly.ws.WebSocketNotifier.class));

        var ex = assertThrows(com.bikeassembly.exception.BaseException.class,
                () -> repairService.completeEqmRepairOrder(1L));
        assertTrue(ex.getMessage().contains("只能完成自己受理"));
    }

    // ==================== 安灯关闭非当前处理人 ====================
    @Test @DisplayName("4. 非当前处理人安灯关闭被拒")
    void andonCloseRejectsNonHandler() {
        var andonMapper = mock(AndonAlertMapper.class);
        var alert = new AndonAlert();
        alert.setAlertId(1L);
        alert.setStatus("1");
        alert.setHandlerUserId(99L);

        when(andonMapper.selectById(1L)).thenReturn(alert);

        var andonService = new AndonAlertServiceImpl();
        setField(andonService, "andonAlertMapper", andonMapper);
        setField(andonService, "webSocketNotifier", mock(com.bikeassembly.ws.WebSocketNotifier.class));

        var ex = assertThrows(com.bikeassembly.exception.BaseException.class,
                () -> andonService.closeAndonAlert(1L));
        assertTrue(ex.getMessage().contains("只能关闭自己受理"));
    }

    // ==================== 登录失败 Redis 计数 ====================
    @Test @DisplayName("5. 登录失败调用 Redis 原子自增")
    void loginFailUsesAtomicIncrement() {
        var redisService = mock(RedisService.class);
        when(redisService.increment(anyString())).thenReturn(1L);

        var loginFailService = new LoginFailService(redisService);

        loginFailService.incrementFailCount("test_user");

        // 验证 Redis 原子自增 + 首次失败设置过期时间
        verify(redisService).increment("login:fail:test_user");
        verify(redisService).expire(eq("login:fail:test_user"), any());
    }

    // Test 6 (锁定阈值) 由 AuthenticationIntegrationTest 覆盖（真实 Redis 容器）。

    // ---- helpers ----

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
            } catch (Exception e2) {
                throw new RuntimeException(e2);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
