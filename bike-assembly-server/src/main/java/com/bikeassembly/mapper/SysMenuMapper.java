package com.bikeassembly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bikeassembly.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /**
     * 查询某用户角色分配的所有菜单（含目录、菜单页、按钮权限）。
     * 通过 sys_user.role_id → sys_role_menu → sys_menu 关联。
     */
    List<SysMenu> selectMenusByUserId(@Param("userId") Long userId);

    /**
     * 查询某用户角色分配的所有已启用菜单（用于菜单树构建）。
     * 只返回 menu_type IN ('D','C') 且 status='0' 的记录。
     */
    List<SysMenu> selectVisibleMenusByUserId(@Param("userId") Long userId);

    /**
     * 返回给定 ID 列表中真实存在于 sys_menu 的菜单 ID，
     * 用于角色授权入库前过滤非法/虚拟分组节点。
     */
    List<Long> selectExistingMenuIds(@Param("ids") List<Long> ids);
}
