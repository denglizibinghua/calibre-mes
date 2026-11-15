package com.bikeassembly.vo;

import lombok.Data;

@Data
public class WorkOrderQrCodeVO {
    private Long workOrderId;
    private String workOrderNo;
    private String content;
}
