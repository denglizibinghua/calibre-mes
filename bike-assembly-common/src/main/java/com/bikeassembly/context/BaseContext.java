package com.bikeassembly.context;

import java.util.List;

/**
 * [答辩重点] 请求上下文容器 —— 基于 ThreadLocal 实现。
 * 每个 HTTP 请求独占一个线程，ThreadLocal 在线程内全局可访问，
 * Controller/Service 中无需层层传递 userId，直接通过 BaseContext.getCurrentId() 获取。
 *
 * 由 JwtTokenInterceptor.preHandle 设置：
 *   - currentId：当前登录用户 ID
 *   - currentPermissions：用户权限码列表
 *   - currentDataScope：数据范围（ALL/DEPT/SELF）
 *   - currentClientType：客户端类型（admin/app）
 *   - currentTokenJti：当前 Access Token 的 JTI（退出登录时需要）
 *
 * 由 JwtTokenInterceptor.afterCompletion 清理，防止内存泄漏。
 */
public class BaseContext {

    private static final ThreadLocal<Long> threadLocal = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> permissionsLocal = new ThreadLocal<>();
    private static final ThreadLocal<String> dataScopeLocal = new ThreadLocal<>();
    private static final ThreadLocal<String> clientTypeLocal = new ThreadLocal<>();
    private static final ThreadLocal<String> tokenJtiLocal = new ThreadLocal<>();
    private static final ThreadLocal<String> idempotencyKeyLocal = new ThreadLocal<>();
    private static final ThreadLocal<Long> idempotencyRecordIdLocal = new ThreadLocal<>();

    // ========== 用户 ID ==========

    public static void setCurrentId(Long id) {
        threadLocal.set(id);
    }

    public static Long getCurrentId() {
        return threadLocal.get();
    }

    public static void removeCurrentId() {
        threadLocal.remove();
    }

    // ========== 权限列表 ==========

    public static void setCurrentPermissions(List<String> permissions) {
        permissionsLocal.set(permissions);
    }

    public static List<String> getCurrentPermissions() {
        return permissionsLocal.get();
    }

    public static void removeCurrentPermissions() {
        permissionsLocal.remove();
    }

    // ========== 数据范围 ==========

    public static void setCurrentDataScope(String dataScope) {
        dataScopeLocal.set(dataScope);
    }

    public static String getCurrentDataScope() {
        return dataScopeLocal.get();
    }

    public static void removeCurrentDataScope() {
        dataScopeLocal.remove();
    }

    // ========== 客户端类型 ==========

    public static void setCurrentClientType(String clientType) {
        clientTypeLocal.set(clientType);
    }

    public static String getCurrentClientType() {
        return clientTypeLocal.get();
    }

    public static void removeCurrentClientType() {
        clientTypeLocal.remove();
    }

    // ========== Token JTI ==========

    public static void setCurrentTokenJti(String tokenJti) {
        tokenJtiLocal.set(tokenJti);
    }

    public static String getCurrentTokenJti() {
        return tokenJtiLocal.get();
    }

    public static void removeCurrentTokenJti() {
        tokenJtiLocal.remove();
    }

    // ========== 幂等键 ==========

    public static void setIdempotencyKey(String key) {
        idempotencyKeyLocal.set(key);
    }

    public static String getIdempotencyKey() {
        return idempotencyKeyLocal.get();
    }

    public static void removeIdempotencyKey() {
        idempotencyKeyLocal.remove();
        idempotencyRecordIdLocal.remove();
    }

    /** Acquired record ID is server state, never appended to the client key. */
    public static Long getIdempotencyRecordId() {
        return idempotencyRecordIdLocal.get();
    }

    public static void setIdempotencyRecordId(Long recordId) {
        idempotencyRecordIdLocal.set(recordId);
    }

    // ========== 全部清理 ==========

    public static void removeAll() {
        removeCurrentId();
        removeCurrentPermissions();
        removeCurrentDataScope();
        removeCurrentClientType();
        removeCurrentTokenJti();
        removeIdempotencyKey();
    }
}
