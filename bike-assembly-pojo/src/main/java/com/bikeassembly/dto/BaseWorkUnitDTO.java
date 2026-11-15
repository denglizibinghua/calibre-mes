package com.bikeassembly.dto;

import com.bikeassembly.validation.OnCreate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class BaseWorkUnitDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 生产单元ID */
    private Long unitId;
    /** 父级ID */
    private Long parentId;
    /** 单元编码 */
    @NotBlank(message = "单元编码不能为空", groups = OnCreate.class)
    private String unitCode;
    /** 单元名称 */
    @NotBlank(message = "单元名称不能为空", groups = OnCreate.class)
    private String unitName;
    /** 单元类型(CHAR(1)) */
    @NotBlank(message = "单元类型不能为空", groups = OnCreate.class)
    @Size(max = 1, message = "单元类型长度不能超过1")
    private String unitType;
    /** 负责人 */
    private String leader;
    /** 状态(CHAR(1)) */
    @Size(max = 1, message = "状态长度不能超过1")
    private String status;
    /** 创建时间 */
    private LocalDateTime createTime;
}
