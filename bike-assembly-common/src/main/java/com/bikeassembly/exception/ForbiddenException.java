package com.bikeassembly.exception;

/**
 * 无权限异常，全局处理器返回 403。
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String msg) {
        super(msg);
    }
}
