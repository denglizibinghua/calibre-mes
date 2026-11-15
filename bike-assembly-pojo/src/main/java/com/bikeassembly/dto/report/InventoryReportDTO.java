package com.bikeassembly.dto.report;

import lombok.Data;

import java.io.Serializable;

@Data
public class InventoryReportDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 当前页码，从 1 开始 */
    private Integer page = 1;

    /** 每页条数 */
    private Integer size = 20;

    private Long itemId;
    private Long locationId;
    private String itemType;
}
