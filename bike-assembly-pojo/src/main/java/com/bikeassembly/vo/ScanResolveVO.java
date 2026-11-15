package com.bikeassembly.vo;

import lombok.Data;
import java.util.List;

@Data
public class ScanResolveVO {
    private String businessType;
    private Long workOrderId;
    private String workOrderNo;
    private List<String> actions;
    private Long pendingInspectionCount;
}
