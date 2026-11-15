package com.bikeassembly.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 知识图谱概览统计。
 */
@Data
@Builder
public class GraphOverviewVO implements Serializable {
    private long nodeCount;
    private long edgeCount;
    private List<TypeCountVO> nodeTypes;
    private List<TypeCountVO> relationTypes;
    private long publicCount;
    private long privateCount;
}
