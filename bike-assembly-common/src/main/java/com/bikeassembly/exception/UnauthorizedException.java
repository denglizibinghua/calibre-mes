package com.bikeassembly.exception;

/**
 * 未登录 / token 失效异常，全局处理器返回 401。
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String msg) {
        super(msg);
    }
}
