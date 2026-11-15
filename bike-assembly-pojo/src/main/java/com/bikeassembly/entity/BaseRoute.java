package com.bikeassembly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
@TableName("base_route")
public class BaseRoute implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 路线ID */
    @TableId(value = "route_id", type = IdType.AUTO)
    private Long routeId;
    /** 产品物料ID */
    @TableField("item_id")
    private Long itemId;
    /** 路线编码 */
    @TableField("route_code")
    private String routeCode;
    /** 版本 */
    @TableField("version")
    private String version;
    /** 工序步骤JSON */
    @TableField("steps_json")
    private String stepsJson;
    /** 状态 */
    @TableField("status")
    private String status;
    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;
    /** 更新时间 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
