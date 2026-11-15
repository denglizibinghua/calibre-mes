package com.bikeassembly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 幂等记录实体。
 * [幂等保护] 唯一约束 (operator_id, business_type, action, idempotency_key)
 * 保证同一用户、同一业务类型、同一操作、同一幂等键只执行一次。
 */
@Data
@TableName("pro_idempotency_record")
public class ProIdempotencyRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 幂等键（客户端生成，建议 UUID） */
    @TableField("idempotency_key")
    private String idempotencyKey;

    /** 业务类型: WORK_ORDER / WORK_PROCESS / INSPECTION */
    @TableField("business_type")
    private String businessType;

    /** 业务实体 ID（成功后回填） */
    @TableField("business_id")
    private Long businessId;

    /** 操作动作: unified_report / start / release / qc_pass / rework_start 等 */
    @TableField("action")
    private String action;

    /** 请求参数 SHA-256 哈希 */
    @TableField("request_hash")
    private String requestHash;

    /** 处理状态: 0=处理中(PROCESSING) 1=成功(SUCCESS) 2=失败(FAILED) */
    @TableField("process_status")
    private String processStatus;

    /** 业务响应码 */
    @TableField("response_code")
    private Integer responseCode;

    /** 响应数据快照 (JSON) */
    @TableField("response_data")
    private String responseData;

    /** 操作人 ID（NOT NULL，唯一索引的一部分） */
    @TableField("operator_id")
    private Long operatorId;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 更新时间（ON UPDATE CURRENT_TIMESTAMP） */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /** 过期时间（默认 24 小时后） */
    @TableField("expire_time")
    private LocalDateTime expireTime;

    // ==================== 状态常量 ====================

    /** 处理中 */
    public static final String STATUS_PROCESSING = "0";
    /** 成功 */
    public static final String STATUS_SUCCESS = "1";
    /** 失败 */
    public static final String STATUS_FAILED = "2";

    /** 处理中超时阈值（分钟） */
    public static final int PROCESSING_TIMEOUT_MINUTES = 5;
}
