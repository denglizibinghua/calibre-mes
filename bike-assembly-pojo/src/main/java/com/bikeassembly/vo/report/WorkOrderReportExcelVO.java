package com.bikeassembly.vo.report;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.io.Serializable;

@Data
public class WorkOrderReportExcelVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @ExcelProperty("工单ID")
    @ColumnWidth(12)
    private Long workOrderId;

    @ExcelProperty("工单编号")
    @ColumnWidth(22)
    private String workOrderNo;

    @ExcelProperty("订单号")
    @ColumnWidth(22)
    private String orderNo;

    @ExcelProperty("物料编码")
    @ColumnWidth(18)
    private String itemCode;

    @ExcelProperty("物料名称")
    @ColumnWidth(18)
    private String itemName;

    @ExcelProperty("计划数量")
    @ColumnWidth(14)
    private String planQty;

    @ExcelProperty("单位")
    @ColumnWidth(10)
    private String unit;

    @ExcelProperty("状态")
    @ColumnWidth(12)
    private String status;

    @ExcelProperty("计划开始时间")
    @ColumnWidth(22)
    private String planStartTime;

    @ExcelProperty("计划结束时间")
    @ColumnWidth(22)
    private String planEndTime;

    @ExcelProperty("创建时间")
    @ColumnWidth(22)
    private String createTime;
}
