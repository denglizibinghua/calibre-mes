package com.bikeassembly.vo;

import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class BaseRouteVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 路线ID */
    private Long routeId;
    /** 产品物料ID */
    private Long itemId;
    /** 路线编码 */
    private String routeCode;
    /** 版本 */
    private String version;
    /** 工序步骤JSON */
    private String stepsJson;
    /** 状态 */
    private String status;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
