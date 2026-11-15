package com.bikeassembly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("ai_request_audit")
public class AiRequestAudit implements Serializable {
    @TableId(value = "audit_id", type = IdType.AUTO)
    private Long auditId;
    private String requestId;
    private String sessionId;
    private Long userId;
    private String operation;
    private String questionHash;
    private String dataScope;
    private String dataTimeRange;
    private String model;
    private String sourcesJson;
    private String inputSummary;
    private String status;
    private Boolean fallback;
    private Long latencyMs;
    private String errorCode;
    private LocalDateTime createdAt;
}
