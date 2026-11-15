package com.bikeassembly.service;

import com.bikeassembly.vo.KnowledgeGraphEdgeVO;
import com.bikeassembly.vo.KnowledgeGraphNodeVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

@Service
@Slf4j
public class AiKnowledgeGraphService {
    private static final List<String> ENTITY_TYPES =
            List.of("PROCESS", "ITEM", "EQUIPMENT", "DEFECT", "PARAMETER", "DOCUMENT");
    private static final List<String> RELATION_TYPES =
            List.of("USES", "HAS_STEP", "REQUIRES", "AFFECTS", "MEASURED_BY", "RELATED_TO");
    private static final Pattern JSON_FENCE = Pattern.compile("(?s)```(?:json)?\\s*(.*?)```");
    private static final long EXTRACTION_TIMEOUT_SECONDS = 45L;

    private final ObjectProvider<Driver> driverProvider;
    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;
    @Value("${spring.ai.openai.chat.options.model:}")
    private String model;
    @Value("${ai.neo4j.enabled:false}")
    private boolean enabled;

    public AiKnowledgeGraphService(ObjectProvider<Driver> driverProvider,
                                   ChatClient.Builder chatClientBuilder,
                                   ObjectMapper objectMapper) {
        this.driverProvider = driverProvider;
        this.chatClientBuilder = chatClientBuilder;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return enabled && driverProvider.getIfAvailable() != null;
    }

    public void index(String source, String scope, Long ownerId, String text) {
        Driver driver = driverProvider.getIfAvailable();
        if (!enabled || driver == null || !aiConfigured() || text == null || text.isBlank()) return;

        try {
            JsonNode root = extract(text);
            List<Map<String, Object>> entities = validateEntities(root.path("entities"), scope, ownerId);
            List<Map<String, Object>> relations = validateRelations(root.path("relations"), entities);

            String documentKey = UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
            Map<String, Object> base = new HashMap<>();
            base.put("source", source);
            base.put("scope", scope);
            base.put("ownerId", ownerId);
            base.put("documentKey", documentKey);

            try (Session session = driver.session()) {
                session.executeWrite(tx -> {
                    tx.run("MATCH ()-[r:RELATION {source: $source}]->() DELETE r", base);
                    if (entities.isEmpty()) {
                        return null;
                    }
                    tx.run("""
                            UNWIND $entities AS entity
                            MERGE (n:KnowledgeEntity {id: entity.id})
                            SET n.name = entity.name, n.type = entity.type,
                                n.scope = $scope, n.ownerId = $ownerId
                            """, mergeEntityParams(base, entities));
                    tx.run("""
                            UNWIND $relations AS relation
                            MATCH (a:KnowledgeEntity {id: relation.fromId})
                            MATCH (b:KnowledgeEntity {id: relation.toId})
                            MERGE (a)-[r:RELATION {source: $source, fromId: relation.fromId,
                                                   toId: relation.toId, type: relation.type}]->(b)
                            SET r.scope = $scope, r.ownerId = $ownerId, r.documentKey = $documentKey
                            """, mergeRelationParams(base, relations));
                    return null;
                });
            }
        } catch (Exception e) {
            log.warn("知识图谱抽取/写入失败，已跳过该文档图谱索引，source={}, error={}", source, conciseError(e));
        }
    }

