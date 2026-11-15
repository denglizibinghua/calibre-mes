package com.bikeassembly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.entity.ProIdempotencyRecord;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.exception.IdempotentKeyConflictException;
import com.bikeassembly.mapper.ProIdempotencyRecordMapper;
import com.bikeassembly.service.IdempotencyService;
import com.bikeassembly.service.IdempotentResult;
import com.bikeassembly.utils.IdempotencyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;

/** Reservation commits separately; the response snapshot commits with the business changes. */
@Service
@RequiredArgsConstructor
public class IdempotencyServiceImpl implements IdempotencyService {
    private static final int DEFAULT_EXPIRE_HOURS = 24;
    private final ProIdempotencyRecordMapper idempotencyRecordMapper;
    private final PlatformTransactionManager transactionManager;

    @Override
    @Deprecated
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public IdempotentResult tryAcquire(String key, String businessType, String action,
                                      String requestHash, Long operatorId) {
        return acquire(key, businessType, action, requestHash, operatorId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public IdempotentResult tryAcquire(String key, String businessType, String action,
                                      Object requestObj, Long operatorId) {
        return acquire(key, businessType, action, IdempotencyUtils.computeHash(requestObj), operatorId);
    }

    private IdempotentResult acquire(String key, String businessType, String action,
                                     String requestHash, Long operatorId) {
        String validatedKey = IdempotencyUtils.validateIdempotencyKey(key);
        // Existing requests do not INSERT, avoiding shared-lock upgrades on simultaneous retries.
        ProIdempotencyRecord existing = getExisting(validatedKey, businessType, action, operatorId);
        if (existing == null) {
            ProIdempotencyRecord record = new ProIdempotencyRecord();
            LocalDateTime now = LocalDateTime.now();
            record.setIdempotencyKey(validatedKey);
            record.setBusinessType(businessType);
            record.setAction(action);
            record.setRequestHash(requestHash);
            record.setProcessStatus(ProIdempotencyRecord.STATUS_PROCESSING);
            record.setOperatorId(operatorId);
            record.setCreateTime(now);
            record.setUpdatedAt(now);
            record.setExpireTime(now.plusHours(DEFAULT_EXPIRE_HOURS));
            int inserted = idempotencyRecordMapper.insertIfAbsent(record);
            // READ_COMMITTED sees a concurrent winner after INSERT IGNORE has waited for it.
            existing = getExisting(validatedKey, businessType, action, operatorId);
            if (existing == null) throw new BaseException("幂等记录异常，请稍后重试");
            if (inserted == 1) return IdempotentResult.acquired(existing.getId());
        }
        if (!requestHash.equals(existing.getRequestHash())) {
            throw new IdempotentKeyConflictException(MessageConstant.IDEMPOTENCY_KEY_CONFLICT);
        }
        String status = existing.getProcessStatus();
        if (ProIdempotencyRecord.STATUS_SUCCESS.equals(status)) {
            return IdempotentResult.duplicate(existing.getId(), existing.getResponseData());
        }
        if (ProIdempotencyRecord.STATUS_FAILED.equals(status)
                || (ProIdempotencyRecord.STATUS_PROCESSING.equals(status) && isProcessingTimedOut(existing))) {
            LocalDateTime cutoff = LocalDateTime.now().minusMinutes(ProIdempotencyRecord.PROCESSING_TIMEOUT_MINUTES);
            if (idempotencyRecordMapper.reacquire(existing.getId(), status, cutoff) == 1) {
                return IdempotentResult.acquired(existing.getId());
            }
            existing = getExisting(validatedKey, businessType, action, operatorId);
            if (existing != null && ProIdempotencyRecord.STATUS_SUCCESS.equals(existing.getProcessStatus())) {
                return IdempotentResult.duplicate(existing.getId(), existing.getResponseData());
            }
        }
        return IdempotentResult.processing(existing == null ? -1 : existing.getId(), MessageConstant.IDEMPOTENCY_PROCESSING);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void lockExecution(long recordId) {
        ProIdempotencyRecord record = idempotencyRecordMapper.selectByIdForUpdate(recordId);
        if (record == null || !ProIdempotencyRecord.STATUS_PROCESSING.equals(record.getProcessStatus())) {
            throw new BaseException("请求状态已变更，请重试获取执行结果");
        }
        // This lock prevents timeout takeover throughout the actual business transaction.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    persistFailure(recordId, 0, "business_error", record.getUpdatedAt());
                }
            }
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markSuccess(long recordId, Long businessId, String responseData) {
        if (recordId <= 0 || idempotencyRecordMapper.updateResult(recordId,
                ProIdempotencyRecord.STATUS_SUCCESS, 0, responseData, businessId) != 1) {
            throw new BaseException("无法保存请求结果，请稍后重试");
        }
    }

    @Override
    public void markFailure(long recordId, int responseCode, String responseData) {
        if (recordId <= 0) return;
        // The completion callback writes after rollback releases the execution row lock.
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            persistFailure(recordId, responseCode, responseData, null);
        }
    }

    private void persistFailure(long recordId, int responseCode, String responseData, LocalDateTime expectedUpdate) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.executeWithoutResult(ignored -> idempotencyRecordMapper.update(null,
                new LambdaUpdateWrapper<ProIdempotencyRecord>()
                        .eq(ProIdempotencyRecord::getId, recordId)
                        .eq(ProIdempotencyRecord::getProcessStatus, ProIdempotencyRecord.STATUS_PROCESSING)
                        .eq(expectedUpdate != null, ProIdempotencyRecord::getUpdatedAt, expectedUpdate)
                        .set(ProIdempotencyRecord::getProcessStatus, ProIdempotencyRecord.STATUS_FAILED)
                        .set(ProIdempotencyRecord::getResponseCode, responseCode)
                        .set(ProIdempotencyRecord::getResponseData, responseData)
                        .set(ProIdempotencyRecord::getUpdatedAt, LocalDateTime.now())));
    }

    @Override
    public String getExistingResponse(String key, String businessType, String action, Long operatorId) {
        ProIdempotencyRecord record = getExisting(key, businessType, action, operatorId);
        return record != null && ProIdempotencyRecord.STATUS_SUCCESS.equals(record.getProcessStatus())
                ? record.getResponseData() : null;
    }

    private ProIdempotencyRecord getExisting(String key, String businessType, String action, Long operatorId) {
        return idempotencyRecordMapper.selectOne(new LambdaQueryWrapper<ProIdempotencyRecord>()
                .eq(ProIdempotencyRecord::getOperatorId, operatorId)
                .eq(ProIdempotencyRecord::getBusinessType, businessType)
                .eq(ProIdempotencyRecord::getAction, action)
                .eq(ProIdempotencyRecord::getIdempotencyKey, key).last("LIMIT 1"));
    }

    private boolean isProcessingTimedOut(ProIdempotencyRecord record) {
        LocalDateTime updated = record.getUpdatedAt() != null ? record.getUpdatedAt() : record.getCreateTime();
        return updated == null || updated.plusMinutes(ProIdempotencyRecord.PROCESSING_TIMEOUT_MINUTES)
                .isBefore(LocalDateTime.now());
    }
}
