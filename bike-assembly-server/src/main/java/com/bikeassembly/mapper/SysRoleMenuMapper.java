package com.bikeassembly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bikeassembly.entity.SysRoleMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 角色-菜单/权限关联 Mapper。
 */
@Mapper
public interface SysRoleMenuMapper extends BaseMapper<SysRoleMenu> {

    /**
     * 查询某角色已分配的菜单/权限 ID 列表。
     */
    List<Long> selectMenuIdsByRoleId(@Param("roleId") Long roleId);

    /**
     * 查询某角色的权限编码列表。
     */
    List<String> selectPermsByRoleId(@Param("roleId") Long roleId);

    /**
     * 查询某用户的所有权限编码。
     * 通过 sys_user.role_id → sys_role_menu → sys_menu 关联。
     */
    List<String> selectPermsByUserId(@Param("userId") Long userId);

    /**
     * 根据角色ID删除所有关联。
     */
    int deleteByRoleId(@Param("roleId") Long roleId);

    /**
     * 批量插入角色-菜单关联。
     */
    int batchInsert(@Param("list") List<SysRoleMenu> list);
}
