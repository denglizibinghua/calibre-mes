package com.bikeassembly.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bikeassembly.dto.SafTrainingDTO;
import com.bikeassembly.entity.SafTraining;
import com.bikeassembly.vo.SafTrainingVO;
import com.bikeassembly.result.PageResult;


public interface SafTrainingService extends IService<SafTraining> {

    SafTrainingVO saveSafTraining(SafTrainingDTO dto);

    SafTrainingVO updateSafTraining(Long id, SafTrainingDTO dto);

    SafTrainingVO getSafTrainingById(Long id);

    PageResult<SafTrainingVO> listSafTrainings(Long page, Long size);

    void deleteSafTraining(Long id);
}
