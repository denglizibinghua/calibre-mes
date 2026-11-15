package com.bikeassembly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bikeassembly.entity.BaseEquipment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BaseEquipmentMapper extends BaseMapper<BaseEquipment> {

    int countReferences(@Param("equipmentId") Long equipmentId);

    /** 在事务中锁定设备行（SELECT ... FOR UPDATE），防止并发竞态 */
    @Select("SELECT * FROM base_equipment WHERE equipment_id = #{equipmentId} FOR UPDATE")
    BaseEquipment selectByIdForUpdate(@Param("equipmentId") Long equipmentId);
}
