package com.bikeassembly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
@TableName("base_process")
public class BaseProcess implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 工序ID */
    @TableId(value = "process_id", type = IdType.AUTO)
    private Long processId;
    /** 工序编码 */
    @TableField("process_code")
    private String processCode;
    /** 工序名称 */
    @TableField("process_name")
    private String processName;
    /** 标准工时分钟 */
    @TableField("standard_minutes")
    private Integer standardMinutes;
    /** 状态 */
    @TableField("status")
    private String status;
    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;
}
