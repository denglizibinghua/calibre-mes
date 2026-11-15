package com.bikeassembly.exception;

import com.bikeassembly.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 * 所有异常统一返回 Result 结构，前端只读 msg。
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ==================== 业务异常 ====================

    @ExceptionHandler(BaseException.class)
    public Result<Void> handleBaseException(BaseException ex) {
        log.warn("业务异常：{}", ex.getMessage());
        return Result.error(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(StateMachineException.class)
    public Result<Void> handleStateMachineException(StateMachineException ex) {
        log.warn("状态机异常：{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }

    // ==================== 参数校验异常 ====================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + "：" + fe.getDefaultMessage())
                .collect(Collectors.joining("；"));
        log.warn("参数校验失败：{}", msg);
        return Result.error(msg);
    }

    @ExceptionHandler(org.springframework.validation.BindException.class)
    public Result<Void> handleBindException(org.springframework.validation.BindException ex) {
        String msg = ex.getFieldErrors().stream()
                .map(fe -> fe.getField() + "：" + fe.getDefaultMessage())
                .collect(Collectors.joining("；"));
        log.warn("参数绑定失败：{}", msg);
        return Result.error(msg);
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(jakarta.validation.ConstraintViolationException ex) {
        String msg = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + "：" + v.getMessage())
                .collect(Collectors.joining("；"));
        log.warn("约束校验失败：{}", msg);
        return Result.error(msg);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException ex) {
        log.warn("缺少必要参数：{}", ex.getParameterName());
        return Result.error("缺少必要参数：" + ex.getParameterName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("请求体格式错误：{}", ex.getMessage());
        return Result.error("请求体格式错误，请检查 JSON 格式");
    }

    // ==================== 幂等异常 ====================

    @ExceptionHandler(IdempotentKeyMissingException.class)
    public ResponseEntity<Result<Void>> handleIdempotentKeyMissing(IdempotentKeyMissingException ex) {
        log.warn("幂等键缺失：{}", ex.getMessage());
        Result<Void> result = Result.error(400, ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    @ExceptionHandler(IdempotentKeyConflictException.class)
    public ResponseEntity<Result<Void>> handleIdempotentKeyConflict(IdempotentKeyConflictException ex) {
        log.warn("幂等键冲突：{}", ex.getMessage());
        Result<Void> result = Result.error(409, ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
    }

    // ==================== 安全异常 ====================

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Result<Void>> handleUnauthorized(UnauthorizedException ex) {
        log.warn("未授权访问：{}", ex.getMessage());
        Result<Void> result = Result.error(401, ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Result<Void>> handleForbidden(ForbiddenException ex) {
        log.warn("权限不足：{}", ex.getMessage());
        Result<Void> result = Result.error(403, ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
    }

    // ==================== 数据库异常 ====================

    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException ex) {
        log.warn("唯一键冲突：{}", ex.getMessage());
        return Result.error("数据已存在，请检查唯一字段是否重复");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<Void> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String msg = "数据操作冲突，可能存在关联数据约束";

        // 尝试从 cause 中解析更具体的 SQL 错误信息
        try {
            Throwable cause = ex.getMostSpecificCause();
            if (cause != null && cause.getMessage() != null) {
                String causeMsg = cause.getMessage();
                if (causeMsg.contains("foreign key") || causeMsg.contains("FOREIGN KEY")
                        || causeMsg.contains(" CONSTRAINT ") && causeMsg.contains("FK_")) {
                    msg = "引用的关联数据不存在，请检查外键字段";
                } else if (causeMsg.contains("cannot be null") || causeMsg.contains("NOT NULL")) {
                    msg = "必填字段不能为空";
                } else if (causeMsg.contains("Data too long") || causeMsg.contains("data too long")) {
                    msg = "字段值过长，请检查输入长度";
                }
            }
        } catch (Exception ignored) {
            // 解析失败时使用默认消息，不吞异常也不抛新异常
        }

        log.warn("数据完整性冲突：{}", ex.getMessage());
        return Result.error(msg);
    }

    // ==================== 404 ====================

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFound(NoResourceFoundException ex) {
        return ResponseEntity.notFound().build();
    }

    // ==================== 兜底：未知异常 ====================

    /**
     * [答辩重点] 兜底异常处理 —— 所有未被上面具体处理器捕获的异常最终到这里。
     * [异常处理] 1. 生成 8 位 requestId（UUID 前 8 位）方便排查
     *   2. 后端日志打印完整堆栈（log.error），前端只展示友好提示 + requestId
     *   3. 不把 SQL、堆栈、服务器路径暴露给前端（安全要求）
     * [数据转换] 错误响应包含：code(500) + msg(含 requestId) + path + requestId + timestamp
     */
    @ExceptionHandler(Exception.class)
    public Result<Object> handleException(Exception ex, HttpServletRequest request) {
        String requestId = java.util.UUID.randomUUID().toString().substring(0, 8);
        log.error("系统异常 [requestId={}, path={}]：{}", requestId, request.getRequestURI(), ex.getMessage(), ex);
        Result<Object> result = Result.error(500, "系统繁忙，请稍后重试（" + requestId + "）");
        result.setPath(request.getRequestURI());
        result.setRequestId(requestId);
        return result;
    }
}
