package com.bikeassembly.vo;

import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class SysConfigVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 配置ID */
    private Long configId;
    /** 配置键 */
    private String configKey;
    /** 配置值 */
    private String configValue;
    /** 配置名称 */
    private String configName;
    /** 配置类型 */
    private String configType;
    /** 状态 */
    private String status;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
