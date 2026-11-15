package com.bikeassembly.controller.admin;

import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.annotation.IdempotencyRequired;
import com.bikeassembly.annotation.OperLog;
import com.bikeassembly.constant.PermissionConstant;
import com.bikeassembly.dto.AiQueryDTO;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.AiPlanService;
import com.bikeassembly.vo.AiPlanSuggestionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/ai/plans")
@RequiredArgsConstructor
public class AiPlanController {
    private final AiPlanService aiPlanService;

    @RequirePermission(PermissionConstant.ADMIN_AI_VIEW)
    @PostMapping
    @OperLog(module = "AI助手", operType = "生成排产建议")
    public Result<AiPlanSuggestionVO> create(@Valid @RequestBody AiQueryDTO dto) {
        return Result.success(aiPlanService.create(dto));
    }

    @RequirePermission(PermissionConstant.ADMIN_AI_VIEW)
    @GetMapping("/{planId}")
    public Result<AiPlanSuggestionVO> get(@PathVariable String planId) {
        return Result.success(aiPlanService.get(planId));
    }

    @RequirePermission(PermissionConstant.ADMIN_AI_PLAN_CONFIRM)
    @PostMapping("/{planId}/confirm")
    @IdempotencyRequired
    @OperLog(module = "AI助手", operType = "确认排产建议")
    public Result<AiPlanSuggestionVO> confirm(@PathVariable String planId) {
        try {
            return Result.success(aiPlanService.confirm(planId));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.error(e.getMessage());
        }
    }
}
