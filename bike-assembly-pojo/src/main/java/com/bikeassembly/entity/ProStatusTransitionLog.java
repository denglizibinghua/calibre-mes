package com.bikeassembly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 状态流转日志实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("pro_status_transition_log")
public class ProStatusTransitionLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "log_id", type = IdType.AUTO)
    private Long logId;

    /** 实体类型: WORK_ORDER / WORK_PROCESS */
    @TableField("entity_type")
    private String entityType;

    /** 实体主键ID */
    @TableField("entity_id")
    private Long entityId;

    /** 变更前状态 */
    @TableField("from_status")
    private String fromStatus;

    /** 变更后状态 */
    @TableField("to_status")
    private String toStatus;

    /** 操作动作 */
    @TableField("action")
    private String action;

    /** 操作人ID */
    @TableField("operator_user_id")
    private Long operatorUserId;

    /** 操作人姓名 */
    @TableField("operator_name")
    private String operatorName;

    /** 备注 */
    @TableField("remark")
    private String remark;

    /** 变更时间 */
    @TableField("transition_time")
    private LocalDateTime transitionTime;
}
