package com.bikeassembly.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 角色选项（轻量 POJO，用于返回给前端角色选择器）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleOption implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 角色 ID */
    private Long roleId;

    /** 角色名称（如"管理员"、"普通用户"） */
    private String roleName;
}
