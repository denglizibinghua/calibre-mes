package com.bikeassembly.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解。
 * 标注在需要记录操作日志的 Controller 方法上，由 OperLogAspect 拦截记录。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperLog {

    /** 模块名称，如 "工单管理"、"角色管理" */
    String module() default "";

    /** 操作类型，如 "新增"、"修改"、"删除"、"处理"、"关闭" */
    String operType() default "";
}
