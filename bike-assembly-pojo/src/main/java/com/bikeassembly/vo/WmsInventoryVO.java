package com.bikeassembly.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class WmsInventoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 库存ID */
    private Long inventoryId;
    /** 物料ID */
    private Long itemId;
    /** 库位ID */
    private Long locationId;
    /** 库存数量 */
    private BigDecimal qty;
    /** 锁定数量 */
    private BigDecimal lockedQty;
    /** 批次号 */
    private String batchNo;
    /** 更新时间 */
    private LocalDateTime updateTime;

    // ===== 关联展示字段（来自 base_item / wms_location JOIN） =====

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 库位编码 */
    private String locationCode;

    /** 库位名称 */
    private String locationName;

    /** 物料单位 */
    private String unit;
}
