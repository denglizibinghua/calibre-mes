package com.bikeassembly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
@TableName("base_work_unit")
public class BaseWorkUnit implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 生产单元ID */
    @TableId(value = "unit_id", type = IdType.AUTO)
    private Long unitId;
    /** 父级ID */
    @TableField("parent_id")
    private Long parentId;
    /** 单元编码 */
    @TableField("unit_code")
    private String unitCode;
    /** 单元名称 */
    @TableField("unit_name")
    private String unitName;
    /** 单元类型 */
    @TableField("unit_type")
    private String unitType;
    /** 负责人 */
    @TableField("leader")
    private String leader;
    /** 状态 */
    @TableField("status")
    private String status;
    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;
}
