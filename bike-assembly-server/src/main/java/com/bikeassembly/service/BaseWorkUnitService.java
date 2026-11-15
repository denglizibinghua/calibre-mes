package com.bikeassembly.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bikeassembly.dto.BaseWorkUnitDTO;
import com.bikeassembly.entity.BaseWorkUnit;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.vo.BaseWorkUnitVO;

public interface BaseWorkUnitService extends IService<BaseWorkUnit> {

    BaseWorkUnitVO saveWorkUnit(BaseWorkUnitDTO dto);

    BaseWorkUnitVO updateWorkUnit(Long id, BaseWorkUnitDTO dto);

    BaseWorkUnitVO getWorkUnitById(Long id);

    PageResult<BaseWorkUnitVO> listWorkUnits(Long page, Long size);

    void deleteWorkUnit(Long id);
}
