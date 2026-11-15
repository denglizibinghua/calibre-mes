package com.bikeassembly.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * AI 质检分析请求 DTO。
 */
@Data
public class AiDefectAnalysisDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 质检单ID */
    @NotNull(message = "质检单ID不能为空")
    private Long inspectionId;
}
