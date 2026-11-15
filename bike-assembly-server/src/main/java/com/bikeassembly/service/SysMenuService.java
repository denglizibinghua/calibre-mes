package com.bikeassembly.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bikeassembly.dto.SysMenuDTO;
import com.bikeassembly.entity.SysMenu;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.vo.SysMenuVO;

import java.util.List;

public interface SysMenuService extends IService<SysMenu> {

    SysMenuVO saveMenu(SysMenuDTO dto);

    SysMenuVO updateMenu(Long id, SysMenuDTO dto);

    SysMenuVO getMenuById(Long id);

    PageResult<SysMenuVO> listMenus(Long page, Long size);

    void deleteMenu(Long id);

    /**
     * 查询所有权限标识（menu_type='F', status='0'）
     */
    List<SysMenuVO> listAllPerms();

    /**
     * 查询所有菜单，按树形结构返回
     */
    List<SysMenuVO> listMenuTree();

    /**
     * 查询当前登录用户有权访问的菜单树（仅目录和菜单页）。
     * 只返回 menu_type IN ('D','C') 且为已启用、可见的菜单。
     * 用于管理端动态路由和侧边栏渲染。
     */
    List<SysMenuVO> getCurrentUserMenuTree(Long userId);

    /**
     * 查询当前登录用户的所有权限编码（含按钮权限码）。
     * 管理员返回包含通配符的集合。
     */
    List<String> getUserPermissionCodes(Long userId);
}
