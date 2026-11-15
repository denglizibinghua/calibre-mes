package com.bikeassembly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bikeassembly.entity.AndonAlert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AndonAlertMapper extends BaseMapper<AndonAlert> {

    IPage<AndonAlert> selectPageByCondition(Page<AndonAlert> page,
                                             @Param("reporterUserId") Long reporterUserId,
                                             @Param("handlerUserId") Long handlerUserId,
                                             @Param("isPending") Boolean isPending);
}
