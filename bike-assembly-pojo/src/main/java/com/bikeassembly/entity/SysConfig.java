package com.bikeassembly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
@TableName("sys_config")
public class SysConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 配置ID */
    @TableId(value = "config_id", type = IdType.AUTO)
    private Long configId;
    /** 配置键 */
    @TableField("config_key")
    private String configKey;
    /** 配置值 */
    @TableField("config_value")
    private String configValue;
    /** 配置名称 */
    @TableField("config_name")
    private String configName;
    /** 配置类型 */
    @TableField("config_type")
    private String configType;
    /** 状态 */
    @TableField("status")
    private String status;
    /** 更新时间 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
