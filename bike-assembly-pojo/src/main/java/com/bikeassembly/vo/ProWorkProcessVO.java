package com.bikeassembly.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class ProWorkProcessVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 流转记录ID */
    private Long processRecordId;
    /** 工单ID */
    private Long workOrderId;
    /** 工序ID */
    private Long processId;
    /** 设备ID */
    private Long equipmentId;
    /** 操作人 */
    private String operator;
    /** 操作人ID */
    private Long operatorUserId;
    /** 开始时间 */
    private LocalDateTime startTime;
    /** 结束时间 */
    private LocalDateTime endTime;
    /** 报工数量 */
    private BigDecimal reportQty;
    /** 质检结果 */
    private String qcResult;
    /** 状态 */
    private String status;
    /** 乐观锁版本号 */
    private Integer version;
    /** 工序顺序号 */
    private Integer stepSeq;
    /** 返工次数 */
    private Integer reworkCount;
    /** 质检备注 */
    private String qcResultNote;
    /** 创建时间 */
    private LocalDateTime createTime;
}
