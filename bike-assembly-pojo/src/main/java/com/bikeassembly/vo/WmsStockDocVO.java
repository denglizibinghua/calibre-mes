package com.bikeassembly.vo;

import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class WmsStockDocVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 单据ID */
    private Long docId;
    /** 单据编号 */
    private String docNo;
    /** 单据类型 */
    private String docType;
    /** 关联单号 */
    private String relatedOrderNo;
    /** 单据状态 */
    private String status;
    /** 明细JSON */
    private String detailsJson;
    /** 创建时间 */
    private LocalDateTime createTime;
}
