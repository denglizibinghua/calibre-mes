package com.bikeassembly.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("ai_plan_suggestion")
public class AiPlanSuggestion implements Serializable {
    @TableId(value = "plan_id", type = IdType.AUTO)
    private Long planId;
    private String planUuid;
    private Long operatorId;
    private String question;
    private String summary;
    private String affectedOrdersJson;
    private String priorityOrderJson;
    private String constraintsJson;
    private String resourceConstraintsJson;
    private String materialConstraintsJson;
    private String riskLevel;
    private String riskBasis;
    private String dataTimeRange;
    private String sourcesJson;
    private String inputSummary;
    private String model;
    private String requestId;
    private String sessionId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
    private Long confirmedBy;
}
