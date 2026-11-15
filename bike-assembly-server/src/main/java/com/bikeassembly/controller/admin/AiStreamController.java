package com.bikeassembly.controller.admin;

import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.PermissionConstant;
import com.bikeassembly.context.BaseContext;
import com.bikeassembly.dto.AiQueryDTO;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.AiStreamService;
import com.bikeassembly.vo.AiStreamStatusVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@RestController
@RequestMapping("/admin/ai")
@RequiredArgsConstructor
public class AiStreamController {
    private final AiStreamService aiStreamService;

    @RequirePermission(PermissionConstant.ADMIN_AI_VIEW)
    @PostMapping("/stream")
    public Result<Map<String, String>> stream(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody AiQueryDTO dto) {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) return Result.error("未获取到当前用户");
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            return Result.error("Idempotency-Key 必填且长度不能超过128");
        }
        try {
            return Result.success(Map.of("requestId", aiStreamService.submit(userId, idempotencyKey, dto)));
        } catch (IllegalStateException e) {
            return Result.error(e.getMessage());
        }
    }

    @RequirePermission(PermissionConstant.ADMIN_AI_VIEW)
    @GetMapping("/stream/{requestId}")
    public Result<AiStreamStatusVO> status(@PathVariable String requestId) {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) return Result.error("未获取到当前用户");
        AiStreamStatusVO status = aiStreamService.getStatus(userId, requestId);
        if (status == null) return Result.error("流请求不存在或已过期");
        return Result.success(status);
    }

    @RequirePermission(PermissionConstant.ADMIN_AI_VIEW)
    @DeleteMapping("/stream/{requestId}")
    public Result<Void> cancel(@PathVariable String requestId) {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) return Result.error("未获取到当前用户");
        if (!aiStreamService.cancel(userId, requestId)) {
            return Result.error("流请求不存在、已结束或无权取消");
        }
        return Result.success();
    }
}
