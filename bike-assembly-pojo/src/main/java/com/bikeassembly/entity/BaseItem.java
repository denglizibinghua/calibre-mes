package com.bikeassembly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
@TableName("base_item")
public class BaseItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 物料ID */
    @TableId(value = "item_id", type = IdType.AUTO)
    private Long itemId;
    /** 物料编码 */
    @TableField("item_code")
    private String itemCode;
    /** 物料名称 */
    @TableField("item_name")
    private String itemName;
    /** 物料类型 */
    @TableField("item_type")
    private String itemType;
    /** 规格型号 */
    @TableField("spec_model")
    private String specModel;
    /** 单位 */
    @TableField("unit")
    private String unit;
    /** 状态 */
    @TableField("status")
    private String status;
    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;
}
