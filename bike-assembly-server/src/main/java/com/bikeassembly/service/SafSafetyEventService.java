package com.bikeassembly.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bikeassembly.dto.SafSafetyEventDTO;
import com.bikeassembly.entity.SafSafetyEvent;
import com.bikeassembly.vo.SafSafetyEventVO;
import com.bikeassembly.result.PageResult;


public interface SafSafetyEventService extends IService<SafSafetyEvent> {

    SafSafetyEventVO saveSafSafetyEvent(SafSafetyEventDTO dto);

    SafSafetyEventVO updateSafSafetyEvent(Long id, SafSafetyEventDTO dto);

    SafSafetyEventVO getSafSafetyEventById(Long id);

    PageResult<SafSafetyEventVO> listSafSafetyEvents(Long page, Long size);

    void deleteSafSafetyEvent(Long id);
}
