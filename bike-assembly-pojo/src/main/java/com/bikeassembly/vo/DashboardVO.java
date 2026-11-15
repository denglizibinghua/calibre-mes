package com.bikeassembly.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 生产运营看板聚合数据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户总数 */
    private long userCount;
    /** 物料总数 */
    private long itemCount;
    /** 设备总数 */
    private long equipmentCount;
    /** 工单总数 */
    private long workOrderCount;
    /** 今日完工工单数 */
    private long todayCompletedWorkOrders;
    /** 今日产量（今日报工数量合计） */
    private BigDecimal todayReportedQty;
    /** 在制工单数（生产中 + 暂停） */
    private long inProgressWorkOrders;
    /** 待检质检单数 */
    private long pendingInspections;
    /** 今日安灯告警数 */
    private long todayAndonAlerts;
    /** 待处理安灯数 */
    private long pendingAndonAlerts;
    /** 设备运行中（启用且可运行） */
    private long equipmentRunning;
    /** 设备故障/维修中（启用但不可运行） */
    private long equipmentFault;
    /** 工单状态分布 [{name: 状态码, value: 数量}] */
    private List<Map<String, Object>> workOrderStatusDistribution;
    /** 安灯状态分布 [{name: 状态码, value: 数量}] */
    private List<Map<String, Object>> andonStatusDistribution;
}
