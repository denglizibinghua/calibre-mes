package com.bikeassembly.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bikeassembly.dto.QmsDefectDTO;
import com.bikeassembly.entity.QmsDefect;
import com.bikeassembly.vo.QmsDefectVO;
import com.bikeassembly.result.PageResult;


public interface QmsDefectService extends IService<QmsDefect> {

    QmsDefectVO saveQmsDefect(QmsDefectDTO dto);

    QmsDefectVO updateQmsDefect(Long id, QmsDefectDTO dto);

    QmsDefectVO getQmsDefectById(Long id);

    PageResult<QmsDefectVO> listQmsDefects(Long page, Long size);

    void deleteQmsDefect(Long id);
}
