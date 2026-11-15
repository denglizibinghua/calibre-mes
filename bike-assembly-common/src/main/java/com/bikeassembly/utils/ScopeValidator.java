package com.bikeassembly.utils;

import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.exception.BaseException;

import java.util.Set;

/**
 * Scope 参数安全校验工具。
 *
 * Controller 和 Service 层双保险校验任何非法 scope（未知值、空字符串、空格、大小写变体）均返回参数错误，不静默退化为全部查询。
 */
public final class ScopeValidator {

    private ScopeValidator() {}

    /** mine=当前用户, all=全部（工序/工单/维修/安灯通用） */
    public static final Set<String> MINE_OR_ALL = Set.of("mine", "all");

    /** mine=当前用户, pending=待处理, all=全部（质检查询专用） */
    public static final Set<String> MINE_PENDING_ALL = Set.of("mine", "all", "pending");

    /**
     * 校验 scope 参数合法性（Controller 和 Service 层通用）。
     * 严格匹配：不自动 trim、不容错大小写——scope= mine 、scope=ALL 均拒绝。
     *
     * @param scope         待校验的 scope 值
     * @param allowedScopes 该接口允许的合法 scope 值集合
     * @return 原始合法 scope 值
     * @throws BaseException scope 为 null / 空白 / 不在允许集合中时抛出参数错误
     */
    public static String requireValid(String scope, Set<String> allowedScopes) {
        if (scope == null || scope.isBlank()) {
            throw new BaseException(MessageConstant.SCOPE_INVALID);
        }
        // 不 trim，不容错：带空格的变体（如 " mine "）和大小写变体（如 "ALL"）一律拒绝
        if (!allowedScopes.contains(scope)) {
            throw new BaseException(MessageConstant.SCOPE_INVALID);
        }
        return scope;
    }
}
