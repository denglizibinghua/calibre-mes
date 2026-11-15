package com.bikeassembly.ai;

import com.bikeassembly.entity.BaseItem;
import com.bikeassembly.entity.BaseProcess;
import com.bikeassembly.entity.ProWorkOrder;
import com.bikeassembly.entity.QmsDefect;
import com.bikeassembly.entity.QmsInspection;
import com.bikeassembly.mapper.BaseProcessMapper;
import com.bikeassembly.mapper.BaseItemMapper;
import com.bikeassembly.mapper.ProWorkOrderMapper;
import com.bikeassembly.mapper.QmsDefectMapper;
import com.bikeassembly.mapper.QmsInspectionMapper;
import com.bikeassembly.service.AiAnalysisService;
import com.bikeassembly.vo.AiAnalysisVO;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AI 质检分析服务单元测试。
 * <p>
 * 覆盖：JSON 解析（含 Markdown 包裹）、无 Key 优雅降级、本地 HTTP 端到端（模拟 DeepSeek 响应）。
 */
@DisplayName("AI 质检分析服务单元测试")
class AiAnalysisServiceTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("解析模型输出 JSON")
    void parse_validJson() throws Exception {
        AiAnalysisService service = newServiceWithMocks();
        ReflectionTestUtils.setField(service, "model", "deepseek-chat");

        AiAnalysisVO vo = service.parse(
                "{\"summary\":\"扭矩不足\",\"possibleCauses\":[\"扳手未校准\"],"
                        + "\"suggestions\":[\"校准扳手\"],\"riskLevel\":\"中\"}");

        assertTrue(vo.isAvailable());
        assertEquals("扭矩不足", vo.getSummary());
        assertEquals("中", vo.getRiskLevel());
        assertEquals(List.of("扳手未校准"), vo.getPossibleCauses());
    }

    @Test
    @DisplayName("解析带 Markdown 代码块包裹的 JSON")
    void parse_markdownFencedJson() throws Exception {
        AiAnalysisService service = newServiceWithMocks();

        AiAnalysisVO vo = service.parse("```json\n{\"summary\":\"外观划痕\","
                + "\"possibleCauses\":[],\"suggestions\":[],\"riskLevel\":\"低\"}\n```");

        assertTrue(vo.isAvailable());
        assertEquals("外观划痕", vo.getSummary());
    }

    @Test
    @DisplayName("未配置 API Key 时返回降级结果")
    void analyzeDefect_noKey_returnsUnavailable() {
        AiAnalysisService service = newServiceWithMocks();
        ReflectionTestUtils.setField(service, "apiKey", "");

        AiAnalysisVO vo = service.analyzeDefect(1L);

        assertFalse(vo.isAvailable());
        assertTrue(vo.getMessage().contains("DEEPSEEK_API_KEY"));
    }

    @Test
    @DisplayName("配置 Key 且模拟 DeepSeek 响应时返回结构化分析")
    void analyzeDefect_withMockHttp_returnsParsedResult() throws Exception {
        String modelContent = "{\"summary\":\"装配扭矩不足导致车轮松旷\","
                + "\"possibleCauses\":[\"扭矩扳手未校准\",\"操作员未按工艺扭矩作业\"],"
                + "\"suggestions\":[\"校准扭矩扳手并复检\",\"增加工艺扭矩目视看板\"],"
                + "\"riskLevel\":\"高\"}";
        String apiResponse = "{\"choices\":[{\"message\":{\"content\":\""
                + modelContent.replace("\"", "\\\"")
                + "\"}}]}";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            byte[] bytes = apiResponse.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();

        AiAnalysisService service = newServiceWithMocks();
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
        ReflectionTestUtils.setField(service, "model", "deepseek-chat");

        AiAnalysisVO vo = service.analyzeDefect(1L);

        assertTrue(vo.isAvailable(), "模拟 DeepSeek 响应应解析成功");
        assertEquals("装配扭矩不足导致车轮松旷", vo.getSummary());
        assertEquals("高", vo.getRiskLevel());
        assertEquals(2, vo.getPossibleCauses().size());
        assertEquals(2, vo.getSuggestions().size());
    }

    private AiAnalysisService newServiceWithMocks() {
        QmsInspectionMapper inspectionMapper = mock(QmsInspectionMapper.class);
        QmsDefectMapper defectMapper = mock(QmsDefectMapper.class);
        BaseProcessMapper processMapper = mock(BaseProcessMapper.class);
        ProWorkOrderMapper workOrderMapper = mock(ProWorkOrderMapper.class);
        BaseItemMapper itemMapper = mock(BaseItemMapper.class);

        QmsInspection inspection = new QmsInspection();
        inspection.setInspectionId(1L);
        inspection.setInspectionNo("QC2026TEST001");
        inspection.setWorkOrderId(1L);
        inspection.setProcessId(1L);
        inspection.setResult("F");
        inspection.setDetailsJson("[]");
        when(inspectionMapper.selectById(1L)).thenReturn(inspection);

        QmsDefect defect = new QmsDefect();
        defect.setDefectType("扭矩不足");
        defect.setQty(new java.math.BigDecimal("2"));
        defect.setHandleResult("返工");
        when(defectMapper.selectList(any())).thenReturn(List.of(defect));

        BaseProcess process = new BaseProcess();
        process.setProcessId(1L);
        process.setProcessName("车轮安装");
        when(processMapper.selectById(1L)).thenReturn(process);

        ProWorkOrder wo = new ProWorkOrder();
        wo.setWorkOrderId(1L);
        wo.setWorkOrderNo("WO-TEST-001");
        wo.setItemId(1L);
        when(workOrderMapper.selectById(1L)).thenReturn(wo);

        BaseItem item = new BaseItem();
        item.setItemId(1L);
        item.setItemName("测试自行车");
        when(itemMapper.selectById(1L)).thenReturn(item);

        return new AiAnalysisService(10, inspectionMapper, defectMapper,
                processMapper, workOrderMapper, itemMapper);
    }
}
