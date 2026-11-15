package com.bikeassembly.scan;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bikeassembly.constant.PermissionConstant;
import com.bikeassembly.context.BaseContext;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.exception.ForbiddenException;
import com.bikeassembly.mapper.QmsInspectionMapper;
import com.bikeassembly.mapper.ProWorkProcessMapper;
import com.bikeassembly.service.PermissionService;
import com.bikeassembly.service.impl.QmsInspectionServiceImpl;
import com.bikeassembly.service.impl.ProWorkProcessServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkOrderScanQueryTest {
    PermissionService permissions;
    QmsInspectionMapper mapper;
    QmsInspectionServiceImpl service;
    @BeforeEach void setup() {
        BaseContext.setCurrentId(10L);
        permissions = mock(PermissionService.class);
        mapper = mock(QmsInspectionMapper.class);
        service = new QmsInspectionServiceImpl();
        ReflectionTestUtils.setField(service, "permissionService", permissions);
        ReflectionTestUtils.setField(service, "qmsInspectionMapper", mapper);
    }
    @AfterEach void cleanup() { BaseContext.removeAll(); }
    @Test void mineDoesNotIncludeUnassignedWithoutPendingPermission() {
        when(permissions.hasPermission(10L, PermissionConstant.APP_INSPECTION_VIEW)).thenReturn(true);
        when(mapper.selectAppPageByCondition(any(), eq(10L), eq(false), eq(false), eq(false), eq(42L), isNull(), eq("0")))
                .thenReturn(new Page<>(1, 9999));
        service.listAppQmsInspections("mine", 42L, null, "0");
        verify(mapper).selectAppPageByCondition(any(), eq(10L), eq(false), eq(false), eq(false), eq(42L), isNull(), eq("0"));
    }
    @Test void scanCountAndListUseSameScope() {
        when(permissions.hasPermission(10L, PermissionConstant.APP_INSPECTION_VIEW)).thenReturn(true);
        when(permissions.hasPermission(10L, PermissionConstant.APP_INSPECTION_VIEW_PENDING)).thenReturn(true);
        when(mapper.countAppPending(42L, 10L, false, true)).thenReturn(2L);
        when(mapper.selectAppPageByCondition(any(), eq(10L), eq(false), eq(true), eq(false), eq(42L), isNull(), eq("0")))
                .thenReturn(new Page<>(1, 9999));
        assertEquals(2, service.countAccessiblePending(42L));
        service.listAppQmsInspections("mine", 42L, null, "0");
        verify(mapper).countAppPending(42L, 10L, false, true);
    }
    @Test void allAndPendingCannotBeRequestedWithoutAdditionalPermission() {
        when(permissions.hasPermission(10L, PermissionConstant.APP_INSPECTION_VIEW)).thenReturn(true);
        assertThrows(ForbiddenException.class, () -> service.listAppQmsInspections("all", 42L, null, null));
        assertThrows(ForbiddenException.class, () -> service.listAppQmsInspections("pending", 42L, null, null));
        verifyNoInteractions(mapper);
    }
    @Test void badFiltersNeverReachDatabase() {
        when(permissions.hasPermission(10L, PermissionConstant.APP_INSPECTION_VIEW)).thenReturn(true);
        assertThrows(BaseException.class, () -> service.listAppQmsInspections("mine", -1L, null, "0"));
        assertThrows(BaseException.class, () -> service.listAppQmsInspections("mine", 42L, null, "unknown"));
        verifyNoInteractions(mapper);
    }
    @Test void processOnlyViewCanUseOwnProcessQuery() {
        var processMapper = mock(ProWorkProcessMapper.class);
        var processService = new ProWorkProcessServiceImpl();
        ReflectionTestUtils.setField(processService, "permissionService", permissions);
        ReflectionTestUtils.setField(processService, "proWorkProcessMapper", processMapper);
        when(permissions.hasPermission(10L, PermissionConstant.APP_WORK_PROCESS_VIEW)).thenReturn(true);
        when(processMapper.selectAppPageByCondition(any(), eq(10L), eq(42L))).thenReturn(new Page<>(1, 9999));
        processService.listAppProWorkProcesses("mine", 42L);
        verify(processMapper).selectAppPageByCondition(any(), eq(10L), eq(42L));
        assertThrows(ForbiddenException.class, () -> processService.listAppProWorkProcesses("all", 42L));
    }
}
