package com.bikeassembly.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class QmsDefectVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 不合格品ID */
    private Long defectId;
    /** 质检单ID */
    private Long inspectionId;
    /** 工单ID */
    private Long workOrderId;
    /** 工序ID */
    private Long processId;
    /** 缺陷类型 */
    private String defectType;
    /** 数量 */
    private BigDecimal qty;
    /** 处理结果 */
    private String handleResult;
    /** 状态 */
    private String status;
    /** 创建时间 */
    private LocalDateTime createTime;
}
