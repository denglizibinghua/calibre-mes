package com.bikeassembly.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bikeassembly.entity.ProStatusTransitionLog;

import java.util.List;

/**
 * 状态流转日志服务接口。
 */
public interface StatusTransitionLogService extends IService<ProStatusTransitionLog> {

    /**
     * 记录一次状态流转。
     */
    void logTransition(String entityType, Long entityId,
                       String fromStatus, String toStatus,
                       String action, String remark);

    /**
     * 查询实体的流转历史。
     */
    List<ProStatusTransitionLog> getTransitionHistory(String entityType, Long entityId);
}
