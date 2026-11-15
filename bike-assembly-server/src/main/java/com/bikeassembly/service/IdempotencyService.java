package com.bikeassembly.service;

/**
 * [答辩重点] 请求幂等服务接口。
 * 基于独立幂等记录表 pro_idempotency_record 实现业务请求幂等保护。
 *
 * [核心能力]
 *   1. tryAcquire：原子占位 + 请求摘要校验 + 状态判断，返回是否应执行业务
 *   2. markSuccess：业务成功后标记 SUCCESS 并保存响应结果
 *   3. markFailure：业务失败后标记 FAILED，允许同 Key 重试
 *   4. getExistingResponse：查询已有成功记录的响应数据
 *
 * [并发安全] 依赖数据库唯一索引 uk_idm_user_op_key 保证原子性
 */
public interface IdempotencyService {

    /**
     * 尝试获取幂等执行权。
     *
     * [行为]
     *   1. 校验 Idempotency-Key 合法性
     *   2. 计算请求参数 SHA-256 摘要
     *   3. 原子占位（INSERT IGNORE）
     *   4. 占位成功 → 返回 ACQUIRED
     *   5. 占位失败 → 根据已有记录状态判断：
     *      - 相同摘要 + SUCCESS → 返回 DUPLICATE + 首次响应
     *      - 相同摘要 + PROCESSING(未超时) → 返回 PROCESSING
     *      - 相同摘要 + PROCESSING(已超时) → 接管返回 ACQUIRED
     *      - 相同摘要 + FAILED → 允许重试返回 ACQUIRED
     *      - 不同摘要 → 抛出 IdempotentKeyConflictException
     *
     * @param idempotencyKey 客户端传入的幂等键（请求头 Idempotency-Key）
     * @param businessType   业务类型（WORK_ORDER / WORK_PROCESS / INSPECTION）
     * @param action         操作动作（unified_report / start / release / qc_pass 等）
     * @param requestObj     请求参数对象（用于计算 SHA-256 摘要）
     * @param operatorId     当前用户 ID
     * @return 幂等结果
     */
    IdempotentResult tryAcquire(String idempotencyKey, String businessType,
                                String action, Object requestObj, Long operatorId);

    /**
     * 标记幂等记录为成功并保存业务结果。
     * 与业务事务一起提交，业务回滚时成功快照也回滚。
     */
    void markSuccess(long recordId, Long businessId, String responseData);

    /** Lock the acquired record until business commit; rollback permits a retry. */
    void lockExecution(long recordId);

    /**
     * 标记幂等记录为失败。
     * 活动业务事务由 lockExecution 注册的回滚回调处理；无业务事务时独立保存。
     */
    void markFailure(long recordId, int responseCode, String responseData);

    /**
     * 获取已存在的成功幂等记录的响应数据。
     */
    String getExistingResponse(String idempotencyKey, String businessType,
                               String action, Long operatorId);

    // ==================== 业务类型常量 ====================

    String BIZ_TYPE_WORK_ORDER = "WORK_ORDER";
    String BIZ_TYPE_WORK_PROCESS = "WORK_PROCESS";
    String BIZ_TYPE_INSPECTION = "INSPECTION";
    String BIZ_TYPE_AI_PLAN = "AI_PLAN";

    /**
     * [兼容旧接口] 使用原始请求摘要字符串进行幂等检查。
     * 仅用于已有测试代码的兼容，新代码应使用 tryAcquire(String, String, String, Object, Long)。
     *
     * @return IdempotentResult
     */
    @Deprecated
    IdempotentResult tryAcquire(String idempotencyKey, String businessType,
                                String action, String requestHash, Long operatorId);

    // ==================== 操作动作常量 ====================

    // 工序操作
    String ACTION_UNIFIED_REPORT = "unified_report";
    String ACTION_START = "start";
    String ACTION_COMPLETE = "complete";
    String ACTION_REPORT_COMPLETE = "report_complete";
    String ACTION_REPORT_WITH_QC = "report_with_qc";
    String ACTION_QC_PASS = "qc_pass";
    String ACTION_QC_FAIL = "qc_fail";
    String ACTION_REWORK_START = "rework_start";
    String ACTION_REWORK_COMPLETE = "rework_complete";
    String ACTION_SKIP = "skip";
    String ACTION_CANCEL = "cancel";

    // 工单操作
    String ACTION_RELEASE = "release";
    String ACTION_PAUSE = "pause";
    String ACTION_RESUME = "resume";
    String ACTION_CLOSE = "close";
    String ACTION_ASSIGN = "assign";
    String ACTION_REASSIGN = "reassign";
    String ACTION_UNASSIGN = "unassign";

    // 质检操作
    String ACTION_CONFIRM_INSPECTION = "confirm_inspection";
    String ACTION_ENTER_RESULT = "enter_result";
    String ACTION_AI_PLAN_CONFIRM = "ai_plan_confirm";
}
