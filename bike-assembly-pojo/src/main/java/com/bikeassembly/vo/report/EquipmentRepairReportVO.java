package com.bikeassembly.vo.report;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class EquipmentRepairReportVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long repairId;
    private Long equipmentId;
    private String equipmentCode;
    private String equipmentName;
    private String faultDesc;
    private String reportUser;
    private String repairUser;
    private String status;
    private LocalDateTime reportTime;
    private LocalDateTime finishTime;
    private String resultDesc;
}
