package com.bikeassembly.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** Persistent knowledge-base status exposed to the admin UI. */
@Data
@Builder
public class AiKnowledgeStatusVO {
    private boolean vectorStoreEnabled;
    private String vectorStoreStatus;
    private String knowledgePath;
    private int scannedFiles;
    private int indexedFiles;
    private int failedFiles;
    private int filesChanged;
    private int filesSkipped;
    private int filesDeleted;
    private int chunksIndexed;
    private int chunksTotal;
    private String lastTaskId;
    private String lastTaskStatus;
    private List<String> failures;
    private LocalDateTime indexedAt;
    private String message;
}
