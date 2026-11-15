package com.bikeassembly.dto;

import com.bikeassembly.validation.OnCreate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class QmsInspectionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 质检单ID */
    private Long inspectionId;
    /** 质检单号 */
    @NotBlank(message = "质检单号不能为空", groups = OnCreate.class)
    private String inspectionNo;
    /** 工单ID */
    @NotNull(message = "工单不能为空", groups = OnCreate.class)
    private Long workOrderId;
    /** 工序ID（基础工序） */
    private Long processId;
    /** 工序执行记录ID */
    private Long processRecordId;
    /** 检验结果 */
    @Size(max = 1, message = "检验结果长度不能超过1")
    private String result;
    /** 质检明细JSON */
    private String detailsJson;
    /** 检验人 */
    private String inspector;
    /** 检验人ID */
    private Long inspectorUserId;
    /** 检验时间 */
    private LocalDateTime inspectionTime;
    /** 状态 */
    @Size(max = 1, message = "状态长度不能超过1")
    private String status;
}
