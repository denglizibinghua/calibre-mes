package com.bikeassembly.vo.report;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class AndonReportVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long alertId;
    private String alertNo;
    private String alertType;
    private String alertLevel;
    private Long unitId;
    private String unitName;
    private Long equipmentId;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime handleTime;
}
