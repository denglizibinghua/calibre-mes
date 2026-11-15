package com.bikeassembly.exception;

/**
 * 幂等键缺失异常。
 * 当必须幂等的接口缺少 Idempotency-Key 请求头时抛出。
 * 前端应展示友好提示并引导用户刷新页面后重试。
 */
public class IdempotentKeyMissingException extends BaseException {

    public IdempotentKeyMissingException() {
        super(400, "缺少幂等键（Idempotency-Key），请刷新页面后重试");
    }

    public IdempotentKeyMissingException(String msg) {
        super(400, msg);
    }
}
