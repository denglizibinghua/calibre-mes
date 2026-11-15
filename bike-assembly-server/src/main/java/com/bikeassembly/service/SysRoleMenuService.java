package com.bikeassembly.service;

import java.util.List;

/**
 * 角色-菜单/权限关联 Service。
 */
public interface SysRoleMenuService {

    /**
     * 查询某用户的所有权限编码。
     */
    List<String> getPermsByUserId(Long userId);

    /**
     * 查询某角色的权限编码列表。
     */
    List<String> getPermsByRoleId(Long roleId);

    /**
     * 查询某角色已分配的菜单/权限 ID 列表。
     */
    List<Long> getMenuIdsByRoleId(Long roleId);

    /**
     * 为角色分配菜单/权限（事务：先删后插）。
     */
    void assignMenus(Long roleId, List<Long> menuIds);
}
