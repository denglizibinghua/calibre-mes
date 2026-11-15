package com.bikeassembly.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bikeassembly.dto.WmsInventoryDTO;
import com.bikeassembly.entity.WmsInventory;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.vo.WmsInventoryVO;

import java.util.List;

public interface WmsInventoryService extends IService<WmsInventory> {

    WmsInventoryVO saveWmsInventory(WmsInventoryDTO dto);

    WmsInventoryVO updateWmsInventory(Long id, WmsInventoryDTO dto);

    WmsInventoryVO getWmsInventoryById(Long id);

    /**
     * 动态条件 + 分页查询库存（V2.2）。
     *
     * @param page 当前页码（从 1 开始）
     * @param size 每页条数
     */
    PageResult<WmsInventoryVO> listWmsInventories(String itemCode, String itemName, String warehouseName,
                                                   Long page, Long size);

    void deleteWmsInventory(Long id);
}
