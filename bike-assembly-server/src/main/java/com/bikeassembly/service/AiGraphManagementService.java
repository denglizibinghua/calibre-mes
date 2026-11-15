package com.bikeassembly.service;

import com.bikeassembly.exception.BaseException;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.vo.GraphEdgeVO;
import com.bikeassembly.vo.GraphNodeVO;
import com.bikeassembly.vo.GraphOverviewVO;
import com.bikeassembly.vo.TypeCountVO;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * 知识图谱管理服务：概览、节点/关系查询与删除。
 *
 * <p>所有查询都按可见性过滤：PUBLIC，或 PRIVATE 且 ownerId 等于当前用户。
 * 即使当前用户的数据范围为 ALL/DEPT，也不会放开其他用户的 PRIVATE 图谱数据。</p>
 */
@Service
@Slf4j
public class AiGraphManagementService {
    private static final List<String> ENTITY_TYPES =
            List.of("PROCESS", "ITEM", "EQUIPMENT", "DEFECT", "PARAMETER", "DOCUMENT");
    private static final List<String> RELATION_TYPES =
            List.of("USES", "HAS_STEP", "REQUIRES", "AFFECTS", "MEASURED_BY", "RELATED_TO");
    private static final long DEFAULT_SIZE = 50L;
    private static final long MAX_SIZE = 200L;

    private static final String OVERVIEW_COUNTS = """
            MATCH (n:KnowledgeEntity)
            WHERE (n.scope = 'PUBLIC' OR (n.scope = 'PRIVATE' AND n.ownerId = $ownerId))
            RETURN count(n) AS nodeCount,
                   sum(CASE WHEN n.scope = 'PUBLIC' THEN 1 ELSE 0 END) AS publicCount,
                   sum(CASE WHEN n.scope = 'PRIVATE' THEN 1 ELSE 0 END) AS privateCount
            """;

    private static final String NODE_TYPES = """
            MATCH (n:KnowledgeEntity)
            WHERE (n.scope = 'PUBLIC' OR (n.scope = 'PRIVATE' AND n.ownerId = $ownerId))
            RETURN n.type AS type, count(n) AS cnt
            ORDER BY cnt DESC, type ASC
            """;

    private static final String EDGE_COUNT = """
            MATCH (a:KnowledgeEntity)-[r:RELATION]->(b:KnowledgeEntity)
            WHERE (a.scope = 'PUBLIC' OR (a.scope = 'PRIVATE' AND a.ownerId = $ownerId))
              AND (b.scope = 'PUBLIC' OR (b.scope = 'PRIVATE' AND b.ownerId = $ownerId))
              AND (r.scope = 'PUBLIC' OR (r.scope = 'PRIVATE' AND r.ownerId = $ownerId))
            RETURN count(r) AS edgeCount
            """;

    private static final String RELATION_TYPE_COUNTS = """
            MATCH (a:KnowledgeEntity)-[r:RELATION]->(b:KnowledgeEntity)
            WHERE (a.scope = 'PUBLIC' OR (a.scope = 'PRIVATE' AND a.ownerId = $ownerId))
              AND (b.scope = 'PUBLIC' OR (b.scope = 'PRIVATE' AND b.ownerId = $ownerId))
              AND (r.scope = 'PUBLIC' OR (r.scope = 'PRIVATE' AND r.ownerId = $ownerId))
            RETURN r.type AS type, count(r) AS cnt
            ORDER BY cnt DESC, type ASC
            """;

    private static final String NODE_TOTAL = """
            MATCH (n:KnowledgeEntity)
            WHERE (n.scope = 'PUBLIC' OR (n.scope = 'PRIVATE' AND n.ownerId = $ownerId))
              AND ($type = '' OR n.type = $type)
              AND ($keyword = '' OR toLower(n.name) CONTAINS $keyword)
            RETURN count(n) AS total
            """;

    private static final String NODE_PAGE = """
            MATCH (n:KnowledgeEntity)
            WHERE (n.scope = 'PUBLIC' OR (n.scope = 'PRIVATE' AND n.ownerId = $ownerId))
              AND ($type = '' OR n.type = $type)
              AND ($keyword = '' OR toLower(n.name) CONTAINS $keyword)
            RETURN n.id AS id, n.name AS name, n.type AS type, n.scope AS scope,
                   n.ownerId AS ownerId,
                   size([(n)-[r:RELATION]-(m:KnowledgeEntity)
                         WHERE (r.scope = 'PUBLIC' OR (r.scope = 'PRIVATE' AND r.ownerId = $ownerId))
                           AND (m.scope = 'PUBLIC' OR (m.scope = 'PRIVATE' AND m.ownerId = $ownerId))
                         | r]) AS relationCount
            ORDER BY name ASC, id ASC
            SKIP $skip LIMIT $limit
            """;

