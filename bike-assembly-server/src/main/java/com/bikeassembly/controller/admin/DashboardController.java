package com.bikeassembly.controller.admin;

import com.bikeassembly.result.Result;
import com.bikeassembly.service.DashboardService;
import com.bikeassembly.vo.DashboardVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 生产运营看板接口。
 */
@RestController
@RequestMapping("/admin/dashboard")
@Slf4j
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    /**
     * 看板聚合数据（登录用户首页可见）。
     */
    @GetMapping("/overview")
    public Result<DashboardVO> overview() {
        return Result.success(dashboardService.overview());
    }
}
