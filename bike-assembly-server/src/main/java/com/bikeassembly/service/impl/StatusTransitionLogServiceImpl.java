package com.bikeassembly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bikeassembly.context.BaseContext;
import com.bikeassembly.entity.ProStatusTransitionLog;
import com.bikeassembly.mapper.ProStatusTransitionLogMapper;
import com.bikeassembly.service.StatusTransitionLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 状态流转日志服务实现。
 */
@Service
@Slf4j
public class StatusTransitionLogServiceImpl
        extends ServiceImpl<ProStatusTransitionLogMapper, ProStatusTransitionLog>
        implements StatusTransitionLogService {

    @Override
    public void logTransition(String entityType, Long entityId,
                              String fromStatus, String toStatus,
                              String action, String remark) {
        // 状态日志写入失败不应影响主业务事务，记录告警日志便于排查
        try {
            ProStatusTransitionLog logEntry = ProStatusTransitionLog.builder()
                    .entityType(entityType)
                    .entityId(entityId)
                    .fromStatus(fromStatus != null ? fromStatus : "N")
                    .toStatus(toStatus)
                    .action(action)
                    .operatorUserId(getCurrentUserId())
                    .operatorName(getCurrentUserName())
                    .remark(remark)
                    .transitionTime(LocalDateTime.now())
                    .build();
            save(logEntry);
            log.debug("状态流转日志：{}[{}] {} -> {} ({})", entityType, entityId, fromStatus, toStatus, action);
        } catch (Exception e) {
            log.error("状态流转日志写入失败（不影响主业务）：entityType={}, entityId={}, action={}, error={}",
                    entityType, entityId, action, e.getMessage(), e);
        }
    }

    @Override
    public List<ProStatusTransitionLog> getTransitionHistory(String entityType, Long entityId) {
        LambdaQueryWrapper<ProStatusTransitionLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProStatusTransitionLog::getEntityType, entityType)
               .eq(ProStatusTransitionLog::getEntityId, entityId)
               .orderByAsc(ProStatusTransitionLog::getTransitionTime);
        return list(wrapper);
    }

    private Long getCurrentUserId() {
        try {
            return BaseContext.getCurrentId();
        } catch (Exception e) {
            return null;
        }
    }

    private String getCurrentUserName() {
        // BaseContext 不直接存用户名，这里返回 userId，调用方可自行解析
        Long userId = getCurrentUserId();
        return userId != null ? String.valueOf(userId) : "SYSTEM";
    }
}
