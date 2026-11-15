package com.bikeassembly.vo.report;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.io.Serializable;

@Data
public class AndonReportExcelVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @ExcelProperty("告警ID")
    @ColumnWidth(12)
    private Long alertId;

    @ExcelProperty("告警编号")
    @ColumnWidth(22)
    private String alertNo;

    @ExcelProperty("告警类型")
    @ColumnWidth(14)
    private String alertType;

    @ExcelProperty("告警等级")
    @ColumnWidth(12)
    private String alertLevel;

    @ExcelProperty("生产单元")
    @ColumnWidth(18)
    private String unitName;

    @ExcelProperty("设备ID")
    @ColumnWidth(12)
    private Long equipmentId;

    @ExcelProperty("状态")
    @ColumnWidth(12)
    private String status;

    @ExcelProperty("告警时间")
    @ColumnWidth(22)
    private String createTime;

    @ExcelProperty("处理时间")
    @ColumnWidth(22)
    private String handleTime;
}
