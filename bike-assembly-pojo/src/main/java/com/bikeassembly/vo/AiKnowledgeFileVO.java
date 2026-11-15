package com.bikeassembly.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
public class AiKnowledgeFileVO implements Serializable {
    private Long documentId;
    private String name;
    private String scope;
    private String mimeType;
    private Long size;
    private Integer chunkCount;
    private String indexStatus;
    private LocalDateTime indexedAt;
}
