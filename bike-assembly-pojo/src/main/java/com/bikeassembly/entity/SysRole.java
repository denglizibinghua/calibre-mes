package com.bikeassembly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
@TableName("sys_role")
public class SysRole implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 角色ID */
    @TableId(value = "role_id", type = IdType.AUTO)
    private Long roleId;
    /** 角色名称 */
    @TableField("role_name")
    private String roleName;
    /** 角色编码 */
    @TableField("role_key")
    private String roleKey;
    /** 数据范围（ALL=全部 SELF=本人） */
    @TableField("data_scope")
    private String dataScope;
    /** 状态 */
    @TableField("status")
    private String status;
    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;
}
