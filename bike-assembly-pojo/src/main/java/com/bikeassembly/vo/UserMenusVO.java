package com.bikeassembly.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 当前用户菜单与权限响应 VO。
 * 用于管理端动态路由和侧边栏渲染。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMenusVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 菜单树（仅目录和菜单页） */
    private List<SysMenuVO> menus;

    /** 当前用户所有权限编码（含按钮权限码） */
    private List<String> permissions;
}
