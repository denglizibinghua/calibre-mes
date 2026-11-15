package com.bikeassembly.vo;

import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class BaseWorkUnitVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 生产单元ID */
    private Long unitId;
    /** 父级ID */
    private Long parentId;
    /** 单元编码 */
    private String unitCode;
    /** 单元名称 */
    private String unitName;
    /** 单元类型 */
    private String unitType;
    /** 负责人 */
    private String leader;
    /** 状态 */
    private String status;
    /** 创建时间 */
    private LocalDateTime createTime;
}
