package com.bikeassembly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 角色-菜单/权限关联实体。
 */
@Data
@TableName("sys_role_menu")
public class SysRoleMenu implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "role_menu_id", type = IdType.AUTO)
    private Long roleMenuId;
    /** 角色ID */
    @TableField("role_id")
    private Long roleId;
    /** 菜单/权限ID */
    @TableField("menu_id")
    private Long menuId;
}
