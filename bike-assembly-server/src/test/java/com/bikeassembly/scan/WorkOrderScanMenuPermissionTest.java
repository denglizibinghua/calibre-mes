package com.bikeassembly.scan;

import com.bikeassembly.entity.SysMenu;
import com.bikeassembly.entity.SysRole;
import com.bikeassembly.entity.SysUser;
import com.bikeassembly.mapper.SysMenuMapper;
import com.bikeassembly.mapper.SysRoleMapper;
import com.bikeassembly.mapper.SysUserMapper;
import com.bikeassembly.service.impl.SysMenuServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkOrderScanMenuPermissionTest {
    private SysMenu menu(String type, String permission, String status) {
        SysMenu menu = new SysMenu();
        menu.setMenuType(type); menu.setPerms(permission); menu.setStatus(status);
        return menu;
    }
    @Test void assignedPageViewPermissionIsKeptWithoutGrantingUnassignedRights() {
        SysMenuMapper mapper = mock(SysMenuMapper.class);
        SysMenuServiceImpl service = service(mapper, mock(SysUserMapper.class), mock(SysRoleMapper.class));
        when(mapper.selectMenusByUserId(20L)).thenReturn(List.of(
                menu("C", "admin:work-order:view", "0"), menu("F", "admin:work-order:manage", "0"),
                menu("D", "", "0"), menu("F", "admin:forbidden", "1")));
        assertEquals(List.of("admin:work-order:view", "admin:work-order:manage"), service.getUserPermissionCodes(20L));
        verify(mapper, never()).selectList(any());
    }
    @Test void administratorAlsoReceivesPageViewPermissions() {
        SysMenuMapper mapper = mock(SysMenuMapper.class);
        SysUserMapper users = mock(SysUserMapper.class);
        SysRoleMapper roles = mock(SysRoleMapper.class);
        SysUser user = new SysUser(); user.setRoleId(1L);
        SysRole role = new SysRole(); role.setRoleKey("admin");
        when(users.selectById(1L)).thenReturn(user); when(roles.selectById(1L)).thenReturn(role);
        when(mapper.selectList(any())).thenReturn(List.of(menu("C", "admin:work-order:view", "0")));
        assertEquals(List.of("admin:work-order:view"), service(mapper, users, roles).getUserPermissionCodes(1L));
    }
    @Test void anonymousHasNoPermissions() {
        assertTrue(service(mock(SysMenuMapper.class), mock(SysUserMapper.class), mock(SysRoleMapper.class)).getUserPermissionCodes(null).isEmpty());
    }
    private SysMenuServiceImpl service(SysMenuMapper menus, SysUserMapper users, SysRoleMapper roles) {
        SysMenuServiceImpl service = new SysMenuServiceImpl();
        ReflectionTestUtils.setField(service, "sysMenuMapper", menus);
        ReflectionTestUtils.setField(service, "sysUserMapper", users);
        ReflectionTestUtils.setField(service, "sysRoleMapper", roles);
        return service;
    }
}
