package com.bikeassembly.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 生产运营看板聚合查询（真实 SQL 聚合，非内存统计）。
 */
@Mapper
public interface DashboardMapper {

    @Select("SELECT COUNT(*) FROM sys_user")
    long countUsers();

    @Select("SELECT COUNT(*) FROM base_item")
    long countItems();

    @Select("SELECT COUNT(*) FROM base_equipment")
    long countEquipment();

    @Select("SELECT COUNT(*) FROM pro_work_order")
    long countWorkOrders();

    @Select("SELECT COUNT(*) FROM pro_work_order WHERE status = '2' AND actual_end_time >= CURDATE()")
    long countTodayCompletedWorkOrders();

    @Select("SELECT COALESCE(SUM(report_qty), 0) FROM pro_work_process WHERE end_time >= CURDATE()")
    BigDecimal sumTodayReportedQty();

    @Select("SELECT COUNT(*) FROM pro_work_order WHERE status IN ('1', '5')")
    long countInProgressWorkOrders();

    @Select("SELECT COUNT(*) FROM qms_inspection WHERE status = '0'")
    long countPendingInspections();

    @Select("SELECT COUNT(*) FROM andon_alert WHERE create_time >= CURDATE()")
    long countTodayAndonAlerts();

    @Select("SELECT COUNT(*) FROM andon_alert WHERE status = '0'")
    long countPendingAndonAlerts();

    @Select("SELECT COUNT(*) FROM base_equipment WHERE status = '0' AND running_status = '0'")
    long countEquipmentRunning();

    @Select("SELECT COUNT(*) FROM base_equipment WHERE status = '0' AND running_status = '1'")
    long countEquipmentFault();

    @Select("SELECT status AS name, COUNT(*) AS value FROM pro_work_order GROUP BY status")
    List<Map<String, Object>> workOrderStatusDistribution();

    @Select("SELECT status AS name, COUNT(*) AS value FROM andon_alert GROUP BY status")
    List<Map<String, Object>> andonStatusDistribution();
}
