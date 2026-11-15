package com.bikeassembly.service.impl;

import com.bikeassembly.mapper.DashboardMapper;
import com.bikeassembly.service.DashboardService;
import com.bikeassembly.vo.DashboardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 生产运营看板服务实现：调用真实 SQL 聚合。
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final DashboardMapper dashboardMapper;

    @Override
    public DashboardVO overview() {
        return DashboardVO.builder()
                .userCount(dashboardMapper.countUsers())
                .itemCount(dashboardMapper.countItems())
                .equipmentCount(dashboardMapper.countEquipment())
                .workOrderCount(dashboardMapper.countWorkOrders())
                .todayCompletedWorkOrders(dashboardMapper.countTodayCompletedWorkOrders())
                .todayReportedQty(dashboardMapper.sumTodayReportedQty())
                .inProgressWorkOrders(dashboardMapper.countInProgressWorkOrders())
                .pendingInspections(dashboardMapper.countPendingInspections())
                .todayAndonAlerts(dashboardMapper.countTodayAndonAlerts())
                .pendingAndonAlerts(dashboardMapper.countPendingAndonAlerts())
                .equipmentRunning(dashboardMapper.countEquipmentRunning())
                .equipmentFault(dashboardMapper.countEquipmentFault())
                .workOrderStatusDistribution(dashboardMapper.workOrderStatusDistribution())
                .andonStatusDistribution(dashboardMapper.andonStatusDistribution())
                .build();
    }
}
