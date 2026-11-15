package com.bikeassembly.dto;

import com.bikeassembly.validation.OnCreate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class BaseItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 物料ID */
    private Long itemId;
    /** 物料编码 */
    @NotBlank(message = "物料编码不能为空", groups = OnCreate.class)
    private String itemCode;
    /** 物料名称 */
    @NotBlank(message = "物料名称不能为空", groups = OnCreate.class)
    private String itemName;
    /** 物料类型(CHAR(1)) */
    @NotBlank(message = "物料类型不能为空", groups = OnCreate.class)
    @Size(max = 1, message = "物料类型长度不能超过1")
    private String itemType;
    /** 规格型号 */
    private String specModel;
    /** 单位 */
    @NotBlank(message = "单位不能为空", groups = OnCreate.class)
    private String unit;
    /** 状态(CHAR(1)) */
    @Size(max = 1, message = "状态长度不能超过1")
    private String status;
    /** 创建时间 */
    private LocalDateTime createTime;
}
