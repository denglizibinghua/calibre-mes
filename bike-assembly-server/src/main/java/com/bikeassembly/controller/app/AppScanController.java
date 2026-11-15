package com.bikeassembly.controller.app;

import com.bikeassembly.dto.ScanResolveDTO;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.WorkOrderScanService;
import com.bikeassembly.vo.ScanResolveVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/app/scan")
@RequiredArgsConstructor
public class AppScanController {
    private final WorkOrderScanService scanService;

    /** 各入口权限由Service分别判定，不能要求质检员拥有生产工单查看权限。 */
    @PostMapping("/resolve")
    public Result<ScanResolveVO> resolve(@Valid @RequestBody ScanResolveDTO dto) {
        return Result.success(scanService.resolve(dto.getContent()));
    }
}
