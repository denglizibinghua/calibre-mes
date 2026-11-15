package com.bikeassembly.dto;

import com.bikeassembly.validation.OnCreate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class BaseRouteDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 路线ID */
    private Long routeId;
    /** 产品物料ID */
    @NotNull(message = "产品物料ID不能为空", groups = OnCreate.class)
    private Long itemId;
    /** 路线编码 */
    @NotBlank(message = "工艺路线编码不能为空", groups = OnCreate.class)
    private String routeCode;
    /** 版本（DB 有 DEFAULT 'V1.0'，创建时可省略） */
    private String version;
    /** 工序步骤JSON */
    @NotBlank(message = "工序步骤不能为空", groups = OnCreate.class)
    private String stepsJson;
    /** 状态(CHAR(1)) */
    @Size(max = 1, message = "状态长度不能超过1")
    private String status;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
