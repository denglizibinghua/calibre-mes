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
@TableName("wms_inventory")
public class WmsInventory implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 库存ID */
    @TableId(value = "inventory_id", type = IdType.AUTO)
    private Long inventoryId;
    /** 物料ID */
    @TableField("item_id")
    private Long itemId;
    /** 库位ID */
    @TableField("location_id")
    private Long locationId;
    /** 库存数量 */
    @TableField("qty")
    private BigDecimal qty;
    /** 锁定数量 */
    @TableField("locked_qty")
    private BigDecimal lockedQty;
    /** 批次号 */
    @TableField("batch_no")
    private String batchNo;
    /** 更新时间 */
    @TableField("update_time")
    private LocalDateTime updateTime;

    // ===== 以下为 JOIN 查询带入的展示字段（非数据库列） =====

    /** 物料编码（关联 base_item.item_code） */
    @TableField(exist = false)
    private String itemCode;

    /** 物料名称（关联 base_item.item_name） */
    @TableField(exist = false)
    private String itemName;

    /** 库位编码（关联 wms_location.location_code） */
    @TableField(exist = false)
    private String locationCode;

    /** 库位名称（关联 wms_location.location_name） */
    @TableField(exist = false)
    private String locationName;

    /** 物料单位（关联 base_item.unit） */
    @TableField(exist = false)
    private String unit;
}
