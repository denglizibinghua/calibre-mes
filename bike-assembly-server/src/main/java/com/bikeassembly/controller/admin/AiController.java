package com.bikeassembly.controller.admin;

import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.PermissionConstant;
import com.bikeassembly.dto.AiDefectAnalysisDTO;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.AiAnalysisService;
import com.bikeassembly.vo.AiAnalysisVO;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 能力接口。
 */
@RestController
@RequestMapping("/admin/ai")
@Slf4j
public class AiController {

    @Autowired
    private AiAnalysisService aiAnalysisService;

    /**
     * 质检不合格原因辅助分析（AI 未配置/失败时返回降级结果，不影响主流程）。
     */
    @RequirePermission(PermissionConstant.ADMIN_INSPECTION_VIEW)
    @PostMapping("/defect-analysis")
    public Result<AiAnalysisVO> defectAnalysis(@Valid @RequestBody AiDefectAnalysisDTO dto) {
        AiAnalysisVO vo = aiAnalysisService.analyzeDefect(dto.getInspectionId());
        return Result.success(vo);
    }
}
