package com.bikeassembly.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serializable;

/**
 * 管理端工序编辑专用 DTO。
 * 仅允许修改设备绑定（equipmentId），其他字段（工单、工序、状态、数量、操作员、质检结果、时间）均不可通过此接口修改。
 * version 用于乐观锁校验。
 */
@Data
public class ProWorkProcessUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 设备ID（选填，非空时校验设备存在性）。null 表示解除设备绑定。 */
    @Positive(message = "设备ID必须大于0")
    private Long equipmentId;

    /** 乐观锁版本号（必填），用于防止并发覆盖。 */
    @NotNull(message = "版本号不能为空")
    private Integer version;
}
