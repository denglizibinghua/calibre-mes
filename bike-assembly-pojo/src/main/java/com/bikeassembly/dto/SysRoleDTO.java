package com.bikeassembly.dto;

import com.bikeassembly.validation.OnCreate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class SysRoleDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 角色ID */
    private Long roleId;
    /** 角色名称 */
    @NotBlank(message = "角色名称不能为空", groups = OnCreate.class)
    private String roleName;
    /** 角色编码 */
    @NotBlank(message = "角色编码不能为空", groups = OnCreate.class)
    private String roleKey;
    /** 数据范围 */
    @Size(max = 16, message = "数据范围长度不能超过16")
    private String dataScope;
    /** 状态 */
    @Size(max = 1, message = "状态长度不能超过1")
    private String status;
    /** 分配菜单/权限ID列表 */
    private java.util.List<Long> menuIds;
    /** 创建时间 */
    private LocalDateTime createTime;
}
