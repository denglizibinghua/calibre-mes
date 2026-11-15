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
@TableName("pro_work_process")
public class ProWorkProcess implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 流转记录ID */
    @TableId(value = "process_record_id", type = IdType.AUTO)
    private Long processRecordId;
    /** 工单ID */
    @TableField("work_order_id")
    private Long workOrderId;
    /** 工序ID */
    @TableField("process_id")
    private Long processId;
    /** 设备ID */
    @TableField("equipment_id")
    private Long equipmentId;
    /** 操作人 */
    @TableField("operator")
    private String operator;
    /** 操作人ID */
    @TableField("operator_user_id")
    private Long operatorUserId;
    /** 开始时间 */
    @TableField("start_time")
    private LocalDateTime startTime;
    /** 结束时间 */
    @TableField("end_time")
    private LocalDateTime endTime;
    /** 报工数量 */
    @TableField("report_qty")
    private BigDecimal reportQty;
    /** 质检结果 */
    @TableField("qc_result")
    private String qcResult;
    /** 状态 */
    @TableField("status")
    private String status;
    /** 请求幂等键 */
    @TableField("request_id")
    private String requestId;
    /** 乐观锁版本号 */
    @TableField("version")
    @com.baomidou.mybatisplus.annotation.Version
    private Integer version;
    /** 工序顺序号（从1开始，根据工艺路线 steps_json 确定） */
    @TableField("step_seq")
    private Integer stepSeq;
    /** 返工次数 */
    @TableField("rework_count")
    private Integer reworkCount;
    /** 质检备注 */
    @TableField("qc_result_note")
    private String qcResultNote;
    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;
}
