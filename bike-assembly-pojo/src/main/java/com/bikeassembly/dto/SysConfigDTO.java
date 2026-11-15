package com.bikeassembly.dto;

import com.bikeassembly.validation.OnCreate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class SysConfigDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 配置ID */
    private Long configId;
    /** 配置键 */
    @NotBlank(message = "配置键不能为空", groups = OnCreate.class)
    private String configKey;
    /** 配置值 */
    private String configValue;
    /** 配置名称 */
    @NotBlank(message = "配置名称不能为空", groups = OnCreate.class)
    private String configName;
    /** 配置类型 */
    private String configType;
    /** 状态(CHAR(1)) */
    @Size(max = 1, message = "状态长度不能超过1")
    private String status;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
