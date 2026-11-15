package com.bikeassembly.service;

import com.bikeassembly.vo.ScanResolveVO;
import com.bikeassembly.vo.WorkOrderQrCodeVO;

public interface WorkOrderScanService {
    ScanResolveVO resolve(String content);
    WorkOrderQrCodeVO getQrCode(Long workOrderId);
}
