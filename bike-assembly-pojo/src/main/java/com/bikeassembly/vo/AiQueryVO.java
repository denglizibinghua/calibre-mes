package com.bikeassembly.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
public class AiQueryVO implements Serializable {
    private String requestId;
    private boolean available;
    private String answer;
    private String model;
    private List<String> sources;
    private List<String> citations;
    private String dataScope;
    private String dataTimeRange;
    private boolean fallback;
    private String message;
    private List<KnowledgeGraphNodeVO> graphNodes;
    private List<KnowledgeGraphEdgeVO> graphEdges;
}
