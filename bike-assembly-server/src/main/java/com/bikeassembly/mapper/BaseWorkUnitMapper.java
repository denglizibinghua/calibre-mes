package com.bikeassembly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bikeassembly.entity.BaseWorkUnit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BaseWorkUnitMapper extends BaseMapper<BaseWorkUnit> {

    int countReferences(@Param("unitId") Long unitId);

    List<Long> selectDescendantUnitIds(@Param("rootUnitId") Long rootUnitId);
}
