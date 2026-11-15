package com.bikeassembly.exception;

/**
 * 幂等键冲突异常。
 * 当同一个 Idempotency-Key 被用于不同的请求参数时抛出。
 * HTTP 409 Conflict — 前端应阻止用户继续操作并提示刷新。
 */
public class IdempotentKeyConflictException extends BaseException {

    public IdempotentKeyConflictException() {
        super(409, "当前幂等键已用于另一组请求参数，请刷新页面后重试");
    }

    public IdempotentKeyConflictException(String msg) {
        super(409, msg);
    }
}
