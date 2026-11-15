package com.bikeassembly.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AiKnowledgeTaskVO {
    private String taskId;
    private String status;
    private int scannedFiles;
    private int filesChanged;
    private int filesSkipped;
    private int filesDeleted;
    private int chunksIndexed;
    private int chunksTotal;
    private List<String> failures;
    private String message;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
