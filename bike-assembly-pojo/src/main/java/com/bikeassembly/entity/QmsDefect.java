package com.bikeassembly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
@TableName("qms_defect")
public class QmsDefect implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 不合格品ID */
    @TableId(value = "defect_id", type = IdType.AUTO)
    private Long defectId;
    /** 质检单ID */
    @TableField("inspection_id")
    private Long inspectionId;
    /** 工单ID */
    @TableField("work_order_id")
    private Long workOrderId;
    /** 工序ID */
    @TableField("process_id")
    private Long processId;
    /** 缺陷类型 */
    @TableField("defect_type")
    private String defectType;
    /** 数量 */
    @TableField("qty")
    private BigDecimal qty;
    /** 处理结果 */
    @TableField("handle_result")
    private String handleResult;
    /** 状态 */
    @TableField("status")
    private String status;
    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;
}
