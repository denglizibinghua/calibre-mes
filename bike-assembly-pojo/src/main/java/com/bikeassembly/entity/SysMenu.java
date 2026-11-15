package com.bikeassembly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("sys_menu")
public class SysMenu implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 菜单ID */
    @TableId(value = "menu_id", type = IdType.AUTO)
    private Long menuId;
    /** 父菜单ID */
    @TableField("parent_id")
    private Long parentId;
    /** 菜单名称 */
    @TableField("menu_name")
    private String menuName;
    /** 路由路径 */
    @TableField("path")
    private String path;
    /** 前端组件路径 */
    @TableField("component")
    private String component;
    /** 菜单图标 */
    @TableField("icon")
    private String icon;
    /** 是否可见 */
    @TableField("visible")
    private String visible;
    /** 是否缓存 */
    @TableField("keep_alive")
    private String keepAlive;
    /** 目录默认跳转路径 */
    @TableField("redirect")
    private String redirect;
    /** 权限标识 */
    @TableField("perms")
    private String perms;
    /** 菜单类型 */
    @TableField("menu_type")
    private String menuType;
    /** 排序 */
    @TableField("order_num")
    private Integer orderNum;
    /** 状态 */
    @TableField("status")
    private String status;
}
