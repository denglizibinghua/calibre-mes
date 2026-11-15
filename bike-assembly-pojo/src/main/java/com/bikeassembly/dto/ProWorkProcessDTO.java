package com.bikeassembly.dto;

import com.bikeassembly.validation.OnCreate;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class ProWorkProcessDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 流转记录ID */
    private Long processRecordId;
    /** 工单ID */
    @NotNull(message = "工单ID不能为空", groups = OnCreate.class)
    @Positive(message = "工单ID必须大于0", groups = OnCreate.class)
    private Long workOrderId;
    /** 工序ID */
    @NotNull(message = "工序ID不能为空", groups = OnCreate.class)
    @Positive(message = "工序ID必须大于0", groups = OnCreate.class)
    private Long processId;
    /** 设备ID（选填，非空时校验存在性） */
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
    /** 质检结果 CHAR(1): 0=待检 1=合格 2=不合格 3=待复检 */
    @Size(max = 1, message = "质检结果长度不能超过1")
    private String qcResult;
    /** 状态 CHAR(1) */
    @Size(max = 1, message = "状态长度不能超过1")
    private String status;
    /** 乐观锁版本号 */
    private Integer version;
    /** 工序顺序号 */
    private Integer stepSeq;
    /** 返工次数 */
    private Integer reworkCount;
    /** 质检备注 */
    @Size(max = 500, message = "质检备注长度不能超过500")
    private String qcResultNote;
    /** 创建时间 */
    private LocalDateTime createTime;
}
