package com.bikeassembly.vo.report;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.io.Serializable;

@Data
public class InventoryReportExcelVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @ExcelProperty("库存ID")
    @ColumnWidth(12)
    private Long inventoryId;

    @ExcelProperty("物料编码")
    @ColumnWidth(18)
    private String itemCode;

    @ExcelProperty("物料名称")
    @ColumnWidth(18)
    private String itemName;

    @ExcelProperty("库位编码")
    @ColumnWidth(18)
    private String locationCode;

    @ExcelProperty("库位名称")
    @ColumnWidth(18)
    private String locationName;

    @ExcelProperty("库存数量")
    @ColumnWidth(14)
    private String quantity;

    @ExcelProperty("锁定数量")
    @ColumnWidth(14)
    private String lockedQuantity;

    @ExcelProperty("单位")
    @ColumnWidth(10)
    private String unit;

    @ExcelProperty("批次号")
    @ColumnWidth(18)
    private String batchNo;

    @ExcelProperty("更新时间")
    @ColumnWidth(22)
    private String updateTime;
}
