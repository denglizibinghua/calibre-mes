package com.bikeassembly.service;

import com.bikeassembly.dto.report.AndonReportDTO;
import com.bikeassembly.dto.report.EquipmentRepairReportDTO;
import com.bikeassembly.dto.report.InventoryReportDTO;
import com.bikeassembly.dto.report.QualityReportDTO;
import com.bikeassembly.dto.report.WorkOrderReportDTO;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.vo.report.AndonReportExcelVO;
import com.bikeassembly.vo.report.AndonReportVO;
import com.bikeassembly.vo.report.EquipmentRepairReportExcelVO;
import com.bikeassembly.vo.report.EquipmentRepairReportVO;
import com.bikeassembly.vo.report.InventoryReportExcelVO;
import com.bikeassembly.vo.report.InventoryReportVO;
import com.bikeassembly.vo.report.QualityReportExcelVO;
import com.bikeassembly.vo.report.QualityReportVO;
import com.bikeassembly.vo.report.WorkOrderReportExcelVO;
import com.bikeassembly.vo.report.WorkOrderReportVO;

import java.util.List;

public interface ReportService {

    PageResult<WorkOrderReportVO> queryWorkOrderReport(WorkOrderReportDTO dto);

    PageResult<InventoryReportVO> queryInventoryReport(InventoryReportDTO dto);

    PageResult<QualityReportVO> queryQualityReport(QualityReportDTO dto);

    PageResult<EquipmentRepairReportVO> queryEquipmentRepairReport(EquipmentRepairReportDTO dto);

    PageResult<AndonReportVO> queryAndonReport(AndonReportDTO dto);

    // ========== 导出（ExcelVO 映射，保持全量导出，带数据权限过滤） ==========

    List<WorkOrderReportExcelVO> exportWorkOrderReport(WorkOrderReportDTO dto);

    List<InventoryReportExcelVO> exportInventoryReport(InventoryReportDTO dto);

    List<QualityReportExcelVO> exportQualityReport(QualityReportDTO dto);

    List<EquipmentRepairReportExcelVO> exportEquipmentRepairReport(EquipmentRepairReportDTO dto);

    List<AndonReportExcelVO> exportAndonReport(AndonReportDTO dto);
}
