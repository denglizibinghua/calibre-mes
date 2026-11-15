package com.bikeassembly.erp;

import com.bikeassembly.service.erp.ErpSyncClient;
import com.bikeassembly.service.erp.ErpSyncClientImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ERP 同步客户端单元测试。
 * <p>
 * 未配置 ERP_BASE_URL 时进入 MOCK 模式，返回模拟成功，保证本地/演示可跑通全链路。
 */
@DisplayName("ERP 同步客户端单元测试")
class ErpSyncClientTest {

    @Test
    @DisplayName("未配置 ERP_BASE_URL 时返回 MOCK 成功")
    void mockMode_whenBaseUrlBlank() {
        ErpSyncClientImpl client = new ErpSyncClientImpl();
        ReflectionTestUtils.setField(client, "baseUrl", "");

        ErpSyncClient.ErpSyncResult result = client.sync(
                new ErpSyncClient.ErpSyncRequest("WORK_ORDER", "1",
                        Map.of("workOrderId", 1L, "status", "1")));

        assertTrue(result.success(), "MOCK 模式应返回成功");
        assertTrue(result.responseData().contains("MOCK"), "响应应标识 MOCK");
    }
}
