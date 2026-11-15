package com.bikeassembly.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bikeassembly.dto.BaseBomDTO;
import com.bikeassembly.entity.BaseBom;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.vo.BaseBomVO;

public interface BaseBomService extends IService<BaseBom> {

    BaseBomVO saveBom(BaseBomDTO dto);

    BaseBomVO updateBom(Long id, BaseBomDTO dto);

    BaseBomVO getBomById(Long id);

    PageResult<BaseBomVO> listBoms(Long page, Long size);

    void deleteBom(Long id);
}
