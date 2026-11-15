package com.bikeassembly.service;

import com.bikeassembly.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 本地人脸服务适配器。协议故意收敛为特征提取 + 活体结果，
 * 后端不保存原始图像。
 */
@Component
public class FaceEmbeddingClient {
    private static final int MAX_IMAGE_BASE64_CHARS = 5_600_000;
    private final RestClient client;

    public FaceEmbeddingClient(
            @Value("${ai.face.service-url:${FACE_SERVICE_URL:http://localhost:8092}}") String serviceUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2_000);
        requestFactory.setReadTimeout(8_000);
        this.client = RestClient.builder().baseUrl(serviceUrl)
                .requestFactory(requestFactory).build();
    }

    @SuppressWarnings("unchecked")
    public FaceResult extract(String imageBase64) {
        if (imageBase64 == null || imageBase64.isBlank()) {
            throw new BaseException("人脸图像不能为空");
        }
        if (imageBase64.length() > MAX_IMAGE_BASE64_CHARS) {
            throw new BaseException("人脸图像过大");
        }
        try {
            Map<String, Object> body = client.post()
                    .uri("/v1/face/embedding")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("imageBase64", imageBase64))
                    .retrieve()
                    .body(Map.class);
            if (body == null) throw new BaseException("人脸服务返回为空");
            int faceCount = ((Number) body.getOrDefault("faceCount", 0)).intValue();
            boolean liveness = Boolean.TRUE.equals(body.get("liveness"));
            List<Number> values = (List<Number>) body.get("embedding");
            if (faceCount != 1) throw new BaseException(faceCount == 0 ? "未检测到人脸" : "请确保画面中只有一张人脸");
            if (!liveness) throw new BaseException("活体检测未通过，请使用实时摄像头采集");
            if (values == null || values.size() != 128) throw new BaseException("人脸特征维度无效");
            double[] embedding = new double[values.size()];
            double norm = 0;
            for (int i = 0; i < values.size(); i++) {
                embedding[i] = values.get(i).doubleValue();
                if (!Double.isFinite(embedding[i])) throw new BaseException("人脸特征包含无效数值");
                norm += embedding[i] * embedding[i];
            }
            if (norm == 0) throw new BaseException("人脸特征无效");
            return new FaceResult(embedding, true);
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            throw new BaseException("本地人脸服务不可用，已拒绝人脸操作");
        }
    }

    public record FaceResult(double[] embedding, boolean liveness) {
    }
}
