package com.bikeassembly.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 知识图谱类型计数。
 */
@Data
@Builder
public class TypeCountVO implements Serializable {
    private String type;
    private long count;
}
