package com.bikeassembly.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bikeassembly.dto.WmsLocationDTO;
import com.bikeassembly.entity.WmsLocation;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.vo.WmsLocationVO;

public interface WmsLocationService extends IService<WmsLocation> {

    WmsLocationVO saveWmsLocation(WmsLocationDTO dto);

    WmsLocationVO updateWmsLocation(Long id, WmsLocationDTO dto);

    WmsLocationVO getWmsLocationById(Long id);

    PageResult<WmsLocationVO> listWmsLocations(Long page, Long size);

    void deleteWmsLocation(Long id);
}