    private static final String EDGE_TOTAL = """
            MATCH (a:KnowledgeEntity)-[r:RELATION]->(b:KnowledgeEntity)
            WHERE (a.scope = 'PUBLIC' OR (a.scope = 'PRIVATE' AND a.ownerId = $ownerId))
              AND (b.scope = 'PUBLIC' OR (b.scope = 'PRIVATE' AND b.ownerId = $ownerId))
              AND (r.scope = 'PUBLIC' OR (r.scope = 'PRIVATE' AND r.ownerId = $ownerId))
              AND ($nodeId = '' OR a.id = $nodeId OR b.id = $nodeId)
              AND ($type = '' OR r.type = $type)
            RETURN count(r) AS total
            """;

    private static final String EDGE_PAGE = """
            MATCH (a:KnowledgeEntity)-[r:RELATION]->(b:KnowledgeEntity)
            WHERE (a.scope = 'PUBLIC' OR (a.scope = 'PRIVATE' AND a.ownerId = $ownerId))
              AND (b.scope = 'PUBLIC' OR (b.scope = 'PRIVATE' AND b.ownerId = $ownerId))
              AND (r.scope = 'PUBLIC' OR (r.scope = 'PRIVATE' AND r.ownerId = $ownerId))
              AND ($nodeId = '' OR a.id = $nodeId OR b.id = $nodeId)
              AND ($type = '' OR r.type = $type)
            RETURN a.id AS fromId, a.name AS fromName, a.type AS fromType,
                   b.id AS toId, b.name AS toName, b.type AS toType,
                   r.type AS type, r.source AS source, r.scope AS scope, r.ownerId AS ownerId
            ORDER BY fromId ASC, toId ASC, type ASC
            SKIP $skip LIMIT $limit
            """;

    private static final String DELETE_NODE = """
            MATCH (n:KnowledgeEntity {id: $nodeId})
            WHERE (n.scope = 'PUBLIC' OR (n.scope = 'PRIVATE' AND n.ownerId = $ownerId))
            WITH n, 1 AS deleted
            DETACH DELETE n
            RETURN deleted
            """;

    private static final String DELETE_EDGE = """
            MATCH (a:KnowledgeEntity {id: $fromId})-[r:RELATION {type: $type}]->(b:KnowledgeEntity {id: $toId})
            WHERE (a.scope = 'PUBLIC' OR (a.scope = 'PRIVATE' AND a.ownerId = $ownerId))
              AND (b.scope = 'PUBLIC' OR (b.scope = 'PRIVATE' AND b.ownerId = $ownerId))
              AND (r.scope = 'PUBLIC' OR (r.scope = 'PRIVATE' AND r.ownerId = $ownerId))
            WITH r, 1 AS deleted
            DELETE r
            RETURN deleted
            """;

    private final ObjectProvider<Driver> driverProvider;

    @org.springframework.beans.factory.annotation.Value("${ai.neo4j.enabled:false}")
    private boolean enabled;

    public AiGraphManagementService(ObjectProvider<Driver> driverProvider) {
        this.driverProvider = driverProvider;
    }

    public boolean isEnabled() {
        return enabled && driverProvider.getIfAvailable() != null;
    }

    public GraphOverviewVO overview(Long ownerId) {
        return withSession(session -> {
            var parameters = Values.parameters("ownerId", ownerId(ownerId));
            var counts = session.run(OVERVIEW_COUNTS, parameters).single();
            long edgeCount = session.run(EDGE_COUNT, parameters).single().get("edgeCount").asLong();
            return GraphOverviewVO.builder()
                    .nodeCount(counts.get("nodeCount").asLong())
                    .edgeCount(edgeCount)
                    .nodeTypes(typeCounts(session, NODE_TYPES, ownerId))
                    .relationTypes(typeCounts(session, RELATION_TYPE_COUNTS, ownerId))
                    .publicCount(counts.get("publicCount").asLong())
                    .privateCount(counts.get("privateCount").asLong())
                    .build();
        });
    }

    public PageResult<GraphNodeVO> listNodes(Long ownerId, String type, String keyword,
                                             Long page, Long size) {
        String safeType = normalizeType(type, ENTITY_TYPES, "实体类型");
        String safeKeyword = normalizeKeyword(keyword);
        long safePage = normalizePage(page);
        long safeSize = normalizeSize(size);
        long skip = (safePage - 1) * safeSize;
        return withSession(session -> {
            var parameters = Values.parameters(
                    "ownerId", ownerId(ownerId),
                    "type", safeType,
                    "keyword", safeKeyword,
                    "skip", skip,
                    "limit", safeSize);
            long total = session.run(NODE_TOTAL, parameters).single().get("total").asLong();
            List<GraphNodeVO> records = new ArrayList<>();
            if (total > 0) {
                var result = session.run(NODE_PAGE, parameters);
                while (result.hasNext()) {
                    var row = result.next();
                    records.add(GraphNodeVO.builder()
                            .id(row.get("id").asString(""))
                            .name(row.get("name").asString(""))
                            .type(row.get("type").asString(""))
                            .scope(row.get("scope").asString(""))
                            .ownerId(nullableLong(row.get("ownerId")))
                            .relationCount(row.get("relationCount").asLong())
                            .build());
                }
            }
            return PageResult.of(records, total, safePage, safeSize);
        });
    }

