package com.bikeassembly.service.erp;

import java.util.Map;

/**
 * ERP 同步客户端。
 * <p>
 * 出站同步（MES → ERP）：由 ERP 消息消费者调用，把业务快照同步到 ERP 系统。
 * 未配置 {@code erp.base-url} 时进入 MOCK 模式（本地/演示可跑通全链路）。
 */
public interface ErpSyncClient {

    /** 同步请求 */
    record ErpSyncRequest(String bizType, String bizId, Map<String, Object> payload) {
    }

    /** 同步结果 */
    record ErpSyncResult(boolean success, String responseData) {

        public static ErpSyncResult success(String responseData) {
            return new ErpSyncResult(true, responseData);
        }

        public static ErpSyncResult failure(String responseData) {
            return new ErpSyncResult(false, responseData);
        }
    }

    /**
     * 执行一次 ERP 同步。
     *
     * @return 同步结果（成功/失败 + 响应快照，失败时 responseData 为错误信息）
     */
    ErpSyncResult sync(ErpSyncRequest request);
}
