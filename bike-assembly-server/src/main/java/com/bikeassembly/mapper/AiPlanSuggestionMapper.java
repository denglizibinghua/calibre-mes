package com.bikeassembly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bikeassembly.entity.AiPlanSuggestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiPlanSuggestionMapper extends BaseMapper<AiPlanSuggestion> {
    AiPlanSuggestion selectByUuidAndOperator(@Param("planUuid") String planUuid,
                                               @Param("operatorId") Long operatorId);
}
