package com.bikeassembly.vo.report;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class QualityReportVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long inspectionId;
    private String inspectionNo;
    private Long workOrderId;
    private String workOrderNo;
    private String itemName;
    private String itemCode;
    private String result;
    private String inspector;
    private LocalDateTime inspectionTime;
    private String status;
}
