package com.bikeassembly.dto;

import com.bikeassembly.validation.OnCreate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class WmsLocationDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 库位ID */
    private Long locationId;
    /** 父级ID */
    private Long parentId;
    /** 位置编码 */
    @NotBlank(message = "位置编码不能为空", groups = OnCreate.class)
    private String locationCode;
    /** 位置名称 */
    @NotBlank(message = "位置名称不能为空", groups = OnCreate.class)
    private String locationName;
    /** 位置类型(CHAR(1)) */
    @NotBlank(message = "位置类型不能为空", groups = OnCreate.class)
    @Size(max = 1, message = "位置类型长度不能超过1")
    private String locationType;
    /** 状态(CHAR(1)) */
    @Size(max = 1, message = "状态长度不能超过1")
    private String status;
    /** 创建时间 */
    private LocalDateTime createTime;
}
