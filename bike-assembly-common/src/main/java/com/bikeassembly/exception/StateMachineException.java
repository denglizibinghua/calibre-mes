package com.bikeassembly.exception;

/**
 * 状态机非法转换异常。
 * 由 GlobalExceptionHandler 统一处理，返回 code=0 的业务错误。
 */
public class StateMachineException extends RuntimeException {

    public StateMachineException(String message) {
        super(message);
    }

    public StateMachineException(String message, Throwable cause) {
        super(message, cause);
    }
}
