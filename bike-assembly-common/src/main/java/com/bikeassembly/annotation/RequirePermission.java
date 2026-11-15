package com.bikeassembly.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验注解。
 * 标注在 Controller 方法上，由 PermissionAspect 拦截校验。
 * 当前用户若无指定权限，抛出 ForbiddenException → HTTP 403。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /** 权限编码，如 "app:work-order:view" */
    String value();
}
