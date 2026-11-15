package com.bikeassembly.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bikeassembly.dto.CompleteRepairDTO;
import com.bikeassembly.dto.EqmRepairOrderDTO;
import com.bikeassembly.entity.EqmRepairOrder;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.vo.EqmRepairOrderVO;

public interface EqmRepairOrderService extends IService<EqmRepairOrder> {

    EqmRepairOrderVO saveEqmRepairOrder(EqmRepairOrderDTO dto);

    EqmRepairOrderVO updateEqmRepairOrder(Long id, EqmRepairOrderDTO dto);

    EqmRepairOrderVO getEqmRepairOrderById(Long id);

    PageResult<EqmRepairOrderVO> listEqmRepairOrders(Long page, Long size, String scope, String relation);

    void deleteEqmRepairOrder(Long id);

    /** 受理维修：将待受理状态变为维修中 */
    EqmRepairOrderVO acceptEqmRepairOrder(Long id);

    /** 完成维修：将维修中状态变为已完成 */
    EqmRepairOrderVO completeEqmRepairOrder(Long id);

    /** 完成维修（携带维修结果DTO）：写入resultDesc并联动设备状态 */
    EqmRepairOrderVO completeEqmRepairOrderWithDto(Long id, CompleteRepairDTO dto);
}
