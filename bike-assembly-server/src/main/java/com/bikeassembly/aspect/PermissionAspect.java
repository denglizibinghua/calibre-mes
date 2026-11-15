package com.bikeassembly.aspect;

import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.context.BaseContext;
import com.bikeassembly.exception.ForbiddenException;
import com.bikeassembly.exception.UnauthorizedException;
import com.bikeassembly.service.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * [答辩入口] 权限校验切面 —— 后端 RBAC 权限控制的核心执行点。
 * [权限控制] 通过 @Around 拦截所有标注 @RequirePermission 的方法：
 *   1. 从 BaseContext（ThreadLocal）获取当前用户 ID
 *   2. 调用 PermissionService.hasPermission 判断用户是否有该权限码
 *   3. 管理员（role_key=admin）自动通过（PermissionService 返回通配符 *）
 *   4. 无权限时抛 ForbiddenException，由 GlobalExceptionHandler 转为 403 响应
 * [调用链] 前端请求 → JWT 拦截器（设置 BaseContext） → PermissionAspect → Controller 方法
 * [实现情况] 前端用 v-permission 指令隐藏按钮，但真正的安全控制在后端切面
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class PermissionAspect {

    private final PermissionService permissionService;

    @Around("@annotation(requirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint, RequirePermission requirePermission) throws Throwable {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            throw new UnauthorizedException("请先登录");
        }

        String requiredPerm = requirePermission.value();
        if (!permissionService.hasPermission(userId, requiredPerm)) {
            log.warn("权限不足：userId={}, required={}", userId, requiredPerm);
            throw new ForbiddenException(MessageConstant.PERMISSION_DENIED);
        }

        return joinPoint.proceed();
    }
}
