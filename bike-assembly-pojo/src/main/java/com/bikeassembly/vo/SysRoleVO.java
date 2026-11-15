package com.bikeassembly.vo;

import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class SysRoleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 角色ID */
    private Long roleId;
    /** 角色名称 */
    private String roleName;
    /** 角色编码 */
    private String roleKey;
    /** 数据范围 */
    private String dataScope;
    /** 状态 */
    private String status;
    /** 创建时间 */
    private LocalDateTime createTime;
}
