package com.bikeassembly.service.erp;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * ERP 同步客户端实现。
 * <p>
 * 配置 {@code ERP_BASE_URL} 后走真实 HTTP 同步（POST {base-url}/api/sync/{bizType}）；
 * 未配置时返回 MOCK 成功，便于本地开发与演示全链路。
 */
@Service
@Slf4j
public class ErpSyncClientImpl implements ErpSyncClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Value("${erp.base-url:}")
    private String baseUrl;

    private final RestClient restClient;

    public ErpSyncClientImpl() {
        this.restClient = RestClient.builder()
                .requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory(
                        HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(5))
                                .build()))
                .build();
    }

    @Override
    public ErpSyncResult sync(ErpSyncRequest request) {
        if (baseUrl == null || baseUrl.isBlank()) {
            log.info("[ERP-MOCK] 未配置 ERP_BASE_URL，模拟同步成功：bizType={}, bizId={}",
                    request.bizType(), request.bizId());
            return ErpSyncResult.success(mockResponse(request));
        }

        try {
            String url = baseUrl.replaceAll("/+$", "") + "/api/sync/" + request.bizType();
            String body = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(OBJECT_MAPPER.writeValueAsString(request.payload()))
                    .retrieve()
                    .body(String.class);
            log.info("[ERP-HTTP] 同步成功：url={}, bizId={}, response={}",
                    url, request.bizId(), body);
            return ErpSyncResult.success(body == null ? "{}" : body);
        } catch (Exception e) {
            log.warn("[ERP-HTTP] 同步失败：bizType={}, bizId={}, error={}",
                    request.bizType(), request.bizId(), e.getMessage());
            return ErpSyncResult.failure(e.getMessage());
        }
    }

    private String mockResponse(ErpSyncRequest request) {
        try {
            return OBJECT_MAPPER.writeValueAsString(java.util.Map.of(
                    "code", "0",
                    "message", "同步成功(MOCK)",
                    "data", java.util.Map.of(
                            "bizType", request.bizType(),
                            "bizId", request.bizId())
            ));
        } catch (Exception e) {
            return "{\"code\":\"0\",\"message\":\"同步成功(MOCK)\"}";
        }
    }
}
