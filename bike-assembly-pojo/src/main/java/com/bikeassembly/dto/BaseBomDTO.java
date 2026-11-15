package com.bikeassembly.dto;

import com.bikeassembly.validation.OnCreate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class BaseBomDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** BOM ID */
    private Long bomId;
    /** 成品物料ID */
    @NotNull(message = "成品物料ID不能为空", groups = OnCreate.class)
    private Long itemId;
    /** BOM编码 */
    @NotBlank(message = "BOM编码不能为空", groups = OnCreate.class)
    private String bomCode;
    /** 版本（DB 有 DEFAULT 'V1.0'，创建时可省略） */
    private String version;
    /** 子件明细JSON */
    @NotBlank(message = "子件明细不能为空", groups = OnCreate.class)
    private String detailsJson;
    /** 状态(CHAR(1)) */
    @Size(max = 1, message = "状态长度不能超过1")
    private String status;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
