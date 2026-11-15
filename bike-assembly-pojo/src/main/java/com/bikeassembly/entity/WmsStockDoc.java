package com.bikeassembly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
@TableName("wms_stock_doc")
public class WmsStockDoc implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 单据ID */
    @TableId(value = "doc_id", type = IdType.AUTO)
    private Long docId;
    /** 单据编号 */
    @TableField("doc_no")
    private String docNo;
    /** 单据类型 */
    @TableField("doc_type")
    private String docType;
    /** 关联单号 */
    @TableField("related_order_no")
    private String relatedOrderNo;
    /** 单据状态 */
    @TableField("status")
    private String status;
    /** 明细JSON */
    @TableField("details_json")
    private String detailsJson;
    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;
}
