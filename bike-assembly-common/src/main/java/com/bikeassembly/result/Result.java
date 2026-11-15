package com.bikeassembly.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 统一返回结果。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> implements Serializable {

    private Integer code;
    private String msg;
    private T data;
    private String path;
    private LocalDateTime timestamp;
    private String requestId;

    public static <T> Result<T> success() {
        Result<T> result = new Result<>();
        result.code = 1;
        result.msg = "success";
        result.timestamp = LocalDateTime.now();
        return result;
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.code = 1;
        result.msg = "success";
        result.data = data;
        result.timestamp = LocalDateTime.now();
        return result;
    }

    public static <T> Result<T> error(String msg) {
        Result<T> result = new Result<>();
        result.code = 0;
        result.msg = msg;
        result.timestamp = LocalDateTime.now();
        return result;
    }

    public static <T> Result<T> error(int code, String msg) {
        Result<T> result = new Result<>();
        result.code = code;
        result.msg = msg;
        result.timestamp = LocalDateTime.now();
        return result;
    }

    public Result<T> withPath(String path) {
        this.path = path;
        return this;
    }

    public Result<T> withRequestId(String requestId) {
        this.requestId = requestId;
        return this;
    }

    public Result<T> withRequestId() {
        this.requestId = UUID.randomUUID().toString().substring(0, 8);
        return this;
    }
}
