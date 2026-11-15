package com.bikeassembly.vo;

import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class WmsLocationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 库位ID */
    private Long locationId;
    /** 父级ID */
    private Long parentId;
    /** 位置编码 */
    private String locationCode;
    /** 位置名称 */
    private String locationName;
    /** 位置类型 */
    private String locationType;
    /** 状态 */
    private String status;
    /** 创建时间 */
    private LocalDateTime createTime;
}
