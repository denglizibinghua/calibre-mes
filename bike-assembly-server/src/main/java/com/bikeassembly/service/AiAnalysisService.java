package com.bikeassembly.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.entity.BaseItem;
import com.bikeassembly.entity.BaseProcess;
import com.bikeassembly.entity.ProWorkOrder;
import com.bikeassembly.entity.QmsDefect;
import com.bikeassembly.entity.QmsInspection;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.mapper.BaseProcessMapper;
import com.bikeassembly.mapper.BaseItemMapper;
import com.bikeassembly.mapper.ProWorkOrderMapper;
import com.bikeassembly.mapper.QmsDefectMapper;
import com.bikeassembly.mapper.QmsInspectionMapper;
import com.bikeassembly.vo.AiAnalysisVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 质检分析服务：质检不合格时调用 DeepSeek（OpenAI 兼容协议）生成原因分析与建议。
 * <p>
 * 未配置 {@code DEEPSEEK_API_KEY} 或调用失败时返回 available=false 的降级结果，不影响主流程。
 */
@Service
@Slf4j
public class AiAnalysisService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Value("${ai.deepseek.api-key:}")
    private String apiKey;
    @Value("${ai.deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;
    @Value("${ai.deepseek.model:deepseek-chat}")
    private String model;

    private final RestClient restClient;
    private final QmsInspectionMapper qmsInspectionMapper;
    private final QmsDefectMapper qmsDefectMapper;
    private final BaseProcessMapper baseProcessMapper;
    private final ProWorkOrderMapper proWorkOrderMapper;
    private final BaseItemMapper baseItemMapper;

    @Autowired
    public AiAnalysisService(@Value("${ai.deepseek.timeout-seconds:30}") int timeoutSeconds,
                             QmsInspectionMapper qmsInspectionMapper,
                             QmsDefectMapper qmsDefectMapper,
                             BaseProcessMapper baseProcessMapper,
                             ProWorkOrderMapper proWorkOrderMapper,
                             BaseItemMapper baseItemMapper) {
        this.restClient = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                        .build()))
                .build();
        this.qmsInspectionMapper = qmsInspectionMapper;
        this.qmsDefectMapper = qmsDefectMapper;
        this.baseProcessMapper = baseProcessMapper;
        this.proWorkOrderMapper = proWorkOrderMapper;
        this.baseItemMapper = baseItemMapper;
    }

    /**
     * 质检不合格原因辅助分析。
     *
     * @param inspectionId 质检单ID
     * @return 结构化分析结果；AI 未配置/调用失败时返回 available=false
     */
    public AiAnalysisVO analyzeDefect(Long inspectionId) {
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("AI 未配置，跳过质检分析：inspectionId={}", inspectionId);
            return AiAnalysisVO.builder()
                    .available(false)
                    .message("AI 未配置：请设置 DEEPSEEK_API_KEY 后使用")
                    .build();
        }

        QmsInspection inspection = qmsInspectionMapper.selectById(inspectionId);
        if (inspection == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        List<QmsDefect> defects = qmsDefectMapper.selectList(
                new LambdaQueryWrapper<QmsDefect>()
                        .eq(QmsDefect::getInspectionId, inspectionId));

        String processName = loadProcessName(inspection.getProcessId());
        String workOrderNo = loadWorkOrderNo(inspection.getWorkOrderId());
        String itemName = loadItemName(inspection.getWorkOrderId());

        String systemPrompt = "你是自行车装配 MES 系统的资深质量工程师。"
                + "根据质检单与缺陷记录，分析不合格原因并给出改进建议。"
                + "只输出 JSON，不要包含 Markdown 代码块标记，格式："
                + "{\"summary\":\"分析摘要\",\"possibleCauses\":[\"可能原因1\",\"可能原因2\"],"
                + "\"suggestions\":[\"建议措施1\",\"建议措施2\"],\"riskLevel\":\"高|中|低\"}";
        String userPrompt = buildUserPrompt(inspection, defects, processName, workOrderNo, itemName);

        try {
            String content = callChat(systemPrompt, userPrompt);
            return parse(content);
        } catch (Exception e) {
            log.warn("AI 质检分析失败：inspectionId={}, error={}", inspectionId, e.getMessage(), e);
            return AiAnalysisVO.builder()
                    .available(false)
                    .message("AI 分析失败：" + e.getMessage())
                    .build();
        }
    }

    private String callChat(String systemPrompt, String userPrompt) throws Exception {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)),
                "temperature", 0.3,
                "response_format", Map.of("type", "json_object"));

        String url = baseUrl.replaceAll("/+$", "") + "/chat/completions";
        String response = restClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(OBJECT_MAPPER.writeValueAsString(requestBody))
                .retrieve()
                .body(String.class);

        JsonNode root = OBJECT_MAPPER.readTree(response);
        return root.path("choices").path(0).path("message").path("content").asText("");
    }

    /** 解析模型输出的 JSON（兼容 Markdown 代码块包裹）。 */
    public AiAnalysisVO parse(String content) throws Exception {
        String json = content == null ? "" : content.trim();
        if (json.startsWith("```")) {
            json = json.replaceFirst("^```[a-zA-Z]*\\s*", "");
            json = json.replaceAll("\\s*```$", "");
        }
        JsonNode node = OBJECT_MAPPER.readTree(json);
        return AiAnalysisVO.builder()
                .available(true)
                .model(model)
                .summary(node.path("summary").asText(""))
                .possibleCauses(readStringArray(node, "possibleCauses"))
                .suggestions(readStringArray(node, "suggestions"))
                .riskLevel(node.path("riskLevel").asText(""))
                .build();
    }

    private List<String> readStringArray(JsonNode node, String field) {
        List<String> result = new ArrayList<>();
        JsonNode array = node.path(field);
        if (array.isArray()) {
            array.forEach(item -> result.add(item.asText()));
        }
        return result;
    }

    private String buildUserPrompt(QmsInspection inspection, List<QmsDefect> defects,
                                   String processName, String workOrderNo, String itemName) {
        StringBuilder sb = new StringBuilder();
        sb.append("质检单号：").append(inspection.getInspectionNo()).append("\n");
        sb.append("工单号：").append(workOrderNo).append("\n");
        sb.append("产品：").append(itemName).append("\n");
        sb.append("工序：").append(processName).append("\n");
        sb.append("质检结果：").append("F".equals(inspection.getResult()) ? "不合格" : inspection.getResult()).append("\n");
        sb.append("检验明细：").append(inspection.getDetailsJson() == null ? "无" : inspection.getDetailsJson()).append("\n");
        sb.append("缺陷记录：\n");
        if (defects.isEmpty()) {
            sb.append("  （无独立缺陷记录）\n");
        } else {
            for (QmsDefect d : defects) {
                sb.append("  - 缺陷类型=").append(d.getDefectType())
                        .append("，数量=").append(d.getQty())
                        .append("，处理结果=").append(d.getHandleResult()).append("\n");
            }
        }
        sb.append("请输出 JSON 分析结果。");
        return sb.toString();
    }

    private String loadProcessName(Long processId) {
        if (processId == null) {
            return "";
        }
        BaseProcess process = baseProcessMapper.selectById(processId);
        return process == null ? "" : process.getProcessName();
    }

    private String loadWorkOrderNo(Long workOrderId) {
        if (workOrderId == null) {
            return "";
        }
        ProWorkOrder wo = proWorkOrderMapper.selectById(workOrderId);
        return wo == null ? "" : wo.getWorkOrderNo();
    }

    private String loadItemName(Long workOrderId) {
        if (workOrderId == null) {
            return "";
        }
        ProWorkOrder wo = proWorkOrderMapper.selectById(workOrderId);
        if (wo == null || wo.getItemId() == null) {
            return "";
        }
        BaseItem item = baseItemMapper.selectById(wo.getItemId());
        return item == null ? "" : item.getItemName();
    }
}
