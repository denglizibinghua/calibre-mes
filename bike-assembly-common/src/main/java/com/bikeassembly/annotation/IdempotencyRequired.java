package com.bikeassembly.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要幂等保护的 Controller 方法。
 * 有此注解的方法必须携带合法的 Idempotency-Key 请求头，
 * 否则直接返回 400 参数错误。
 *
 * [用法示例]
 * <pre>
 *   {@code @PutMapping("/{id}/report")}
 *   {@code @IdempotencyRequired}
 *   {@code public Result<ProWorkProcessVO> report(@PathVariable Long id, ...) { ... }}
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IdempotencyRequired {
}
