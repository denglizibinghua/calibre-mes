package com.bikeassembly.exception;

/**
 * 业务异常，Service 层抛此异常，由 GlobalExceptionHandler 统一处理。
 */
public class BaseException extends RuntimeException {

    private int code;

    public BaseException() {
        this.code = 0;
    }

    public BaseException(String msg) {
        super(msg);
        this.code = 0;
    }

    public BaseException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
