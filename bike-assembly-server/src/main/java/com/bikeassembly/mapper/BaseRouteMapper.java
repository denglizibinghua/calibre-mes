package com.bikeassembly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bikeassembly.entity.BaseRoute;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BaseRouteMapper extends BaseMapper<BaseRoute> {

    int countReferences(@Param("routeId") Long routeId);
}
