package com.bikeassembly.vo.report;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class WorkOrderReportVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long workOrderId;
    private String workOrderNo;
    private String orderNo;
    private Long itemId;
    private String itemCode;
    private String itemName;
    private BigDecimal planQty;
    private String unit;
    private String status;
    private LocalDateTime planStartTime;
    private LocalDateTime planEndTime;
    private LocalDateTime createTime;
}
