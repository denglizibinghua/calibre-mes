package com.bikeassembly.vo.report;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.io.Serializable;

@Data
public class EquipmentRepairReportExcelVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @ExcelProperty("维修单ID")
    @ColumnWidth(12)
    private Long repairId;

    @ExcelProperty("设备编码")
    @ColumnWidth(18)
    private String equipmentCode;

    @ExcelProperty("设备名称")
    @ColumnWidth(18)
    private String equipmentName;

    @ExcelProperty("故障描述")
    @ColumnWidth(30)
    private String faultDesc;

    @ExcelProperty("报修人")
    @ColumnWidth(14)
    private String reportUser;

    @ExcelProperty("维修人")
    @ColumnWidth(14)
    private String repairUser;

    @ExcelProperty("状态")
    @ColumnWidth(12)
    private String status;

    @ExcelProperty("报修时间")
    @ColumnWidth(22)
    private String reportTime;

    @ExcelProperty("完成时间")
    @ColumnWidth(22)
    private String finishTime;

    @ExcelProperty("维修结果")
    @ColumnWidth(30)
    private String resultDesc;
}
