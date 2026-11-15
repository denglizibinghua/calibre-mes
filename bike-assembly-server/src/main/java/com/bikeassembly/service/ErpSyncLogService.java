package com.bikeassembly.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bikeassembly.dto.ErpSyncLogDTO;
import com.bikeassembly.entity.ErpSyncLog;
import com.bikeassembly.vo.ErpSyncLogVO;
import com.bikeassembly.result.PageResult;


public interface ErpSyncLogService extends IService<ErpSyncLog> {

    ErpSyncLogVO saveSyncLog(ErpSyncLogDTO dto);

    ErpSyncLogVO updateSyncLog(Long id, ErpSyncLogDTO dto);

    ErpSyncLogVO getSyncLogById(Long id);

    PageResult<ErpSyncLogVO> listSyncLogs(Long page, Long size);

    void deleteSyncLog(Long id);

    /**
     * 幂等记录一次 ERP 同步结果（同一业务仅保留一条记录）。
     */
    void recordSync(String bizType, String bizId, String syncDirection,
                    String syncStatus, String requestData, String responseData);

    /**
     * 重试失败的 ERP 同步。
     */
    ErpSyncLogVO retrySync(Long id);
}
