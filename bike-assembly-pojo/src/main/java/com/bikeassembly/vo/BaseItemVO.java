package com.bikeassembly.vo;

import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class BaseItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 物料ID */
    private Long itemId;
    /** 物料编码 */
    private String itemCode;
    /** 物料名称 */
    private String itemName;
    /** 物料类型 */
    private String itemType;
    /** 规格型号 */
    private String specModel;
    /** 单位 */
    private String unit;
    /** 状态 */
    private String status;
    /** 创建时间 */
    private LocalDateTime createTime;
}
