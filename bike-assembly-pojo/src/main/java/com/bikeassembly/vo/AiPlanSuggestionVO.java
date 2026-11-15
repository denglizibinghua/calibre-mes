package com.bikeassembly.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AiPlanSuggestionVO implements Serializable {
    private String planId;
    private String status;
    private String summary;
    private List<String> affectedOrders;
    private List<String> priorityOrder;
    private List<String> constraints;
    private List<String> resourceConstraints;
    private List<String> materialConstraints;
    private String riskLevel;
    private String riskBasis;
    private String dataTimeRange;
    private List<String> sources;
    private String inputSummary;
    private String model;
    private String requestId;
    private String sessionId;
    private LocalDateTime createdAt;
}
