package com.bikeassembly.vo;

import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class BaseBomVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** BOM ID */
    private Long bomId;
    /** 成品物料ID */
    private Long itemId;
    /** BOM编码 */
    private String bomCode;
    /** 版本 */
    private String version;
    /** 子件明细JSON */
    private String detailsJson;
    /** 状态 */
    private String status;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
