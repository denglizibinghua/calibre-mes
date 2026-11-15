package com.bikeassembly.vo.report;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.io.Serializable;

@Data
public class QualityReportExcelVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @ExcelProperty("质检单ID")
    @ColumnWidth(12)
    private Long inspectionId;

    @ExcelProperty("质检单号")
    @ColumnWidth(22)
    private String inspectionNo;

    @ExcelProperty("工单编号")
    @ColumnWidth(22)
    private String workOrderNo;

    @ExcelProperty("物料编码")
    @ColumnWidth(18)
    private String itemCode;

    @ExcelProperty("物料名称")
    @ColumnWidth(18)
    private String itemName;

    @ExcelProperty("检验结果")
    @ColumnWidth(12)
    private String result;

    @ExcelProperty("检验人")
    @ColumnWidth(14)
    private String inspector;

    @ExcelProperty("检验时间")
    @ColumnWidth(22)
    private String inspectionTime;

    @ExcelProperty("状态")
    @ColumnWidth(12)
    private String status;
}
