package com.bikeassembly.dto;

import com.bikeassembly.validation.OnCreate;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class WmsInventoryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 库存ID */
    private Long inventoryId;
    /** 物料ID */
    @NotNull(message = "物料不能为空", groups = OnCreate.class)
    private Long itemId;
    /** 库位ID */
    @NotNull(message = "库位不能为空", groups = OnCreate.class)
    private Long locationId;
    /** 库存数量 */
    private BigDecimal qty;
    /** 锁定数量 */
    private BigDecimal lockedQty;
    /** 批次号 */
    private String batchNo;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
