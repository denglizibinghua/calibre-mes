package com.bikeassembly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
@TableName("wms_location")
public class WmsLocation implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 库位ID */
    @TableId(value = "location_id", type = IdType.AUTO)
    private Long locationId;
    /** 父级ID */
    @TableField("parent_id")
    private Long parentId;
    /** 位置编码 */
    @TableField("location_code")
    private String locationCode;
    /** 位置名称 */
    @TableField("location_name")
    private String locationName;
    /** 位置类型 */
    @TableField("location_type")
    private String locationType;
    /** 状态 */
    @TableField("status")
    private String status;
    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;
}
