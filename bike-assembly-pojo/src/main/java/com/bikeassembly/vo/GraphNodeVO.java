package com.bikeassembly.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 知识图谱节点。
 */
@Data
@Builder
public class GraphNodeVO implements Serializable {
    private String id;
    private String name;
    private String type;
    private String scope;
    private Long ownerId;
    private long relationCount;
}
