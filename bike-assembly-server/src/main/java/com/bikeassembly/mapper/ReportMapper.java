package com.bikeassembly.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bikeassembly.dto.report.AndonReportDTO;
import com.bikeassembly.dto.report.EquipmentRepairReportDTO;
import com.bikeassembly.dto.report.InventoryReportDTO;
import com.bikeassembly.dto.report.QualityReportDTO;
import com.bikeassembly.dto.report.WorkOrderReportDTO;
import com.bikeassembly.vo.report.AndonReportVO;
import com.bikeassembly.vo.report.EquipmentRepairReportVO;
import com.bikeassembly.vo.report.InventoryReportVO;
import com.bikeassembly.vo.report.QualityReportVO;
import com.bikeassembly.vo.report.WorkOrderReportVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReportMapper {

    /**
     * 生产工单报表分页查询。
     *
     * @param page   MyBatis-Plus 分页参数（真实 SQL 分页）
     * @param dto    查询条件
     * @param userId 数据范围 userId；ALL 数据范围传 null 表示不过滤
     */
    IPage<WorkOrderReportVO> queryWorkOrderReport(Page<?> page,
                                                  @Param("dto") WorkOrderReportDTO dto,
                                                  @Param("userId") Long userId);

    /**
     * 生产工单报表全量查询（导出用，带数据权限过滤）。
     */
    List<WorkOrderReportVO> listWorkOrderReport(@Param("dto") WorkOrderReportDTO dto,
                                                @Param("userId") Long userId);

    /**
     * 库存报表分页查询。
     */
    IPage<InventoryReportVO> queryInventoryReport(Page<?> page,
                                                  @Param("dto") InventoryReportDTO dto,
                                                  @Param("userId") Long userId);

    /**
     * 库存报表全量查询（导出用，带数据权限过滤）。
     */
    List<InventoryReportVO> listInventoryReport(@Param("dto") InventoryReportDTO dto,
                                                @Param("userId") Long userId);

    /**
     * 质量检验报表分页查询。
     */
    IPage<QualityReportVO> queryQualityReport(Page<?> page,
                                              @Param("dto") QualityReportDTO dto,
                                              @Param("userId") Long userId);

    /**
     * 质量检验报表全量查询（导出用，带数据权限过滤）。
     */
    List<QualityReportVO> listQualityReport(@Param("dto") QualityReportDTO dto,
                                            @Param("userId") Long userId);

    /**
     * 设备维修报表分页查询。
     */
    IPage<EquipmentRepairReportVO> queryEquipmentRepairReport(Page<?> page,
                                                              @Param("dto") EquipmentRepairReportDTO dto,
                                                              @Param("userId") Long userId);

    /**
     * 设备维修报表全量查询（导出用，带数据权限过滤）。
     */
    List<EquipmentRepairReportVO> listEquipmentRepairReport(@Param("dto") EquipmentRepairReportDTO dto,
                                                            @Param("userId") Long userId);

    /**
     * 安灯告警报表分页查询。
     */
    IPage<AndonReportVO> queryAndonReport(Page<?> page,
                                          @Param("dto") AndonReportDTO dto,
                                          @Param("userId") Long userId);

    /**
     * 安灯告警报表全量查询（导出用，带数据权限过滤）。
     */
    List<AndonReportVO> listAndonReport(@Param("dto") AndonReportDTO dto,
                                        @Param("userId") Long userId);
}
