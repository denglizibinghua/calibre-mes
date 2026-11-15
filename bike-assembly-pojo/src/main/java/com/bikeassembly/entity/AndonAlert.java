package com.bikeassembly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
@TableName("andon_alert")
public class AndonAlert implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 告警ID */
    @TableId(value = "alert_id", type = IdType.AUTO)
    private Long alertId;
    /** 告警编号 */
    @TableField("alert_no")
    private String alertNo;
    /** 工单ID */
    @TableField("work_order_id")
    private Long workOrderId;
    /** 生产单元ID */
    @TableField("unit_id")
    private Long unitId;
    /** 设备ID */
    @TableField("equipment_id")
    private Long equipmentId;
    /** 告警类型 */
    @TableField("alert_type")
    private String alertType;
    /** 告警等级 */
    @TableField("alert_level")
    private String alertLevel;
    /** 状态 */
    @TableField("status")
    private String status;
    /** 上报人ID */
    @TableField("reporter_user_id")
    private Long reporterUserId;
    /** 处理人ID */
    @TableField("handler_user_id")
    private Long handlerUserId;
    /** 告警时间 */
    @TableField("create_time")
    private LocalDateTime createTime;
    /** 处理时间 */
    @TableField("handle_time")
    private LocalDateTime handleTime;
    /** 升级状态：0=未升级 1=已升级 */
    @TableField("escalation_status")
    private String escalationStatus;
    /** 升级人ID */
    @TableField("escalated_by")
    private Long escalatedBy;
    /** 升级时间 */
    @TableField("escalation_time")
    private LocalDateTime escalationTime;
    /** 升级备注 */
    @TableField("escalation_remark")
    private String escalationRemark;
}
