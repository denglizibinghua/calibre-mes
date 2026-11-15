package com.bikeassembly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bikeassembly.entity.EqmRepairOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EqmRepairOrderMapper extends BaseMapper<EqmRepairOrder> {

    IPage<EqmRepairOrder> selectPageByCondition(Page<EqmRepairOrder> page,
                                                  @Param("reporterUserId") Long reporterUserId,
                                                  @Param("handlerUserId") Long handlerUserId,
                                                  @Param("isPending") Boolean isPending);
}
