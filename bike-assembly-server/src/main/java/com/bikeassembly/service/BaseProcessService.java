package com.bikeassembly.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bikeassembly.dto.BaseProcessDTO;
import com.bikeassembly.entity.BaseProcess;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.vo.BaseProcessVO;

public interface BaseProcessService extends IService<BaseProcess> {

    BaseProcessVO saveProcess(BaseProcessDTO dto);

    BaseProcessVO updateProcess(Long id, BaseProcessDTO dto);

    BaseProcessVO getProcessById(Long id);

    PageResult<BaseProcessVO> listProcesses(Long page, Long size);

    void deleteProcess(Long id);
}
