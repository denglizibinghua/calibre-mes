package com.bikeassembly.vo.report;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InventoryReportVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long inventoryId;
    private Long itemId;
    private String itemCode;
    private String itemName;
    private Long locationId;
    private String locationCode;
    private String locationName;
    private BigDecimal quantity;
    private BigDecimal lockedQuantity;
    private String unit;
    private String batchNo;
    private LocalDateTime updateTime;
}
