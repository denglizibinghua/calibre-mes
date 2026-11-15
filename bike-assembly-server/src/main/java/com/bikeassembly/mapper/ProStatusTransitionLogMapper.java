package com.bikeassembly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bikeassembly.entity.ProStatusTransitionLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 状态流转日志 Mapper。
 */
@Mapper
public interface ProStatusTransitionLogMapper extends BaseMapper<ProStatusTransitionLog> {
}
