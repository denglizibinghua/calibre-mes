package com.bikeassembly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
@TableName("eqm_maintenance")
public class EqmMaintenance implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 维护ID */
    @TableId(value = "maintenance_id", type = IdType.AUTO)
    private Long maintenanceId;
    /** 设备ID */
    @TableField("equipment_id")
    private Long equipmentId;
    /** 维护类型 */
    @TableField("maintenance_type")
    private String maintenanceType;
    /** 计划时间 */
    @TableField("plan_time")
    private LocalDateTime planTime;
    /** 完成时间 */
    @TableField("finish_time")
    private LocalDateTime finishTime;
    /** 责任人 */
    @TableField("responsible_user")
    private String responsibleUser;
    /** 状态 */
    @TableField("status")
    private String status;
    /** 备注 */
    @TableField("remark")
    private String remark;
}