    public PageResult<GraphEdgeVO> listEdges(Long ownerId, String nodeId, String type,
                                             Long page, Long size) {
        String safeNodeId = nodeId == null ? "" : nodeId.trim();
        String safeType = normalizeType(type, RELATION_TYPES, "关系类型");
        long safePage = normalizePage(page);
        long safeSize = normalizeSize(size);
        long skip = (safePage - 1) * safeSize;
        return withSession(session -> {
            var parameters = Values.parameters(
                    "ownerId", ownerId(ownerId),
                    "nodeId", safeNodeId,
                    "type", safeType,
                    "skip", skip,
                    "limit", safeSize);
            long total = session.run(EDGE_TOTAL, parameters).single().get("total").asLong();
            List<GraphEdgeVO> records = new ArrayList<>();
            if (total > 0) {
                var result = session.run(EDGE_PAGE, parameters);
                while (result.hasNext()) {
                    var row = result.next();
                    records.add(GraphEdgeVO.builder()
                            .fromId(row.get("fromId").asString(""))
                            .fromName(row.get("fromName").asString(""))
                            .fromType(row.get("fromType").asString(""))
                            .toId(row.get("toId").asString(""))
                            .toName(row.get("toName").asString(""))
                            .toType(row.get("toType").asString(""))
                            .type(row.get("type").asString(""))
                            .source(row.get("source").asString(""))
                            .scope(row.get("scope").asString(""))
                            .ownerId(nullableLong(row.get("ownerId")))
                            .build());
                }
            }
            return PageResult.of(records, total, safePage, safeSize);
        });
    }

    public void deleteNode(Long ownerId, String nodeId) {
        String safeNodeId = requireText(nodeId, "节点 ID");
        boolean deleted = withSession(session -> session.executeWrite(tx -> tx.run(DELETE_NODE,
                Values.parameters("ownerId", ownerId(ownerId), "nodeId", safeNodeId)).hasNext()));
        if (!deleted) {
            throw new BaseException("节点不存在或无权访问");
        }
    }

    public void deleteEdge(Long ownerId, String fromId, String toId, String type) {
        String safeFromId = requireText(fromId, "起点 ID");
        String safeToId = requireText(toId, "终点 ID");
        String safeType = normalizeType(type, RELATION_TYPES, "关系类型");
        if (safeType.isEmpty()) {
            throw new BaseException("关系类型不能为空");
        }
        boolean deleted = withSession(session -> session.executeWrite(tx -> tx.run(DELETE_EDGE,
                Values.parameters(
                        "ownerId", ownerId(ownerId),
                        "fromId", safeFromId,
                        "toId", safeToId,
                        "type", safeType)).hasNext()));
        if (!deleted) {
            throw new BaseException("关系不存在或无权访问");
        }
    }

    private <T> T withSession(Function<Session, T> action) {
        Driver driver = driverProvider.getIfAvailable();
        if (!enabled || driver == null) {
            throw new BaseException("知识图谱未配置（NOT_CONFIGURED）");
        }
        try (Session session = driver.session()) {
            return action.apply(session);
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.warn("知识图谱操作失败: {}", conciseError(e));
            throw new BaseException("知识图谱操作失败: " + conciseError(e));
        }
    }

    private List<TypeCountVO> typeCounts(Session session, String query, Long ownerId) {
        List<TypeCountVO> result = new ArrayList<>();
        var records = session.run(query, Values.parameters("ownerId", ownerId(ownerId)));
        while (records.hasNext()) {
            var row = records.next();
            result.add(TypeCountVO.builder()
                    .type(row.get("type").asString(""))
                    .count(row.get("cnt").asLong())
                    .build());
        }
        return result;
    }

    private String normalizeType(String type, List<String> allowed, String label) {
        if (type == null || type.isBlank()) {
            return "";
        }
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new BaseException(label + "不合法：" + type);
        }
        return normalized;
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return "";
        }
        String cleaned = keyword.replaceAll("[\\p{Cntrl}]", " ")
                .replaceAll("\\s+", " ").trim();
        if (cleaned.length() > 120) {
            cleaned = cleaned.substring(0, 120);
        }
        return cleaned.toLowerCase(Locale.ROOT);
    }

    private long normalizePage(Long page) {
        return page == null || page < 1 ? 1L : page;
    }

    private long normalizeSize(Long size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new BaseException(label + "不能为空");
        }
        return value.trim();
    }

    private long ownerId(Long ownerId) {
        return ownerId == null ? -1L : ownerId;
    }

    private Long nullableLong(Value value) {
        return value == null || value.isNull() ? null : value.asLong();
    }

    private String conciseError(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        message = message.replaceAll("\\s+", " ").trim();
        return message.length() > 180 ? message.substring(0, 180) : message;
    }
}
