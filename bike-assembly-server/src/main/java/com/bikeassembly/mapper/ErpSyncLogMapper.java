package com.bikeassembly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bikeassembly.entity.ErpSyncLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ErpSyncLogMapper extends BaseMapper<ErpSyncLog> {

    /**
     * 按 (biz_type, biz_id, sync_direction) 幂等写入：存在则更新状态/请求/响应/时间。
     * 依赖唯一键 uk_erp_sync_log_biz。
     */
    @Insert("INSERT INTO erp_sync_log " +
            "(biz_type, biz_id, sync_direction, sync_status, request_data, response_data, sync_time) " +
            "VALUES (#{bizType}, #{bizId}, #{syncDirection}, #{syncStatus}, #{requestData}, #{responseData}, NOW()) " +
            "ON DUPLICATE KEY UPDATE " +
            "sync_status = VALUES(sync_status), " +
            "request_data = VALUES(request_data), " +
            "response_data = VALUES(response_data), " +
            "sync_time = NOW()")
    int upsertByBiz(@Param("bizType") String bizType,
                    @Param("bizId") String bizId,
                    @Param("syncDirection") String syncDirection,
                    @Param("syncStatus") String syncStatus,
                    @Param("requestData") String requestData,
                    @Param("responseData") String responseData);
}
