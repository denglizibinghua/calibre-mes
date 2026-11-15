package com.bikeassembly.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

/**
 * 幂等执行结果。
 * 封装 tryAcquire 后的三种可能结果：获得执行权、重复请求、处理中。
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IdempotentResult {

    /** 幂等记录 ID */
    private final long recordId;

    /** 结果类型 */
    private final ResultType type;

    /** 已有响应数据（重复请求时返回） */
    private final String existingResponse;

    private enum ResultType {
        /** 获得业务执行权：应执行核心业务逻辑 */
        ACQUIRED,
        /** 重复请求：第一次已成功，直接返回第一次结果 */
        DUPLICATE,
        /** 处理中：另一请求正在执行，稍后重试 */
        PROCESSING
    }

    private IdempotentResult(long recordId, ResultType type, String existingResponse) {
        this.recordId = recordId;
        this.type = type;
        this.existingResponse = existingResponse;
    }

    // ==================== 工厂方法 ====================

    /** 获得业务执行权 */
    public static IdempotentResult acquired(long recordId) {
        return new IdempotentResult(recordId, ResultType.ACQUIRED, null);
    }

    /** 重复请求（首次已成功） */
    public static IdempotentResult duplicate(long recordId, String existingResponse) {
        return new IdempotentResult(recordId, ResultType.DUPLICATE, existingResponse);
    }

    /** 处理中 */
    public static IdempotentResult processing(long recordId, String message) {
        return new IdempotentResult(recordId, ResultType.PROCESSING, message);
    }

    // ==================== 判断方法 ====================

    /** 是否获得业务执行权（应执行核心业务逻辑） */
    public boolean isAcquired() {
        return type == ResultType.ACQUIRED;
    }

    /** 是否为重复请求（应直接返回首次结果） */
    public boolean isDuplicate() {
        return type == ResultType.DUPLICATE;
    }

    /** 是否处理中 */
    public boolean isProcessing() {
        return type == ResultType.PROCESSING;
    }
}
