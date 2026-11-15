package com.bikeassembly.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 知识图谱关系。
 */
@Data
@Builder
public class GraphEdgeVO implements Serializable {
    private String fromId;
    private String fromName;
    private String fromType;
    private String toId;
    private String toName;
    private String toType;
    private String type;
    private String source;
    private String scope;
    private Long ownerId;
}
