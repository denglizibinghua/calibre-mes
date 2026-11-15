package com.bikeassembly.service;

import com.bikeassembly.dto.AiQueryDTO;
import com.bikeassembly.dto.DashboardScreenQueryDTO;
import com.bikeassembly.mapper.AiMesDataMapper;
import com.bikeassembly.vo.AiQueryVO;
import com.bikeassembly.vo.DashboardScreenVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * MES 助手查询服务。
 *
 * <p>模型只负责自然语言总结，所有业务数据先由只读查询生成快照。
 * 即使上游模型未配置或暂时不可用，也会返回基于数据库的确定性摘要，
 * 避免用户看到空白回答。</p>
 */
@Service
@Slf4j
public class AiQueryService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final ChatClient.Builder chatClientBuilder;
    private final DashboardScreenService dashboardScreenService;
    private final AiMesDataMapper mesDataMapper;
    private final ObjectMapper objectMapper;
    private final VectorStore vectorStore;
    private final DataScopeService dataScopeService;
    private final AiRequestAuditService auditService;
    private final AiKnowledgeGraphService knowledgeGraphService;

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;
    @Value("${spring.ai.openai.chat.options.model:}")
    private String model;
    @Value("${ai.rag.min-similarity:0.70}")
    private double ragMinSimilarity;
    @Value("${ai.context.max-chars:12000}")
    private int maxContextChars;

    @Autowired
    public AiQueryService(ChatClient.Builder chatClientBuilder,
                          DashboardScreenService dashboardScreenService,
                          AiMesDataMapper mesDataMapper,
                          ObjectMapper objectMapper,
                          ObjectProvider<VectorStore> vectorStoreProvider,
                          DataScopeService dataScopeService,
                          AiRequestAuditService auditService,
                          AiKnowledgeGraphService knowledgeGraphService) {
        this.chatClientBuilder = chatClientBuilder;
        this.dashboardScreenService = dashboardScreenService;
        this.mesDataMapper = mesDataMapper;
        this.objectMapper = objectMapper;
        this.vectorStore = vectorStoreProvider.getIfAvailable();
        this.dataScopeService = dataScopeService;
        this.auditService = auditService;
        this.knowledgeGraphService = knowledgeGraphService;
    }

    /** Backwards-compatible constructor for isolated tests and legacy callers. */
    public AiQueryService(ChatClient.Builder chatClientBuilder,
                          DashboardScreenService dashboardScreenService,
                          AiMesDataMapper mesDataMapper,
                          ObjectMapper objectMapper,
                          ObjectProvider<VectorStore> vectorStoreProvider,
                          DataScopeService dataScopeService,
                          AiRequestAuditService auditService) {
        this(chatClientBuilder, dashboardScreenService, mesDataMapper, objectMapper,
                vectorStoreProvider, dataScopeService, auditService, null);
    }

    public AiQueryVO ask(AiQueryDTO dto) {
        String requestId = UUID.randomUUID().toString();
        long startedAt = System.currentTimeMillis();
        MesSnapshot snapshot = snapshot(dto);
        String fallback = localAnswer(dto.getQuestion(), snapshot);
        if (!aiConfigured()) {
            AiQueryVO result = unavailable(requestId, fallback, snapshot,
                    "模型未配置，已使用 MES 数据直接生成摘要。请设置 AI_OPENAI_API_KEY；"
                            + "可通过 AI_CHAT_MODEL 覆盖默认模型。");
            audit(snapshot, requestId, dto, "FALLBACK", true, startedAt, "MODEL_NOT_CONFIGURED");
            return result;
        }

        try {
            String answer = chatClientBuilder.build().prompt()
                    .system(systemPrompt())
                    .user(snapshot.prompt())
                    .call()
                    .content();
            if (answer == null || answer.isBlank()) {
                AiQueryVO result = unavailable(requestId, fallback, snapshot,
                        "模型返回空内容，已使用 MES 数据直接生成摘要。");
                audit(snapshot, requestId, dto, "FALLBACK", true, startedAt, "EMPTY_RESPONSE");
                return result;
            }
            AiQueryVO result = AiQueryVO.builder()
                    .requestId(requestId)
                    .available(true)
                    .answer(sanitizeCitations(answer, snapshot.citations()))
                    .model(model)
                    .sources(snapshot.sources())
                    .citations(snapshot.citations())
                    .dataScope(snapshot.scope().name())
                    .dataTimeRange(snapshot.dataTimeRange())
                    .fallback(false)
                    .graphNodes(snapshot.graph().nodes())
                    .graphEdges(snapshot.graph().edges())
                    .build();
            audit(snapshot, requestId, dto, "SUCCESS", false, startedAt, null);
            return result;
        } catch (Exception e) {
            log.warn("AI 查询上游调用失败，返回本地 MES 摘要: {}", conciseError(e));
            AiQueryVO result = unavailable(requestId, fallback, snapshot,
                    "模型暂时不可用（" + conciseError(e) + "），已使用 MES 数据直接生成摘要。");
            audit(snapshot, requestId, dto, "FALLBACK", true, startedAt, "UPSTREAM_ERROR");
            return result;
        }
    }

    private void audit(MesSnapshot snapshot, String requestId, AiQueryDTO dto,
                       String status, boolean fallback, long startedAt, String errorCode) {
        auditService.record(requestId, dto.getSessionId(), snapshot.scope().userId(), "QUERY",
                dto.getQuestion(), snapshot.scope().name(), snapshot.dataTimeRange(), model,
                snapshot.sources(), "domains=" + snapshot.prompt().length(),
                status, fallback, System.currentTimeMillis() - startedAt, errorCode);
    }

    private AiQueryVO unavailable(String requestId, String answer, MesSnapshot snapshot, String message) {
        return AiQueryVO.builder()
                .requestId(requestId)
                .available(false)
                .answer(answer)
                .model(model)
                .sources(snapshot.sources())
                .citations(snapshot.citations())
                .dataScope(snapshot.scope().name())
                .dataTimeRange(snapshot.dataTimeRange())
                .fallback(true)
                .message(message)
                .graphNodes(snapshot.graph().nodes())
                .graphEdges(snapshot.graph().edges())
                .build();
    }

    private boolean aiConfigured() {
        return apiKey != null && !apiKey.isBlank()
                && !"local-disabled-key".equals(apiKey)
                && model != null && !model.isBlank();
    }

    /** 给流式接口复用的上下文构造方法。调用方必须先恢复当前用户的数据范围。 */
    public String buildPromptContext(AiQueryDTO dto) {
        return snapshot(dto).prompt();
    }

    /** Builds the prompt and server-verified metadata used by streaming replies. */
    public StreamContext buildStreamContext(AiQueryDTO dto) {
        MesSnapshot snapshot = snapshot(dto);
        return new StreamContext(snapshot.prompt(), snapshot.sources(),
                snapshot.scope().name(), snapshot.dataTimeRange(),
                snapshot.graph().nodes(), snapshot.graph().edges());
    }

    /** 不调用模型，直接基于当前权限范围生成确定性 MES 摘要。 */
    public String fallbackAnswer(AiQueryDTO dto) {
        return localAnswer(dto.getQuestion(), snapshot(dto));
    }

    public String systemPrompt() {
        return "你是自行车装配 MES 生产运营助手。只能根据提供的 MES 数据回答，不得编造数据，"
                + "不得执行 SQL，不得修改工单、库存或设备数据。回答先给结论，再给数量、时间范围和数据依据；"
                + "数据不足时明确说明，不要用泛泛的通用知识代替 MES 数据。"
                + "如果使用知识库内容，必须在对应结论后标注 [来源1]、[来源2] 等引用；"
                + "知识库没有依据时要明确说未检索到工艺文档依据，并把 MES 结构化数据和文档依据分开。";
    }

    private MesSnapshot snapshot(AiQueryDTO dto) {
        int days = normalizeDays(dto.getDays());
        DashboardScreenQueryDTO query = new DashboardScreenQueryDTO();
        query.setDays(days);

        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        LocalDate startDate = now.toLocalDate().minusDays(days - 1L);
        LocalDateTime start = startDate.atStartOfDay();
        DataScopeService.Scope scope = dataScopeService.current();
        if (scope.department()) {
            query.setWorkUnitId(scope.rootUnitId());
        }
        Long userId = scope.name().equals("SELF") ? scope.userId()
                : (scope.name().equals("DENIED") ? -1L : null);
        QueryRoute route = route(dto.getQuestion());

        DashboardScreenVO screen = route.includes(QueryDomain.PRODUCTION) || route.includes(QueryDomain.MIXED)
                ? dashboardScreenService.screen(query) : null;
        List<Map<String, Object>> quality = route.includes(QueryDomain.QUALITY) || route.includes(QueryDomain.MIXED)
                ? mesDataMapper.qualityByProcess(start, now, userId, scope.unitIds()) : List.of();
        List<Map<String, Object>> inventory = route.includes(QueryDomain.INVENTORY) || route.includes(QueryDomain.MIXED)
                ? mesDataMapper.inventorySummary(userId, scope.unitIds()) : List.of();
        List<Map<String, Object>> repairs = route.includes(QueryDomain.EQUIPMENT) || route.includes(QueryDomain.MIXED)
                ? mesDataMapper.repairSummary(start, now, userId, scope.unitIds()) : List.of();
        List<Map<String, Object>> andon = route.includes(QueryDomain.ANDON) || route.includes(QueryDomain.MIXED)
                ? mesDataMapper.andonSummary(start, now, userId, scope.unitIds()) : List.of();
        boolean retrieveDocuments = route.includes(QueryDomain.DOCUMENTS)
                || route.includes(QueryDomain.MIXED)
                || dto.getRetrievalMode() != AiQueryDTO.RetrievalMode.RAG;
        KnowledgeContext knowledge = retrieveDocuments
                ? buildKnowledgeContext(dto.getQuestion(), scope.userId(), dto.getRetrievalMode())
                : KnowledgeContext.empty("知识库：本问题未启用文档检索。");
        AiKnowledgeGraphService.GraphContext graph =
                dto.getRetrievalMode() == AiQueryDTO.RetrievalMode.RAG || knowledgeGraphService == null
                        ? AiKnowledgeGraphService.GraphContext.empty()
                        : knowledgeGraphService.search(dto.getQuestion(), scope.userId());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("统计开始", start);
        data.put("统计结束", now);
        data.put("数据范围", scope.name());
        if (screen != null) data.put("生产看板", screen);
        if (!quality.isEmpty()) data.put("按工序质量异常", limitRows(quality, 20));
        if (!inventory.isEmpty()) data.put("库存摘要", limitRows(inventory, 30));
        if (!repairs.isEmpty()) data.put("设备维修", limitRows(repairs, 30));
        if (!andon.isEmpty()) data.put("安灯告警", limitRows(andon, 30));

        String prompt = "以下是当前用户权限范围内的只读 MES 数据快照（JSON），只能据此回答：\n"
                + limitContext(toJson(data))
                + "\n\n" + knowledge.context()
                + "\n\n" + graph.context()
                + "\n\n用户问题：" + dto.getQuestion();
        List<String> sources = new ArrayList<>(List.of(
                "MES 生产看板（" + startDate + " 至 " + now.toLocalDate() + "）",
                "MES 质量、工单、库存、设备维修和安灯只读查询",
                "当前用户数据权限范围：" + scope.name()));
        sources.addAll(knowledge.sources());
        sources.addAll(graph.sources().stream().map(source -> "[图谱来源] " + source).toList());
        return new MesSnapshot(days, start, now, screen, quality, inventory, repairs, andon,
                sources, knowledge.sources(), graph, scope, startDate + " 至 " + now.toLocalDate(), prompt);
    }

    private int normalizeDays(Integer value) {
        return value == null ? 7 : Math.min(Math.max(value, 1), 30);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("序列化 MES AI 上下文失败", e);
            return String.valueOf(value);
        }
    }

    private KnowledgeContext buildKnowledgeContext(String question, Long ownerId,
                                                   AiQueryDTO.RetrievalMode retrievalMode) {
        if (retrievalMode == AiQueryDTO.RetrievalMode.GRAPHRAG) {
            return KnowledgeContext.empty("知识库：当前为 GraphRAG 模式，使用图谱上下文。");
        }
        if (vectorStore == null) {
            return new KnowledgeContext("知识库：当前未启用向量检索。", List.of());
        }
        try {
            var filterBuilder = new FilterExpressionBuilder();
            var visibleScope = ownerId == null
                    ? filterBuilder.eq("scope", "PUBLIC")
                    : filterBuilder.or(filterBuilder.eq("scope", "PUBLIC"),
                    filterBuilder.and(filterBuilder.eq("scope", "PRIVATE"),
                            filterBuilder.eq("ownerId", ownerId)));
            var request = SearchRequest.builder()
                    .query(question)
                    .topK(4)
                    .similarityThreshold(ragMinSimilarity)
                    .filterExpression(visibleScope.build())
                    .build();
            var docs = vectorStore.similaritySearch(request);
            if (docs == null || docs.isEmpty()) {
                return new KnowledgeContext("知识库：未检索到相关文档。", List.of());
            }
            List<org.springframework.ai.document.Document> visibleDocs = docs.stream()
                    .filter(doc -> visibleTo(doc, ownerId)).limit(4).toList();
            if (visibleDocs.isEmpty()) {
                return new KnowledgeContext("知识库：未检索到当前用户可见的相关文档。", List.of());
            }
            List<String> sources = new ArrayList<>();
            String context = "知识库检索结果（仅可引用以下文档）：\n"
                    + java.util.stream.IntStream.range(0, visibleDocs.size())
                    .mapToObj(index -> {
                        var doc = visibleDocs.get(index);
                        String source = String.valueOf(doc.getMetadata()
                                .getOrDefault("relativeSource",
                                        doc.getMetadata().getOrDefault("source", "未知")));
                        String title = String.valueOf(doc.getMetadata().getOrDefault("title", ""));
                        String modified = String.valueOf(doc.getMetadata().getOrDefault("lastModified", "未知"));
                        String chunk = String.valueOf(doc.getMetadata().getOrDefault("chunk", "?"));
                        sources.add("[来源" + (index + 1) + "] " + source
                                + (title.isBlank() ? "" : "（" + title + "）")
                                + "，chunk " + chunk + "，更新时间 " + modified);
                        return "[来源" + (index + 1) + "] " + source
                                + "\n标题：" + title
                                + "\n更新时间：" + modified
                                + "\n内容：" + doc.getText();
                    })
                    .collect(Collectors.joining("\n\n"));
            return new KnowledgeContext(context, sources);
        } catch (Exception e) {
            log.warn("AI 知识库检索失败: {}", conciseError(e));
            return new KnowledgeContext("知识库：检索失败，不能作为回答依据。", List.of());
        }
    }

    private boolean visibleTo(org.springframework.ai.document.Document doc, Long ownerId) {
        Object scope = doc.getMetadata().get("scope");
        if ("PUBLIC".equalsIgnoreCase(String.valueOf(scope))) return true;
        if (!"PRIVATE".equalsIgnoreCase(String.valueOf(scope)) || ownerId == null) return false;
        Object documentOwner = doc.getMetadata().get("ownerId");
        return documentOwner != null && String.valueOf(ownerId).equals(String.valueOf(documentOwner));
    }

    /** 不依赖模型的本地兜底回答，覆盖助手最常见的 MES 查询。 */
    private String localAnswer(String question, MesSnapshot snapshot) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        if (containsAny(q, "质量", "质检", "缺陷", "异常", "不合格")) {
            return qualityAnswer(snapshot);
        }
        if (containsAny(q, "库存", "物料", "缺料", "余量")) {
            return inventoryAnswer(snapshot);
        }
        if (containsAny(q, "维修", "设备故障", "设备状态", "保养")) {
            return repairAnswer(snapshot);
        }
        if (containsAny(q, "安灯", "告警", "报警")) {
            return andonAnswer(snapshot);
        }
        if (containsAny(q, "工单", "订单", "排产", "生产", "进度", "延期")) {
            return productionAnswer(snapshot);
        }
        return overviewAnswer(snapshot);
    }

    private QueryRoute route(String question) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        EnumSet<QueryDomain> domains = EnumSet.noneOf(QueryDomain.class);
        if (containsAny(q, "工单", "订单", "排产", "生产", "进度", "延期")) domains.add(QueryDomain.PRODUCTION);
        if (containsAny(q, "质量", "质检", "缺陷", "异常", "不合格")) domains.add(QueryDomain.QUALITY);
        if (containsAny(q, "库存", "物料", "缺料", "余量")) domains.add(QueryDomain.INVENTORY);
        if (containsAny(q, "维修", "设备故障", "设备状态", "保养")) domains.add(QueryDomain.EQUIPMENT);
        if (containsAny(q, "安灯", "告警", "报警")) domains.add(QueryDomain.ANDON);
        if (containsAny(q, "工艺", "规范", "文档", "知识库", "作业指导", "扭矩",
                "状态机", "流转", "怎么操作", "操作流程", "操作步骤")) domains.add(QueryDomain.DOCUMENTS);
        if (domains.isEmpty()) domains.add(QueryDomain.MIXED);
        if (domains.size() > 2) {
            domains.clear();
            domains.add(QueryDomain.MIXED);
        }
        return new QueryRoute(domains);
    }

    private List<Map<String, Object>> limitRows(List<Map<String, Object>> rows, int limit) {
        return rows == null || rows.size() <= limit ? rows : rows.subList(0, limit);
    }

    private String limitContext(String context) {
        if (context == null || context.length() <= Math.max(maxContextChars, 2000)) return context;
        return context.substring(0, Math.max(maxContextChars, 2000))
                + "\n[上下文已按安全上限截断，请基于已有数据回答。]";
    }

    private String sanitizeCitations(String answer, List<String> citations) {
        if (answer == null || answer.isBlank()) return answer;
        int allowed = citations == null ? 0 : citations.size();
        Matcher matcher = Pattern.compile("\\[来源(\\d+)\\]").matcher(answer);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            try {
                int index = Integer.parseInt(matcher.group(1));
                matcher.appendReplacement(result,
                        Matcher.quoteReplacement(index >= 1 && index <= allowed ? matcher.group() : ""));
            } catch (NumberFormatException e) {
                matcher.appendReplacement(result, "");
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String qualityAnswer(MesSnapshot s) {
        long failed = s.quality().stream().mapToLong(row -> number(row, "failedCount")).sum();
        BigDecimal defectQty = s.quality().stream()
                .map(row -> decimal(row, "defectQty"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String ranking = s.quality().stream().limit(8)
                .map(row -> text(row, "processName") + "：不合格 " + number(row, "failedCount")
                        + " 次，缺陷数量 " + decimal(row, "defectQty").stripTrailingZeros().toPlainString())
                .collect(Collectors.joining("；"));
        if (ranking.isBlank()) ranking = "暂无已确认的质量检验记录";
        return "统计 " + s.days() + " 天（" + s.start().toLocalDate() + " 至 "
                + s.end().toLocalDate() + "）：共发现不合格 " + failed + " 次，缺陷数量 "
                + defectQty.stripTrailingZeros().toPlainString() + "。按工序："
                + ranking + "。依据：已确认初检 qms_inspection/qms_defect 数据。";
    }

    private String inventoryAnswer(MesSnapshot s) {
        if (s.inventory().isEmpty()) {
            return "当前用户数据范围内没有可查询的库存记录；库存表没有人员归属，只有全部数据权限才允许助手查询库存。";
        }
        String items = s.inventory().stream().limit(8)
                .map(row -> text(row, "itemName") + "：可用 "
                        + decimal(row, "quantity").subtract(decimal(row, "lockedQuantity"))
                        .stripTrailingZeros().toPlainString() + " " + text(row, "unit"))
                .collect(Collectors.joining("；"));
        return "库存摘要（按可用数量从低到高）：" + items
                + "。可用数量=库存数量-锁定数量，数据来自 wms_inventory。";
    }

    private String repairAnswer(MesSnapshot s) {
        long open = s.repairs().stream().filter(row -> !"2".equals(text(row, "status"))
                && !"3".equals(text(row, "status"))).count();
        if (s.repairs().isEmpty()) return "统计周期内没有当前数据范围内的设备维修记录。";
        String items = s.repairs().stream().limit(8)
                .map(row -> text(row, "equipmentName") + "（" + text(row, "status")
                        + "）：" + text(row, "faultDesc"))
                .collect(Collectors.joining("；"));
        return "统计周期内维修记录 " + s.repairs().size() + " 条，其中未完成约 " + open
                + " 条。明细：" + items + "。依据：eqm_repair_order。";
    }

    private String andonAnswer(MesSnapshot s) {
        long open = s.andon().stream().filter(row -> "0".equals(text(row, "status"))
                || "1".equals(text(row, "status"))).count();
        if (s.andon().isEmpty()) return "统计周期内没有当前数据范围内的安灯告警记录。";
        String items = s.andon().stream().limit(8)
                .map(row -> text(row, "alertNo") + "：" + text(row, "alertType")
                        + "，等级 " + text(row, "alertLevel") + "，状态 " + text(row, "status"))
                .collect(Collectors.joining("；"));
        return "统计周期内安灯告警 " + s.andon().size() + " 条，当前未关闭约 " + open
                + " 条。明细：" + items + "。依据：andon_alert。";
    }

    private String productionAnswer(MesSnapshot s) {
        if (s.screen() == null) {
            return overviewAnswer(s);
        }
        DashboardScreenVO.Summary summary = s.screen().getSummary();
        if (summary == null) return overviewAnswer(s);
        return "生产概览（" + s.days() + " 天）：进行中工单 " + summary.getActiveWorkOrders()
                + "，今日完工 " + summary.getTodayCompletedWorkOrders()
                + "，逾期 " + summary.getOverdueWorkOrders()
                + "，待质检 " + summary.getPendingInspections()
                + "，未关闭安灯 " + summary.getOpenAlerts() + "。";
    }

    private String overviewAnswer(MesSnapshot s) {
        if (s.screen() == null) {
            return "当前问题已按权限范围检索对应业务数据。若需要生产概览，请询问工单、进度、延期或排产。";
        }
        DashboardScreenVO.Summary summary = s.screen().getSummary();
        DashboardScreenVO.Quality quality = s.screen().getQuality();
        DashboardScreenVO.Equipment equipment = s.screen().getEquipment();
        return "我可以查询工单/生产进度、工序质量异常、库存、设备维修和安灯告警。当前 "
                + s.days() + " 天概览：进行中工单 " + (summary == null ? 0 : summary.getActiveWorkOrders())
                + "，逾期 " + (summary == null ? 0 : summary.getOverdueWorkOrders())
                + "，质检 " + (quality == null ? 0 : quality.getInspected())
                + " 次，设备故障 " + (equipment == null ? 0 : equipment.getFault()) + " 台。";
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            if (value.contains(term)) return true;
        }
        return false;
    }

    private String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? "未填写" : String.valueOf(value);
    }

    private long number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Number n) return n.longValue();
        try {
            return value == null ? 0L : Long.parseLong(value.toString());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private BigDecimal decimal(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof BigDecimal decimal) return decimal;
        try {
            return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private String conciseError(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        if (message == null || message.isBlank()) return cause.getClass().getSimpleName();
        message = message.replaceAll("\\s+", " ").trim();
        return message.length() > 120 ? message.substring(0, 120) + "..." : message;
    }

    private record MesSnapshot(int days,
                               LocalDateTime start,
                               LocalDateTime end,
                               DashboardScreenVO screen,
                               List<Map<String, Object>> quality,
                               List<Map<String, Object>> inventory,
                               List<Map<String, Object>> repairs,
                               List<Map<String, Object>> andon,
                               List<String> sources,
                               List<String> citations,
                               AiKnowledgeGraphService.GraphContext graph,
                               DataScopeService.Scope scope,
                               String dataTimeRange,
                               String prompt) {
    }

    private record KnowledgeContext(String context, List<String> sources) {
        private static KnowledgeContext empty(String message) {
            return new KnowledgeContext(message, List.of());
        }
    }

    public record StreamContext(String prompt, List<String> sources,
                                String dataScope, String dataTimeRange,
                                List<com.bikeassembly.vo.KnowledgeGraphNodeVO> graphNodes,
                                List<com.bikeassembly.vo.KnowledgeGraphEdgeVO> graphEdges) {
        public StreamContext(String prompt, List<String> sources,
                             String dataScope, String dataTimeRange) {
            this(prompt, sources, dataScope, dataTimeRange, List.of(), List.of());
        }
    }

    private enum QueryDomain {
        PRODUCTION, QUALITY, INVENTORY, EQUIPMENT, ANDON, DOCUMENTS, MIXED
    }

    private record QueryRoute(EnumSet<QueryDomain> domains) {
        private boolean includes(QueryDomain domain) {
            return domains.contains(domain);
        }
    }
}
