package com.bikeassembly.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class KnowledgeGraphNodeVO implements Serializable {
    private String id;
    private String name;
    private String type;
    private String source;
}
