package com.bikeassembly.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bikeassembly.dto.EqmMaintenanceDTO;
import com.bikeassembly.entity.EqmMaintenance;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.vo.EqmMaintenanceVO;

public interface EqmMaintenanceService extends IService<EqmMaintenance> {

    EqmMaintenanceVO saveEqmMaintenance(EqmMaintenanceDTO dto);

    EqmMaintenanceVO updateEqmMaintenance(Long id, EqmMaintenanceDTO dto);

    EqmMaintenanceVO getEqmMaintenanceById(Long id);

    PageResult<EqmMaintenanceVO> listEqmMaintenances(Long page, Long size);

    void deleteEqmMaintenance(Long id);
}
