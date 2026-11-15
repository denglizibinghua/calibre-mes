package com.bikeassembly.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * AI 质检分析结果 VO。
 * <p>
 * available=false 表示 AI 未配置或调用失败（message 说明原因），前端展示降级提示。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalysisVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** AI 是否可用（未配置 Key / 调用失败时为 false） */
    private boolean available;
    /** 使用的模型 */
    private String model;
    /** 分析摘要 */
    private String summary;
    /** 可能原因 */
    private List<String> possibleCauses;
    /** 建议措施 */
    private List<String> suggestions;
    /** 风险等级：高 / 中 / 低 */
    private String riskLevel;
    /** 不可用原因（available=false 时展示） */
    private String message;
}
