package com.bikeassembly.ai;

import com.bikeassembly.mapper.BaseWorkUnitMapper;
import com.bikeassembly.mapper.SysUserMapper;
import com.bikeassembly.service.DataScopeService;
import com.bikeassembly.service.PermissionService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataScopeServiceTest {

    @Test
    void departmentWithoutUnitIsDeniedInsteadOfBecomingAll() {
        PermissionService permissions = mock(PermissionService.class);
        SysUserMapper users = mock(SysUserMapper.class);
        BaseWorkUnitMapper units = mock(BaseWorkUnitMapper.class);
        when(permissions.getUserDataScope(7L)).thenReturn("DEPT");
        when(users.selectDepartmentRootUnitId(7L)).thenReturn(null);

        DataScopeService.Scope scope = new DataScopeService(permissions, users, units).resolve(7L);

        assertEquals("DENIED", scope.name());
        assertTrue(scope.unitIds().isEmpty());
        assertTrue(!scope.all());
    }

    @Test
    void departmentUsesOnlyDescendantUnits() {
        PermissionService permissions = mock(PermissionService.class);
        SysUserMapper users = mock(SysUserMapper.class);
        BaseWorkUnitMapper units = mock(BaseWorkUnitMapper.class);
        when(permissions.getUserDataScope(7L)).thenReturn("DEPT");
        when(users.selectDepartmentRootUnitId(7L)).thenReturn(10L);
        when(units.selectDescendantUnitIds(10L)).thenReturn(List.of(10L, 11L, 12L));

        DataScopeService.Scope scope = new DataScopeService(permissions, users, units).resolve(7L);

        assertEquals("DEPT", scope.name());
        assertEquals(10L, scope.rootUnitId());
        assertEquals(List.of(10L, 11L, 12L), scope.unitIds());
    }
}
