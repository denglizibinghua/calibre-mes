package com.bikeassembly.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class KnowledgeGraphEdgeVO implements Serializable {
    private String source;
    private String target;
    private String type;
    private String documentSource;
}