    public void deleteSource(String source) {
        Driver driver = driverProvider.getIfAvailable();
        if (!enabled || driver == null || source == null) return;
        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run("""
                        MATCH ()-[r:RELATION {source: $source}]->()
                        WITH collect(startNode(r)) + collect(endNode(r)) AS endpoints
                        MATCH ()-[r:RELATION {source: $source}]->()
                        DELETE r
                        WITH endpoints
                        UNWIND endpoints AS n
                        WITH DISTINCT n
                        WHERE NOT (n)--()
                        DELETE n
                        """, Values.parameters("source", source));
                return null;
            });
        } catch (Exception e) {
            log.warn("清理知识图谱失败，source={}, error={}", source, conciseError(e));
            throw new IllegalStateException("知识图谱清理失败: " + conciseError(e), e);
        }
    }

    public GraphContext search(String query, Long ownerId) {
        Driver driver = driverProvider.getIfAvailable();
        if (!enabled || driver == null) return GraphContext.empty();
        List<String> terms = keywords(query);
        if (terms.isEmpty()) return GraphContext.empty();
        try (Session session = driver.session()) {
            var result = session.run("""
                    MATCH (a:KnowledgeEntity)-[r:RELATION]->(b:KnowledgeEntity)
                    WHERE (a.scope = 'PUBLIC' OR (a.scope = 'PRIVATE' AND a.ownerId = $ownerId))
                      AND (b.scope = 'PUBLIC' OR (b.scope = 'PRIVATE' AND b.ownerId = $ownerId))
                      AND (r.scope = 'PUBLIC' OR (r.scope = 'PRIVATE' AND r.ownerId = $ownerId))
                      AND any(term IN $terms WHERE
                          toLower(a.name) CONTAINS term OR toLower(b.name) CONTAINS term)
                    RETURN a.id AS fromId, a.name AS fromName, a.type AS fromType,
                           b.id AS toId, b.name AS toName, b.type AS toType,
                           r.type AS relationType, r.source AS source
                    LIMIT 40
                    """, Values.parameters("ownerId", ownerId == null ? -1L : ownerId, "terms", terms));
            Map<String, KnowledgeGraphNodeVO> nodes = new LinkedHashMap<>();
            List<KnowledgeGraphEdgeVO> edges = new ArrayList<>();
            List<String> sources = new ArrayList<>();
            while (result.hasNext()) {
                var row = result.next();
                String fromId = row.get("fromId").asString();
                String toId = row.get("toId").asString();
                String source = row.get("source").asString("");
                nodes.putIfAbsent(fromId, KnowledgeGraphNodeVO.builder().id(fromId)
                        .name(row.get("fromName").asString()).type(row.get("fromType").asString())
                        .source(source).build());
                nodes.putIfAbsent(toId, KnowledgeGraphNodeVO.builder().id(toId)
                        .name(row.get("toName").asString()).type(row.get("toType").asString())
                        .source(source).build());
                edges.add(KnowledgeGraphEdgeVO.builder().source(fromId).target(toId)
                        .type(row.get("relationType").asString()).documentSource(source).build());
                if (!source.isBlank() && !sources.contains(source)) sources.add(source);
            }
            String context = edges.isEmpty() ? "图谱检索：没有命中相关实体关系。"
                    : "知识图谱命中（范围已按当前用户隔离）：\n" + edges.stream()
                    .map(edge -> nodes.get(edge.getSource()).getName() + " -[" + edge.getType()
                            + "]-> " + nodes.get(edge.getTarget()).getName()
                            + "（来源：" + edge.getDocumentSource() + "）")
                    .reduce((left, right) -> left + "\n" + right).orElse("");
            return new GraphContext(context, sources, List.copyOf(nodes.values()), edges);
        } catch (Exception e) {
            log.warn("知识图谱检索失败: {}", conciseError(e));
            return GraphContext.empty();
        }
    }

    private JsonNode extract(String text) throws Exception {
        String snippet = text.substring(0, Math.min(text.length(), 2500));
        String output;
        try {
            output = CompletableFuture.supplyAsync(() -> chatClientBuilder.build().prompt()
                    .system("你是知识图谱实体关系抽取器。只输出 JSON 对象，格式为 "
                            + "{\"entities\":[{\"name\":\"...\",\"type\":\"PROCESS\"}],"
                            + "\"relations\":[{\"from\":\"实体名\",\"to\":\"实体名\",\"type\":\"USES\"}]}。"
                            + "实体 type 只能为 PROCESS、ITEM、EQUIPMENT、DEFECT、PARAMETER、DOCUMENT；"
                            + "关系 type 只能为 USES、HAS_STEP、REQUIRES、AFFECTS、MEASURED_BY、RELATED_TO。"
                            + "只抽取原文明确支持的内容，不推测。")
                    .user(snippet)
                    .call().content())
                    .get(EXTRACTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException timeout) {
            log.warn("知识图谱抽取超时（{}s），已跳过该文档", EXTRACTION_TIMEOUT_SECONDS);
            return objectMapper.createObjectNode();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return objectMapper.createObjectNode();
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            throw new Exception("知识图谱抽取调用失败: " + conciseError(cause), cause);
        }
        if (output == null || output.isBlank()) return objectMapper.createObjectNode();
        var matcher = JSON_FENCE.matcher(output.trim());
        if (matcher.find()) output = matcher.group(1);
        int start = output.indexOf('{');
        int end = output.lastIndexOf('}');
        if (start < 0 || end <= start) return objectMapper.createObjectNode();
        try {
            return objectMapper.readTree(output.substring(start, end + 1));
        } catch (Exception parseError) {
            log.warn("知识图谱抽取结果不是合法 JSON，已跳过：length={}, error={}",
                    output.length(), conciseError(parseError));
            return objectMapper.createObjectNode();
        }
    }

    private List<Map<String, Object>> validateEntities(JsonNode nodes, String scope, Long ownerId) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!nodes.isArray()) return result;
        String visibilityKey = "PUBLIC".equalsIgnoreCase(scope)
                ? "PUBLIC"
                : "PRIVATE:" + (ownerId == null ? "UNKNOWN" : ownerId);
        for (JsonNode node : nodes) {
            String name = clean(node.path("name").asText(), 120);
            String type = node.path("type").asText("").toUpperCase(Locale.ROOT);
            if (name.isBlank() || !ENTITY_TYPES.contains(type)) continue;
            String id = UUID.nameUUIDFromBytes((visibilityKey + ":" + type + ":"
                            + name.toLowerCase(Locale.ROOT))
                    .getBytes(StandardCharsets.UTF_8)).toString();
            result.add(Map.of("id", id, "name", name, "type", type));
            if (result.size() >= 100) break;
        }
        return result;
    }

    private List<Map<String, Object>> validateRelations(JsonNode relations,
                                                        List<Map<String, Object>> entities) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!relations.isArray()) return result;
        Map<String, String> idByName = new HashMap<>();
        for (Map<String, Object> entity : entities) {
            idByName.put(((String) entity.get("name")).toLowerCase(Locale.ROOT), (String) entity.get("id"));
        }
        for (JsonNode relation : relations) {
            String from = clean(relation.path("from").asText(), 120).toLowerCase(Locale.ROOT);
            String to = clean(relation.path("to").asText(), 120).toLowerCase(Locale.ROOT);
            String type = relation.path("type").asText("").toUpperCase(Locale.ROOT);
            if (!RELATION_TYPES.contains(type) || !idByName.containsKey(from) || !idByName.containsKey(to)) continue;
            result.add(Map.of("fromId", idByName.get(from), "toId", idByName.get(to), "type", type));
            if (result.size() >= 200) break;
        }
        return result;
    }

    private Map<String, Object> mergeEntityParams(Map<String, Object> base,
                                                   List<Map<String, Object>> entities) {
        Map<String, Object> values = new HashMap<>(base);
        values.put("entities", entities);
        return values;
    }

    private Map<String, Object> mergeRelationParams(Map<String, Object> base,
                                                     List<Map<String, Object>> relations) {
        Map<String, Object> values = new HashMap<>(base);
        values.put("relations", relations);
        return values;
    }

    private List<String> keywords(String query) {
        if (query == null) return List.of();
        String normalized = query.toLowerCase(Locale.ROOT)
                .replaceAll("[？?，,。.!！、：:；;\\s]+", " ")
                .trim();
        if (normalized.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        for (String token : normalized.split(" ")) {
            if (token.length() < 2) continue;
            if (token.matches("[\\p{IsHan}]+")) {
                int step = Math.max(2, Math.min(5, token.length()));
                for (int i = 0; i + 1 < token.length() && result.size() < 30; i += step) {
                    result.add(token.substring(i, Math.min(token.length(), i + step)).toLowerCase(Locale.ROOT));
                }
            } else {
                result.add(token);
            }
        }
        return result.stream().distinct().limit(30).toList();
    }

    private boolean aiConfigured() {
        return apiKey != null && !apiKey.isBlank() && !"local-disabled-key".equals(apiKey)
                && model != null && !model.isBlank();
    }

    private String clean(String value, int maxLength) {
        if (value == null) return "";
        String cleaned = value.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        return cleaned.length() > maxLength ? cleaned.substring(0, maxLength) : cleaned;
    }

    private String conciseError(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) return error.getClass().getSimpleName();
        message = message.replaceAll("\\s+", " ").trim();
        return message.length() > 180 ? message.substring(0, 180) : message;
    }

    public record GraphContext(String context, List<String> sources,
                               List<KnowledgeGraphNodeVO> nodes,
                               List<KnowledgeGraphEdgeVO> edges) {
        public static GraphContext empty() {
            return new GraphContext("知识图谱：未启用或未检索到图谱数据。",
                    List.of(), List.of(), List.of());
        }
    }
}
