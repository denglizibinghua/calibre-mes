package com.bikeassembly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bikeassembly.entity.BaseItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BaseItemMapper extends BaseMapper<BaseItem> {

    /**
     * 检查物料被其他业务表引用的次数（跨表 UNION ALL）。
     * 基础 CRUD 由 MyBatis-Plus BaseMapper 提供，此处只定义自定义查询。
     */
    int countReferences(@Param("itemId") Long itemId);
}
