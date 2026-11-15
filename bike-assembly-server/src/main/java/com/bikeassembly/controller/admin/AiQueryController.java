package com.bikeassembly.controller.admin;

import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.PermissionConstant;
import com.bikeassembly.dto.AiQueryDTO;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.AiQueryService;
import com.bikeassembly.vo.AiQueryVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/ai")
@RequiredArgsConstructor
public class AiQueryController {
    private final AiQueryService aiQueryService;

    @RequirePermission(PermissionConstant.ADMIN_AI_VIEW)
    @PostMapping("/query")
    public Result<AiQueryVO> query(@Valid @RequestBody AiQueryDTO dto) {
        return Result.success(aiQueryService.ask(dto));
    }
}
