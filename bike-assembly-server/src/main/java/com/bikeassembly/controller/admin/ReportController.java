package com.bikeassembly.controller.admin;

import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.PermissionConstant;
import com.alibaba.excel.EasyExcel;
import com.bikeassembly.dto.report.AndonReportDTO;
import com.bikeassembly.dto.report.EquipmentRepairReportDTO;
import com.bikeassembly.dto.report.InventoryReportDTO;
import com.bikeassembly.dto.report.QualityReportDTO;
import com.bikeassembly.dto.report.WorkOrderReportDTO;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.ReportService;
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
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/admin/report")
@Slf4j
public class ReportController {

    @Autowired
    private ReportService reportService;

    // ==================== 工单报表 ====================

    @GetMapping("/work-order")
    @RequirePermission(PermissionConstant.ADMIN_REPORT_VIEW)
    public Result<PageResult<WorkOrderReportVO>> queryWorkOrder(WorkOrderReportDTO dto) {
        log.info("查询生产工单报表: {}", dto);
        return Result.success(reportService.queryWorkOrderReport(dto));
    }

    @GetMapping("/work-order/export")
    @RequirePermission(PermissionConstant.ADMIN_REPORT_EXPORT)
    public void exportWorkOrder(WorkOrderReportDTO dto, HttpServletResponse response) throws IOException {
        List<WorkOrderReportExcelVO> excelList = reportService.exportWorkOrderReport(dto);
        setExcelResponse(response, "生产工单报表_" + today());
        EasyExcel.write(response.getOutputStream(), WorkOrderReportExcelVO.class).sheet("工单报表").doWrite(excelList);
    }

    // ==================== 库存报表 ====================

    @GetMapping("/inventory")
    @RequirePermission(PermissionConstant.ADMIN_REPORT_VIEW)
    public Result<PageResult<InventoryReportVO>> queryInventory(InventoryReportDTO dto) {
        log.info("查询库存报表: {}", dto);
        return Result.success(reportService.queryInventoryReport(dto));
    }

    @GetMapping("/inventory/export")
    @RequirePermission(PermissionConstant.ADMIN_REPORT_EXPORT)
    public void exportInventory(InventoryReportDTO dto, HttpServletResponse response) throws IOException {
        List<InventoryReportExcelVO> excelList = reportService.exportInventoryReport(dto);
        setExcelResponse(response, "库存报表_" + today());
        EasyExcel.write(response.getOutputStream(), InventoryReportExcelVO.class).sheet("库存报表").doWrite(excelList);
    }

    // ==================== 质检报表 ====================

    @GetMapping("/quality")
    @RequirePermission(PermissionConstant.ADMIN_REPORT_VIEW)
    public Result<PageResult<QualityReportVO>> queryQuality(QualityReportDTO dto) {
        log.info("查询质检报表: {}", dto);
        return Result.success(reportService.queryQualityReport(dto));
    }

    @GetMapping("/quality/export")
    @RequirePermission(PermissionConstant.ADMIN_REPORT_EXPORT)
    public void exportQuality(QualityReportDTO dto, HttpServletResponse response) throws IOException {
        List<QualityReportExcelVO> excelList = reportService.exportQualityReport(dto);
        setExcelResponse(response, "质检报表_" + today());
        EasyExcel.write(response.getOutputStream(), QualityReportExcelVO.class).sheet("质检报表").doWrite(excelList);
    }

    // ==================== 设备维修报表 ====================

    @GetMapping("/equipment-repair")
    @RequirePermission(PermissionConstant.ADMIN_REPORT_VIEW)
    public Result<PageResult<EquipmentRepairReportVO>> queryEquipmentRepair(EquipmentRepairReportDTO dto) {
        log.info("查询设备维修报表: {}", dto);
        return Result.success(reportService.queryEquipmentRepairReport(dto));
    }

    @GetMapping("/equipment-repair/export")
    @RequirePermission(PermissionConstant.ADMIN_REPORT_EXPORT)
    public void exportEquipmentRepair(EquipmentRepairReportDTO dto, HttpServletResponse response) throws IOException {
        List<EquipmentRepairReportExcelVO> excelList = reportService.exportEquipmentRepairReport(dto);
        setExcelResponse(response, "设备维修报表_" + today());
        EasyExcel.write(response.getOutputStream(), EquipmentRepairReportExcelVO.class).sheet("维修报表").doWrite(excelList);
    }

    // ==================== 安灯告警报表 ====================

    @GetMapping("/andon")
    @RequirePermission(PermissionConstant.ADMIN_REPORT_VIEW)
    public Result<PageResult<AndonReportVO>> queryAndon(AndonReportDTO dto) {
        log.info("查询安灯告警报表: {}", dto);
        return Result.success(reportService.queryAndonReport(dto));
    }

    @GetMapping("/andon/export")
    @RequirePermission(PermissionConstant.ADMIN_REPORT_EXPORT)
    public void exportAndon(AndonReportDTO dto, HttpServletResponse response) throws IOException {
        List<AndonReportExcelVO> excelList = reportService.exportAndonReport(dto);
        setExcelResponse(response, "安灯告警报表_" + today());
        EasyExcel.write(response.getOutputStream(), AndonReportExcelVO.class).sheet("安灯告警报表").doWrite(excelList);
    }

    // ==================== 工具方法 ====================

    private String today() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    private void setExcelResponse(HttpServletResponse response, String fileName) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename=" + encoded + ".xlsx");
    }
}
