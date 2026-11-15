package com.bikeassembly.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bikeassembly.dto.BaseEquipmentDTO;
import com.bikeassembly.entity.BaseEquipment;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.vo.BaseEquipmentVO;

public interface BaseEquipmentService extends IService<BaseEquipment> {

    BaseEquipmentVO saveEquipment(BaseEquipmentDTO dto);

    BaseEquipmentVO updateEquipment(Long id, BaseEquipmentDTO dto);

    BaseEquipmentVO getEquipmentById(Long id);

    PageResult<BaseEquipmentVO> listEquipments(Long page, Long size, String equipmentCode, String equipmentName);

    void deleteEquipment(Long id);
}
