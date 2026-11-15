package com.bikeassembly.dto;

import com.bikeassembly.validation.OnCreate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class SysMenuDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 菜单ID */
    private Long menuId;
    /** 父菜单ID */
    private Long parentId;
    /** 菜单名称 */
    @NotBlank(message = "菜单名称不能为空", groups = OnCreate.class)
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
    @Size(max = 1, message = "菜单类型长度不能超过1")
    private String menuType;
    /** 排序 */
    private Integer orderNum;
    /** 状态 */
    @Size(max = 1, message = "状态长度不能超过1")
    private String status;
}
