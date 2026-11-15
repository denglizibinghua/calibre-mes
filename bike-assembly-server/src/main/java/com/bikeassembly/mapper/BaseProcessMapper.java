package com.bikeassembly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bikeassembly.entity.BaseProcess;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BaseProcessMapper extends BaseMapper<BaseProcess> {

    int countReferences(@Param("processId") Long processId);
}
