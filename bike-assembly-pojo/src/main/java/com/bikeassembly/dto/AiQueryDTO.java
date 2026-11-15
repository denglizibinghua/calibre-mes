package com.bikeassembly.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class AiQueryDTO implements Serializable {
    @NotBlank(message = "问题不能为空")
    @Size(max = 2000, message = "问题不能超过2000个字符")
    private String question;
    private Integer days = 7;
    @Size(max = 64, message = "会话 ID 不能超过64个字符")
    private String sessionId;
    private RetrievalMode retrievalMode = RetrievalMode.RAG;

    public enum RetrievalMode {
        RAG,
        GRAPHRAG,
        FUSED
    }
}
