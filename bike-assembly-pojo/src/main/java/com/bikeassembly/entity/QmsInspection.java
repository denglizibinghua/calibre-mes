package com.bikeassembly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
@TableName("qms_inspection")
public class QmsInspection implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 质检单ID */
    @TableId(value = "inspection_id", type = IdType.AUTO)
    private Long inspectionId;
    /** 质检单号 */
    @TableField("inspection_no")
    private String inspectionNo;
    /** 工单ID */
    @TableField("work_order_id")
    private Long workOrderId;
    /** 工序ID（基础工序） */
    @TableField("process_id")
    private Long processId;
    /** 工序执行记录ID（pro_work_process.process_record_id，精确关联） */
    @TableField("process_record_id")
    private Long processRecordId;
    /** 检验结果 */
    @TableField("result")
    private String result;
    /** 质检明细JSON */
    @TableField("details_json")
    private String detailsJson;
    /** 检验人 */
    @TableField("inspector")
    private String inspector;
    /** 检验人ID */
    @TableField("inspector_user_id")
    private Long inspectorUserId;
    /** 检验时间 */
    @TableField("inspection_time")
    private LocalDateTime inspectionTime;
    /** 状态 */
    @TableField("status")
    private String status;
    /** 质检轮次（从1开始，初检=1，第一次复检=2...） */
    @TableField("inspection_round")
    private Integer inspectionRound;
    /** 上一张质检单ID（复检时关联初检/上一次质检单） */
    @TableField("parent_inspection_id")
    private Long parentInspectionId;
    /** 确认人 */
    @TableField("confirmed_by")
    private String confirmedBy;
    /** 确认人ID */
    @TableField("confirmed_user_id")
    private Long confirmedUserId;
    /** 确认时间 */
    @TableField("confirm_time")
    private LocalDateTime confirmTime;
    /** 乐观锁版本号 */
    @Version
    @TableField("version")
    private Integer version;
    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;
}
