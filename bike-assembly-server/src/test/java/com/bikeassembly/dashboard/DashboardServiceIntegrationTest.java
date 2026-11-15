package com.bikeassembly.dashboard;

import com.bikeassembly.mapper.ProWorkOrderMapper;
import com.bikeassembly.service.DashboardService;
import com.bikeassembly.vo.DashboardVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 生产运营看板集成测试（本地 MySQL，真实 SQL 聚合）。
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("生产运营看板集成测试")
class DashboardServiceIntegrationTest {

    @Autowired
    private DashboardService dashboardService;
    @Autowired
    private ProWorkOrderMapper proWorkOrderMapper;

    @Test
    @DisplayName("看板聚合数据与库内真实计数一致")
    void overview_matchesDatabaseCounts() {
        DashboardVO vo = dashboardService.overview();

        assertNotNull(vo.getTodayReportedQty(), "今日产量不应为 null");
        assertEquals(proWorkOrderMapper.selectCount(null), vo.getWorkOrderCount(),
                "工单总数应与库内一致");
        assertNotNull(vo.getWorkOrderStatusDistribution(), "工单状态分布不应为 null");
        assertNotNull(vo.getAndonStatusDistribution(), "安灯状态分布不应为 null");
    }
}
