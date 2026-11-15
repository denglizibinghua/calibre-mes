package com.bikeassembly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
@TableName("eqm_repair_order")
public class EqmRepairOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 维修单ID */
    @TableId(value = "repair_id", type = IdType.AUTO)
    private Long repairId;
    /** 设备ID */
    @TableField("equipment_id")
    private Long equipmentId;
    /** 故障描述 */
    @TableField("fault_desc")
    private String faultDesc;
    /** 报修人 */
    @TableField("report_user")
    private String reportUser;
    /** 报修人ID */
    @TableField("reporter_user_id")
    private Long reporterUserId;
    /** 报修时间 */
    @TableField("report_time")
    private LocalDateTime reportTime;
    /** 维修人 */
    @TableField("repair_user")
    private String repairUser;
    /** 维修人ID */
    @TableField("handler_user_id")
    private Long handlerUserId;
    /** 完成时间 */
    @TableField("finish_time")
    private LocalDateTime finishTime;
    /** 状态 */
    @TableField("status")
    private String status;
    /** 维修结果 */
    @TableField("result_desc")
    private String resultDesc;
}
