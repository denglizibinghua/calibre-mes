package com.bikeassembly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
@TableName("base_equipment")
public class BaseEquipment implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 设备ID */
    @TableId(value = "equipment_id", type = IdType.AUTO)
    private Long equipmentId;
    /** 设备编码 */
    @TableField("equipment_code")
    private String equipmentCode;
    /** 设备名称 */
    @TableField("equipment_name")
    private String equipmentName;
    /** 所属生产单元 */
    @TableField("unit_id")
    private Long unitId;
    /** 规格型号 */
    @TableField("model")
    private String model;
    /** 设备状态（0=启用 1=禁用） */
    @TableField("status")
    private String status;
    /** 设备运行状态（0=正常 1=故障/维修中） */
    @TableField("running_status")
    private String runningStatus;
    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;
}
