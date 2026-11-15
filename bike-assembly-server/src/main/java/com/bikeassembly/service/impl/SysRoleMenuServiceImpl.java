package com.bikeassembly.service.impl;

import com.bikeassembly.entity.SysRoleMenu;
import com.bikeassembly.event.RoleMenusChangedEvent;
import com.bikeassembly.mapper.SysMenuMapper;
import com.bikeassembly.mapper.SysRoleMenuMapper;
import com.bikeassembly.service.SysRoleMenuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色-菜单/权限关联 Service 实现。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SysRoleMenuServiceImpl implements SysRoleMenuService {

    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysMenuMapper sysMenuMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public List<String> getPermsByUserId(Long userId) {
        if (userId == null) return Collections.emptyList();
        return sysRoleMenuMapper.selectPermsByUserId(userId);
    }

    @Override
    public List<String> getPermsByRoleId(Long roleId) {
        if (roleId == null) return Collections.emptyList();
        return sysRoleMenuMapper.selectPermsByRoleId(roleId);
    }

    @Override
    public List<Long> getMenuIdsByRoleId(Long roleId) {
        if (roleId == null) return Collections.emptyList();
        return sysRoleMenuMapper.selectMenuIdsByRoleId(roleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long roleId, List<Long> menuIds) {
        // 先删后插
        sysRoleMenuMapper.deleteByRoleId(roleId);

        List<Long> validMenuIds = filterExistingMenuIds(menuIds);
        if (!validMenuIds.isEmpty()) {
            List<SysRoleMenu> list = new ArrayList<>();
            for (Long menuId : validMenuIds) {
                SysRoleMenu rm = new SysRoleMenu();
                rm.setRoleId(roleId);
                rm.setMenuId(menuId);
                list.add(rm);
            }
            sysRoleMenuMapper.batchInsert(list);
        }

        // 发布角色菜单变更事件，触发权限缓存清理
        eventPublisher.publishEvent(new RoleMenusChangedEvent(this, roleId));
        log.info("角色权限分配成功：roleId={}, menuCount={}", roleId, validMenuIds.size());
    }

    /**
     * 过滤出 sys_menu 中真实存在的菜单 ID：
     * 丢弃 null/非正数（如授权树虚拟分组节点 -1/-2）和表中不存在的 ID，防止外键失败或脏数据。
     */
    private List<Long> filterExistingMenuIds(List<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> distinctIds = menuIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList());
        if (distinctIds.isEmpty()) {
            return Collections.emptyList();
        }
        return sysMenuMapper.selectExistingMenuIds(distinctIds);
    }
}
