package com.bikeassembly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存流水：单据执行时每笔库存增减记录一条。
 */
@Data
@TableName("wms_stock_flow")
public class WmsStockFlow implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "flow_id", type = IdType.AUTO)
    private Long flowId;

    @TableField("doc_id")
    private Long docId;

    @TableField("doc_no")
    private String docNo;

    @TableField("inventory_id")
    private Long inventoryId;

    @TableField("item_id")
    private Long itemId;

    @TableField("location_id")
    private Long locationId;

    @TableField("batch_no")
    private String batchNo;

    /** 正=入库/移库入，负=出库/移库出 */
    @TableField("change_qty")
    private BigDecimal changeQty;

    @TableField("before_qty")
    private BigDecimal beforeQty;

    @TableField("after_qty")
    private BigDecimal afterQty;

    @TableField("flow_type")
    private String flowType;

    @TableField("related_order_no")
    private String relatedOrderNo;

    @TableField("create_by")
    private Long createBy;

    @TableField("create_time")
    private LocalDateTime createTime;
}
