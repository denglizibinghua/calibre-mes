package com.bikeassembly.aspect;

import com.bikeassembly.annotation.OperLog;
import com.bikeassembly.context.BaseContext;
import com.bikeassembly.entity.SysOperLog;
import com.bikeassembly.entity.SysUser;
import com.bikeassembly.mapper.SysOperLogMapper;
import com.bikeassembly.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * [答辩入口] 操作日志 AOP 切面。
 * [核心业务] 拦截 @OperLog 注解的方法，自动记录操作日志到 sys_oper_log 表。
 *   成功时 status="0"，异常时 status="1" 并记录错误信息（截断至 250 字符）
 * [数据库操作] finally 块中写入 sys_oper_log，确保无论成功失败都记录
 * [权限控制] 操作人信息从 BaseContext.getCurrentId() 获取，无需登录时标记为 "SYSTEM"
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class OperLogAspect {

    private final SysOperLogMapper sysOperLogMapper;
    private final SysUserMapper sysUserMapper;
    private final HttpServletRequest request;

    @Around("execution(public * com.bikeassembly.controller.admin..*(..)) && " +
            "(@annotation(org.springframework.web.bind.annotation.PostMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PutMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.DeleteMapping))")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 跳过无需记录的控制器：登录认证、日志自身、dev 消息测试
        String uri = request.getRequestURI();
        String controllerName = joinPoint.getTarget().getClass().getSimpleName();
        if (skipLogging(controllerName, uri)) {
            return joinPoint.proceed();
        }

        SysOperLog logEntry = new SysOperLog();
        logEntry.setModule(resolveModule(controllerName, joinPoint));
        logEntry.setOperType(resolveOperType(uri, request.getMethod(), joinPoint));
        logEntry.setOperTime(LocalDateTime.now());

        // 操作人信息
        Long userId = BaseContext.getCurrentId();
        if (userId != null) {
            logEntry.setOperUserId(userId);
            SysUser user = sysUserMapper.selectById(userId);
            if (user != null) {
                String displayName = user.getNickname() != null && !user.getNickname().isBlank()
                        ? user.getNickname() : user.getUsername();
                logEntry.setOperUser(displayName);
            }
        } else {
            logEntry.setOperUser("SYSTEM");
        }

        // 请求信息
        try {
            logEntry.setOperIp(getClientIp(request));
            logEntry.setRequestMethod(request.getMethod());
        } catch (Exception ignored) {
            // request 不可用时跳过
        }

        try {
            Object result = joinPoint.proceed();
            logEntry.setStatus("0");
            return result;
        } catch (Throwable e) {
            logEntry.setStatus("1");
            String errMsg = e.getMessage();
            if (errMsg != null && errMsg.length() > 250) {
                errMsg = errMsg.substring(0, 250) + "...";
            }
            logEntry.setErrorMsg(errMsg);
            throw e;
        } finally {
            try {
                sysOperLogMapper.insert(logEntry);
            } catch (Exception e) {
                log.error("操作日志写入失败", e);
            }
        }
    }

    /** 控制器类名 → 中文模块名 */
    private static final Map<String, String> MODULE_MAP = Map.ofEntries(
            Map.entry("BaseBomController", "BOM管理"),
            Map.entry("BaseItemController", "物料管理"),
            Map.entry("BaseProcessController", "工序管理"),
            Map.entry("BaseRouteController", "工艺路线管理"),
            Map.entry("BaseWorkUnitController", "工作单元管理"),
            Map.entry("BaseEquipmentController", "设备台账"),
            Map.entry("ProWorkOrderController", "工单管理"),
            Map.entry("ProWorkProcessController", "工序管理"),
            Map.entry("QmsDefectController", "缺陷管理"),
            Map.entry("QmsInspectionItemController", "质检项管理"),
            Map.entry("QmsInspectionController", "质检单"),
            Map.entry("EqmMaintenanceController", "设备保养"),
            Map.entry("EqmRepairOrderController", "设备维修"),
            Map.entry("AndonAlertController", "安灯告警"),
            Map.entry("SafSafetyEventController", "安全事件"),
            Map.entry("SafTrainingController", "安全培训"),
            Map.entry("WmsLocationController", "库位管理"),
            Map.entry("WmsInventoryController", "库存台账"),
            Map.entry("WmsStockDocController", "出入库单"),
            Map.entry("SysConfigController", "系统配置"),
            Map.entry("SysMenuController", "菜单管理"),
            Map.entry("SysRoleController", "角色管理"),
            Map.entry("SysUserController", "用户管理"),
            Map.entry("SysPermissionController", "权限缓存"),
            Map.entry("ErpSyncLogController", "ERP同步日志")
    );

    private boolean skipLogging(String controllerName, String uri) {
        if ("SysOperLogController".equals(controllerName)) return true;
        if ("UserController".equals(controllerName)) return true;
        if ("MessageTestController".equals(controllerName)) return true;
        if (uri.contains("/admin/health")) return true;
        return false;
    }

    /** 优先使用 @OperLog 注解值；未标注时按控制器类名推导模块名 */
    private String resolveModule(String controllerName, ProceedingJoinPoint joinPoint) {
        OperLog ann = getOperLog(joinPoint);
        if (ann != null && !ann.module().isBlank()) {
            return ann.module();
        }
        return MODULE_MAP.getOrDefault(controllerName, controllerName.replace("Controller", ""));
    }

    /** 优先使用 @OperLog 注解值；未标注时按请求路径/方法推导操作类型 */
    private String resolveOperType(String uri, String method, ProceedingJoinPoint joinPoint) {
        OperLog ann = getOperLog(joinPoint);
        if (ann != null && !ann.operType().isBlank()) {
            return ann.operType();
        }
        if (uri.contains("/rework-start")) return "返工开始";
        if (uri.contains("/rework-complete")) return "返工完成";
        if (uri.contains("/reset-password")) return "重置密码";
        if (uri.contains("/change-password")) return "修改密码";
        if (uri.contains("/reassign")) return "改派";
        if (uri.contains("/unassign")) return "取消指派";
        if (uri.contains("/assign")) return "指派";
        if (uri.contains("/release")) return "下达";
        if (uri.contains("/pause")) return "暂停";
        if (uri.contains("/resume")) return "恢复";
        if (uri.contains("/close")) return "关闭";
        if (uri.contains("/cancel")) return "取消";
        if (uri.contains("/skip")) return "跳过";
        if (uri.contains("/unlock")) return "解锁";
        if (uri.contains("/result")) return "录入结果";
        if (uri.contains("/confirm")) return "确认";
        if (uri.contains("/accept")) return "受理";
        if (uri.contains("/complete")) return "完成";
        if (uri.contains("/menus")) return "授权菜单";
        if (uri.contains("/cache")) return "清缓存";
        if ("POST".equals(method)) return "新增";
        if ("PUT".equals(method)) return "修改";
        if ("DELETE".equals(method)) return "删除";
        return "操作";
    }

    private OperLog getOperLog(ProceedingJoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        return method.getAnnotation(OperLog.class);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
