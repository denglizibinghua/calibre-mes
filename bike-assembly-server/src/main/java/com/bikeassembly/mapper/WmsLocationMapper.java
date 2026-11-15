package com.bikeassembly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bikeassembly.entity.WmsLocation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WmsLocationMapper extends BaseMapper<WmsLocation> {

    int countReferences(@Param("locationId") Long locationId);
}
