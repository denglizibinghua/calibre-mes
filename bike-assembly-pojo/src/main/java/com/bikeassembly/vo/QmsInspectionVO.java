package com.bikeassembly.vo;

import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class QmsInspectionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 质检单ID */
    private Long inspectionId;
    /** 质检单号 */
    private String inspectionNo;
    /** 工单ID */
    private Long workOrderId;
    /** 已授权质检任务关联的工单编号 */
    private String workOrderNo;
    /** 工序ID（基础工序） */
    private Long processId;
    /** 工序执行记录ID */
    private Long processRecordId;
    /** 检验结果 */
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
    private String status;
    /** 质检轮次（从1开始，初检=1，第一次复检=2...） */
    private Integer inspectionRound;
    /** 上一张质检单ID（复检时关联初检/上一次质检单） */
    private Long parentInspectionId;
    /** 确认人 */
    private String confirmedBy;
    /** 确认人ID */
    private Long confirmedUserId;
    /** 确认时间 */
    private LocalDateTime confirmTime;
    /** 创建时间 */
    private LocalDateTime createTime;
}
