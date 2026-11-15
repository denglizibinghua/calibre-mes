package com.bikeassembly.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class SysMenuVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 菜单ID */
    private Long menuId;
    /** 父菜单ID */
    private Long parentId;
    /** 菜单名称 */
    private String menuName;
    /** 路由路径 */
    private String path;
    /** 前端组件路径 */
    private String component;
    /** 菜单图标 */
    private String icon;
    /** 是否可见 */
    private String visible;
    /** 是否缓存 */
    private String keepAlive;
    /** 目录默认跳转路径 */
    private String redirect;
    /** 权限标识 */
    private String perms;
    /** 菜单类型 */
    private String menuType;
    /** 排序 */
    private Integer orderNum;
    /** 状态 */
    private String status;
    /** 子菜单（树形结构） */
    private List<SysMenuVO> children;
}
