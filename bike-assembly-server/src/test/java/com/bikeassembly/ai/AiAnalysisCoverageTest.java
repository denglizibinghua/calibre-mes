package com.bikeassembly.ai;

import com.bikeassembly.entity.*;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.mapper.*;
import com.bikeassembly.service.AiAnalysisService;
import com.bikeassembly.vo.AiAnalysisVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Service tests isolate database access with Mockito and use only loopback HTTP. */
class AiAnalysisCoverageTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final QmsInspectionMapper inspections = mock(QmsInspectionMapper.class);
    private final QmsDefectMapper defects = mock(QmsDefectMapper.class);
    private final BaseProcessMapper processes = mock(BaseProcessMapper.class);
    private final ProWorkOrderMapper orders = mock(ProWorkOrderMapper.class);
    private final BaseItemMapper items = mock(BaseItemMapper.class);
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private QmsInspection inspection;
    private ProWorkOrder order;
    private AiAnalysisService service;
    private HttpServer server;

    @BeforeEach
    void prepareFixture() {
        inspection = new QmsInspection();
        inspection.setInspectionId(1L);
        inspection.setInspectionNo("QC-UNIT-001");
        inspection.setProcessId(2L);
        inspection.setWorkOrderId(3L);
        inspection.setResult("F");
        inspection.setDetailsJson("[]");
        order = new ProWorkOrder();
        order.setWorkOrderId(3L);
        order.setWorkOrderNo("WO-UNIT-001");
        order.setItemId(4L);
        BaseProcess process = new BaseProcess();
        process.setProcessName("车轮装配");
        BaseItem item = new BaseItem();
        item.setItemName("测试自行车");
        when(inspections.selectById(1L)).thenReturn(inspection);
        when(defects.selectList(any())).thenReturn(List.of());
        when(processes.selectById(2L)).thenReturn(process);
        when(orders.selectById(3L)).thenReturn(order);
        when(items.selectById(4L)).thenReturn(item);
        service = new AiAnalysisService(2, inspections, defects, processes, orders, items);
        ReflectionTestUtils.setField(service, "apiKey", "unit-test-placeholder");
        ReflectionTestUtils.setField(service, "model", "test-model");
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void missingKeyReturnsBeforeDatabaseAccess(String key) {
        ReflectionTestUtils.setField(service, "apiKey", key);
        AiAnalysisVO result = service.analyzeDefect(1L);
        assertFalse(result.isAvailable());
        assertTrue(result.getMessage().contains("DEEPSEEK_API_KEY"));
        verifyNoInteractions(inspections, defects, processes, orders, items);
    }

    @Test
    void missingInspectionThrowsBusinessExceptionBeforeRelatedQueries() {
        when(inspections.selectById(1L)).thenReturn(null);
        assertThrows(BaseException.class, () -> service.analyzeDefect(1L));
        verifyNoInteractions(defects, processes, orders, items);
    }

    enum FixtureGap {
        PROCESS_ID, PROCESS_RECORD, ORDER_ID, ORDER_RECORD, ITEM_ID, ITEM_RECORD,
        INSPECTION_DETAILS, NON_FAILURE_RESULT, POPULATED_DEFECTS
    }

    @ParameterizedTest(name = "missing or alternative input: {0}")
    @EnumSource(FixtureGap.class)
    void optionalAssociationsDoNotBreakAnalysis(FixtureGap gap) throws Exception {
        String expectedPrompt = switch (gap) {
            case PROCESS_ID -> { inspection.setProcessId(null); yield "工序：\n"; }
            case PROCESS_RECORD -> { when(processes.selectById(2L)).thenReturn(null); yield "工序：\n"; }
            case ORDER_ID -> { inspection.setWorkOrderId(null); yield "工单号：\n"; }
            case ORDER_RECORD -> { when(orders.selectById(3L)).thenReturn(null); yield "产品：\n"; }
            case ITEM_ID -> { order.setItemId(null); yield "产品：\n"; }
            case ITEM_RECORD -> { when(items.selectById(4L)).thenReturn(null); yield "产品：\n"; }
            case INSPECTION_DETAILS -> { inspection.setDetailsJson(null); yield "检验明细：无"; }
            case NON_FAILURE_RESULT -> { inspection.setResult("P"); yield "质检结果：P"; }
            case POPULATED_DEFECTS -> {
                QmsDefect defect = new QmsDefect();
                defect.setDefectType("扭矩不足");
                defect.setQty(java.math.BigDecimal.ONE);
                defect.setHandleResult("返工");
                when(defects.selectList(any())).thenReturn(List.of(defect));
                yield "缺陷类型=扭矩不足";
            }
        };
        respond(200, envelope("{\"summary\":\"复检建议\",\"possibleCauses\":[],\"suggestions\":[],\"riskLevel\":\"中\"}"));
        AiAnalysisVO result = service.analyzeDefect(1L);
        assertTrue(result.isAvailable());
        assertEquals("复检建议", result.getSummary());
        JsonNode submitted = JSON.readTree(requestBody.get());
        assertEquals("test-model", submitted.path("model").asText());
        assertTrue(submitted.path("messages").path(1).path("content").asText().contains(expectedPrompt));
        verify(inspections).selectById(1L);
        if (gap == FixtureGap.PROCESS_ID) {
            verifyNoInteractions(processes);
        }
        if (gap == FixtureGap.ORDER_ID) {
            verifyNoInteractions(orders, items);
        }
        if (gap == FixtureGap.ITEM_ID || gap == FixtureGap.ORDER_RECORD) {
            verifyNoInteractions(items);
        }
    }

    @Test
    void emptyDefectListIsExplainedInPrompt() throws Exception {
        respond(200, envelope("{\"summary\":\"建议\"}"));
        assertTrue(service.analyzeDefect(1L).isAvailable());
        String prompt = JSON.readTree(requestBody.get()).path("messages").path(1).path("content").asText();
        assertTrue(prompt.contains("无独立缺陷记录"));
    }

    @Test
    void nonArrayFieldsBecomeEmptyLists() throws Exception {
        AiAnalysisVO result = service.parse("{\"summary\":\"s\",\"possibleCauses\":\"not-array\",\"suggestions\":null}");
        assertEquals(List.of(), result.getPossibleCauses());
        assertEquals(List.of(), result.getSuggestions());
        assertEquals("", result.getRiskLevel());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "{", "```json\n{\n```"})
    void malformedModelContentThrowsFromParser(String text) {
        assertThrows(Exception.class, () -> service.parse(text));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 429, 503})
    void httpFailureReturnsUnavailableWithoutThrowing(int status) throws Exception {
        respond(status, "{\"error\":\"simulated upstream failure\"}");
        AiAnalysisVO result = service.analyzeDefect(1L);
        assertFalse(result.isAvailable());
        assertTrue(result.getMessage().startsWith("AI 分析失败"));
    }

    @Test
    void malformedUpstreamEnvelopeReturnsUnavailable() throws Exception {
        respond(200, "not-json");
        assertFalse(service.analyzeDefect(1L).isAvailable());
    }

    @Test
    void malformedNestedModelContentReturnsUnavailable() throws Exception {
        respond(200, envelope("not-json"));
        assertFalse(service.analyzeDefect(1L).isAvailable());
    }

    private static String envelope(String content) throws Exception {
        return JSON.writeValueAsString(Map.of("choices", List.of(Map.of("message", Map.of("content", content)))));
    }

    private void respond(int status, String body) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        ReflectionTestUtils.setField(service, "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }
}
