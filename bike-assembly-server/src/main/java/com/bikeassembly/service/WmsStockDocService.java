package com.bikeassembly.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bikeassembly.dto.WmsStockDocDTO;
import com.bikeassembly.entity.WmsStockDoc;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.vo.WmsStockDocVO;

public interface WmsStockDocService extends IService<WmsStockDoc> {

    WmsStockDocVO saveWmsStockDoc(WmsStockDocDTO dto);

    WmsStockDocVO updateWmsStockDoc(Long id, WmsStockDocDTO dto);

    WmsStockDocVO getWmsStockDocById(Long id);

    PageResult<WmsStockDocVO> listWmsStockDocs(Long page, Long size,
                                                String docNo, String docType,
                                                String status, String keyword);

    void deleteWmsStockDoc(Long id);

    /**
     * 执行库存单据：校验明细 → 库存增减（出库条件更新防负库存）→ 写流水 → 单据置为已执行。
     * 入库/出库/移库任一明细失败则整体回滚。
     */
    WmsStockDocVO executeStockDoc(Long id);
}
