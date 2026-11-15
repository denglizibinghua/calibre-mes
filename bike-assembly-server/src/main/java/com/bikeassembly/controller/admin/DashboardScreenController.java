package com.bikeassembly.controller.admin;

import com.bikeassembly.dto.DashboardScreenQueryDTO;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.DashboardScreenService;
import com.bikeassembly.vo.DashboardScreenVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class DashboardScreenController {
    private final DashboardScreenService dashboardScreenService;

    /** Authenticated dashboard users; record visibility is enforced by the service. */
    @GetMapping("/screen")
    public Result<DashboardScreenVO> screen(@Valid DashboardScreenQueryDTO query) {
        return Result.success(dashboardScreenService.screen(query));
    }

    /** Keep validation errors on this new endpoint at HTTP 400 without changing legacy APIs. */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> invalidQuery(BindException exception) {
        String message = exception.getFieldErrors().stream().map(error -> error.isBindingFailure()
                ? ("days".equals(error.getField()) ? "统计天数必须是整数" : "生产单元ID必须是整数")
                : error.getDefaultMessage()).filter(java.util.Objects::nonNull).distinct()
                .collect(java.util.stream.Collectors.joining("；"));
        return ResponseEntity.badRequest().body(Result.error(message.isBlank() ? "筛选参数有误" : message));
    }
}
