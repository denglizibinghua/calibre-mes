package com.bikeassembly.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class AiStreamStatusVO implements Serializable {
    private String requestId;
    private String sessionId;
    private String status;
    private String content;
    private long sequence;
    private String dataScope;
    private String dataTimeRange;
    private String model;
    private boolean fallback;
    private java.util.List<String> sources;
    private java.util.List<KnowledgeGraphNodeVO> graphNodes;
    private java.util.List<KnowledgeGraphEdgeVO> graphEdges;
    private java.time.LocalDateTime startedAt;
    private java.time.LocalDateTime completedAt;
    private String message;
}
