package com.bikeassembly.constant;

public class MessageConstant {

    public static final String UNKNOWN_ERROR = "未知错误";
    public static final String PARAM_ERROR = "参数错误";
    public static final String SCOPE_INVALID = "scope参数非法，仅允许mine/all等合法值";
    public static final String DATA_NOT_FOUND = "数据不存在";
    public static final String SAVE_SUCCESS = "新增成功";
    public static final String UPDATE_SUCCESS = "修改成功";
    public static final String DELETE_SUCCESS = "删除成功";
    public static final String ACCOUNT_NOT_FOUND = "账号不存在";
    public static final String PASSWORD_ERROR = "密码错误";
    public static final String ACCOUNT_DISABLED = "账号已停用";
    public static final String TOKEN_INVALID = "token无效";
    public static final String PERMISSION_DENIED = "权限不足，请联系管理员";
    public static final String ACCOUNT_DISABLED_LOGOUT = "账号已被禁用，请重新登录";
    public static final String STATUS_TRANSITION_DENIED = "当前状态不允许此操作";
    public static final String DATA_CONCURRENT_MODIFIED = "数据已被其他用户修改，请刷新后重试";
    public static final String PREDECESSOR_NOT_COMPLETED = "前序工序尚未完成，当前工序不可开始";
    public static final String WORK_ORDER_NOT_ACTIVE = "工单状态不允许生产操作";
    public static final String REWORK_MUST_RE_INSPECT = "返工完成后必须重新质检";
    public static final String QC_REQUIRED = "该工序需要质检，请先完成报工送检";

    // ==================== 幂等保护 ====================
    public static final String IDEMPOTENCY_KEY_MISSING = "缺少幂等键（Idempotency-Key），请刷新页面后重试";
    public static final String IDEMPOTENCY_KEY_EMPTY = "幂等键不能为空";
    public static final String IDEMPOTENCY_KEY_TOO_LONG = "幂等键长度不能超过128个字符";
    public static final String IDEMPOTENCY_KEY_INVALID_CHARS = "幂等键包含非法字符";
    public static final String IDEMPOTENCY_KEY_CONFLICT = "当前幂等键已用于另一组请求参数，请刷新页面后重试";
    public static final String IDEMPOTENCY_PROCESSING = "请求处理中，请稍后查看结果";
    public static final String IDEMPOTENCY_DUPLICATE = "该请求已处理，请勿重复提交";
}
