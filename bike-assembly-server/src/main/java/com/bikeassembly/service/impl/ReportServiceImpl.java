package com.bikeassembly.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bikeassembly.context.BaseContext;
import com.bikeassembly.dto.report.AndonReportDTO;
import com.bikeassembly.dto.report.EquipmentRepairReportDTO;
import com.bikeassembly.dto.report.InventoryReportDTO;
import com.bikeassembly.dto.report.QualityReportDTO;
import com.bikeassembly.dto.report.WorkOrderReportDTO;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.mapper.ReportMapper;
import com.bikeassembly.result.PageResult;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {

    @Autowired
    private ReportMapper reportMapper;

    @Override
    public PageResult<WorkOrderReportVO> queryWorkOrderReport(WorkOrderReportDTO dto) {
        Page<WorkOrderReportVO> pageParam = buildPage(dto.getPage(), dto.getSize());
        IPage<WorkOrderReportVO> pageResult =
                reportMapper.queryWorkOrderReport(pageParam, dto, resolveReportUserId());
        return PageResult.of(pageResult.getRecords(), pageResult.getTotal(),
                pageResult.getCurrent(), pageResult.getSize());
    }

    @Override
    public PageResult<InventoryReportVO> queryInventoryReport(InventoryReportDTO dto) {
        requireAllDataScope();
        Page<InventoryReportVO> pageParam = buildPage(dto.getPage(), dto.getSize());
        IPage<InventoryReportVO> pageResult =
                reportMapper.queryInventoryReport(pageParam, dto, resolveReportUserId());
        return PageResult.of(pageResult.getRecords(), pageResult.getTotal(),
                pageResult.getCurrent(), pageResult.getSize());
    }

    @Override
    public PageResult<QualityReportVO> queryQualityReport(QualityReportDTO dto) {
        Page<QualityReportVO> pageParam = buildPage(dto.getPage(), dto.getSize());
        IPage<QualityReportVO> pageResult =
                reportMapper.queryQualityReport(pageParam, dto, resolveReportUserId());
        return PageResult.of(pageResult.getRecords(), pageResult.getTotal(),
                pageResult.getCurrent(), pageResult.getSize());
    }

    @Override
    public PageResult<EquipmentRepairReportVO> queryEquipmentRepairReport(EquipmentRepairReportDTO dto) {
        Page<EquipmentRepairReportVO> pageParam = buildPage(dto.getPage(), dto.getSize());
        IPage<EquipmentRepairReportVO> pageResult =
                reportMapper.queryEquipmentRepairReport(pageParam, dto, resolveReportUserId());
        return PageResult.of(pageResult.getRecords(), pageResult.getTotal(),
                pageResult.getCurrent(), pageResult.getSize());
    }

    @Override
    public PageResult<AndonReportVO> queryAndonReport(AndonReportDTO dto) {
        Page<AndonReportVO> pageParam = buildPage(dto.getPage(), dto.getSize());
        IPage<AndonReportVO> pageResult =
                reportMapper.queryAndonReport(pageParam, dto, resolveReportUserId());
        return PageResult.of(pageResult.getRecords(), pageResult.getTotal(),
                pageResult.getCurrent(), pageResult.getSize());
    }

    // ========== 导出（VO -> ExcelVO 映射，全量导出，带数据权限过滤） ==========

    @Override
    public List<WorkOrderReportExcelVO> exportWorkOrderReport(WorkOrderReportDTO dto) {
        List<WorkOrderReportVO> list = reportMapper.listWorkOrderReport(dto, resolveReportUserId());
        return list.stream().map(vo -> {
            WorkOrderReportExcelVO evo = new WorkOrderReportExcelVO();
            evo.setWorkOrderId(vo.getWorkOrderId());
            evo.setWorkOrderNo(vo.getWorkOrderNo());
            evo.setOrderNo(vo.getOrderNo());
            evo.setItemCode(vo.getItemCode());
            evo.setItemName(vo.getItemName());
            evo.setPlanQty(vo.getPlanQty() != null ? vo.getPlanQty().toString() : "");
            evo.setUnit(vo.getUnit());
            evo.setStatus(vo.getStatus());
            evo.setPlanStartTime(vo.getPlanStartTime() != null ? vo.getPlanStartTime().toString() : "");
            evo.setPlanEndTime(vo.getPlanEndTime() != null ? vo.getPlanEndTime().toString() : "");
            evo.setCreateTime(vo.getCreateTime() != null ? vo.getCreateTime().toString() : "");
            return evo;
        }).toList();
    }

    @Override
    public List<InventoryReportExcelVO> exportInventoryReport(InventoryReportDTO dto) {
        requireAllDataScope();
        List<InventoryReportVO> list = reportMapper.listInventoryReport(dto, resolveReportUserId());
        return list.stream().map(vo -> {
            InventoryReportExcelVO evo = new InventoryReportExcelVO();
            evo.setInventoryId(vo.getInventoryId());
            evo.setItemCode(vo.getItemCode());
            evo.setItemName(vo.getItemName());
            evo.setLocationCode(vo.getLocationCode());
            evo.setLocationName(vo.getLocationName());
            evo.setQuantity(vo.getQuantity() != null ? vo.getQuantity().toString() : "");
            evo.setLockedQuantity(vo.getLockedQuantity() != null ? vo.getLockedQuantity().toString() : "");
            evo.setUnit(vo.getUnit());
            evo.setBatchNo(vo.getBatchNo());
            evo.setUpdateTime(vo.getUpdateTime() != null ? vo.getUpdateTime().toString() : "");
            return evo;
        }).toList();
    }

    @Override
    public List<QualityReportExcelVO> exportQualityReport(QualityReportDTO dto) {
        List<QualityReportVO> list = reportMapper.listQualityReport(dto, resolveReportUserId());
        return list.stream().map(vo -> {
            QualityReportExcelVO evo = new QualityReportExcelVO();
            evo.setInspectionId(vo.getInspectionId());
            evo.setInspectionNo(vo.getInspectionNo());
            evo.setWorkOrderNo(vo.getWorkOrderNo());
            evo.setItemCode(vo.getItemCode());
            evo.setItemName(vo.getItemName());
            evo.setResult(vo.getResult());
            evo.setInspector(vo.getInspector());
            evo.setInspectionTime(vo.getInspectionTime() != null ? vo.getInspectionTime().toString() : "");
            evo.setStatus(vo.getStatus());
            return evo;
        }).toList();
    }

    @Override
    public List<EquipmentRepairReportExcelVO> exportEquipmentRepairReport(EquipmentRepairReportDTO dto) {
        List<EquipmentRepairReportVO> list = reportMapper.listEquipmentRepairReport(dto, resolveReportUserId());
        return list.stream().map(vo -> {
            EquipmentRepairReportExcelVO evo = new EquipmentRepairReportExcelVO();
            evo.setRepairId(vo.getRepairId());
            evo.setEquipmentCode(vo.getEquipmentCode());
            evo.setEquipmentName(vo.getEquipmentName());
            evo.setFaultDesc(vo.getFaultDesc());
            evo.setReportUser(vo.getReportUser());
            evo.setRepairUser(vo.getRepairUser());
            evo.setStatus(vo.getStatus());
            evo.setReportTime(vo.getReportTime() != null ? vo.getReportTime().toString() : "");
            evo.setFinishTime(vo.getFinishTime() != null ? vo.getFinishTime().toString() : "");
            evo.setResultDesc(vo.getResultDesc());
            return evo;
        }).toList();
    }

    @Override
    public List<AndonReportExcelVO> exportAndonReport(AndonReportDTO dto) {
        List<AndonReportVO> list = reportMapper.listAndonReport(dto, resolveReportUserId());
        return list.stream().map(vo -> {
            AndonReportExcelVO evo = new AndonReportExcelVO();
            evo.setAlertId(vo.getAlertId());
            evo.setAlertNo(vo.getAlertNo());
            evo.setAlertType(vo.getAlertType());
            evo.setAlertLevel(vo.getAlertLevel());
            evo.setUnitName(vo.getUnitName());
            evo.setEquipmentId(vo.getEquipmentId());
            evo.setStatus(vo.getStatus());
            evo.setCreateTime(vo.getCreateTime() != null ? vo.getCreateTime().toString() : "");
            evo.setHandleTime(vo.getHandleTime() != null ? vo.getHandleTime().toString() : "");
            return evo;
        }).toList();
    }

    /**
     * 计算当前请求的数据权限过滤 userId：
     * ALL 数据范围返回 null（不过滤）；SELF 返回当前登录用户 ID。
     * dataScope 为空时按 SELF 处理，避免误放行全量数据。
     */
    private Long resolveReportUserId() {
        String dataScope = BaseContext.getCurrentDataScope();
        return "ALL".equals(dataScope) ? null : BaseContext.getCurrentId();
    }

    /**
     * 库存报表无归属字段，仅 ALL 数据范围可用。
     */
    private void requireAllDataScope() {
        if (!"ALL".equals(BaseContext.getCurrentDataScope())) {
            throw new BaseException("库存报表需全部数据权限");
        }
    }

    /**
     * 构造真实分页参数，非法值兜底为第 1 页、每页 20 条。
     */
    private <T> Page<T> buildPage(Integer page, Integer size) {
        long current = page == null || page < 1 ? 1 : page;
        long pageSize = size == null || size < 1 ? 20 : size;
        return new Page<>(current, pageSize);
    }
}
