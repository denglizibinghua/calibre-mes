package com.bikeassembly.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bikeassembly.entity.AiKnowledgeDocument;
import com.bikeassembly.mapper.AiKnowledgeDocumentMapper;

/**
 * 本地工艺文档的 RAG 索引器。
 *
 * <p>索引是按文件 hash 增量执行的。文件变化时先按 source 删除旧 chunk，
 * 再写入新 chunk；文档 ID 由 source 和 chunk 序号稳定生成，避免重复执行
 * 索引接口造成重复向量。没有启用 VectorStore 时仍可以扫描和切分文档，
 * 但不会调用 Embedding 或外部服务。</p>
 */
@Service
@Slf4j
public class AiDocumentIndexService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int DEFAULT_MAX_CHARS = 1200;
    private static final int DEFAULT_OVERLAP_CHARS = 160;

    private final VectorStore vectorStore;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final AiKnowledgeGraphService knowledgeGraphService;
    private final Map<String, String> knownHashes = new ConcurrentHashMap<>();
    private final Map<String, List<String>> knownDocumentIds = new ConcurrentHashMap<>();
    private final Map<String, Integer> knownChunkCounts = new ConcurrentHashMap<>();
    @Autowired(required = false)
    private AiKnowledgeDocumentMapper documentMapper;

    @Value("${ai.knowledge.path:docs}")
    private String knowledgePath;
    @Value("${ai.pgvector.dimensions:1024}")
    private int configuredDimensions;

    private volatile IndexResult lastResult = IndexResult.empty();

    @Autowired
    public AiDocumentIndexService(ObjectProvider<VectorStore> vectorStoreProvider,
                                  ObjectProvider<EmbeddingModel> embeddingModelProvider,
                                  AiKnowledgeGraphService knowledgeGraphService) {
        this.vectorStore = vectorStoreProvider.getIfAvailable();
        this.embeddingModelProvider = embeddingModelProvider;
        this.knowledgeGraphService = knowledgeGraphService;
    }

    /** Lightweight constructor retained for isolated unit tests without Spring AI beans. */
    public AiDocumentIndexService(ObjectProvider<VectorStore> vectorStoreProvider) {
        this(vectorStoreProvider, null, null);
    }

    public AiDocumentIndexService(ObjectProvider<VectorStore> vectorStoreProvider,
                                  ObjectProvider<EmbeddingModel> embeddingModelProvider) {
        this(vectorStoreProvider, embeddingModelProvider, null);
    }

    public int countDocuments() {
        return scanFiles().size();
    }

    /**
     * 保留原有测试和调用方使用的无 overlap 切分 API。
     */
    public List<DocumentChunk> loadChunks(int maxChars) {
        List<DocumentChunk> result = new ArrayList<>();
        for (Path path : scanFiles()) {
            try {
                String content = extractText(path);
                result.addAll(chunk(path, content, maxChars, 0));
            } catch (Exception e) {
                log.warn("读取 AI 文档失败: {}", path, e);
            }
        }
        return result;
    }

    /**
     * 兼容旧调用方：返回本次成功准备的 chunk 数。
     */
    public synchronized int indexDocuments(int maxChars) {
        return indexKnowledge(maxChars, DEFAULT_OVERLAP_CHARS).chunksIndexed();
    }

    public synchronized IndexResult indexKnowledge() {
        return indexKnowledge(DEFAULT_MAX_CHARS, DEFAULT_OVERLAP_CHARS);
    }

    public synchronized IndexResult indexKnowledge(int maxChars, int overlapChars) {
        int safeMaxChars = Math.max(maxChars, 200);
        int safeOverlapChars = Math.max(0, Math.min(overlapChars, safeMaxChars / 2));
        List<Path> files = scanFiles();
        int changed = 0;
        int skipped = 0;
        int deleted = 0;
        int chunksIndexed = 0;
        List<String> failures = new ArrayList<>();
        Set<String> currentSources = ConcurrentHashMap.newKeySet();
        restorePersistedManifest();

        String readinessFailure = validateVectorStoreReadiness();
        if (readinessFailure != null) {
            failures.add(readinessFailure);
            IndexResult result = new IndexResult(true, knowledgePath, files.size(), 0, 0, 0,
                    0, 0, 0, 0, failures, null, readinessFailure);
            lastResult = result;
            return result;
        }

        for (Path path : files) {
            String source = relativeSource(path);
            currentSources.add(source);
            String contentHash = null;
            try {
                String content = extractText(path);
                contentHash = sha256(content);
                if (contentHash.equals(knownHashes.get(source))
                        && (vectorStore == null || knownDocumentIds.containsKey(source))) {
                    skipped++;
                    continue;
                }

                List<DocumentChunk> chunks = chunk(path, content, safeMaxChars, safeOverlapChars);
                if (vectorStore != null) {
                    deleteSource(source);
                    final String documentHash = contentHash;
                    List<Document> documents = chunks.stream()
                            .map(chunk -> toDocument(chunk, documentHash, "PUBLIC", null, null))
                            .toList();
                    if (!documents.isEmpty()) {
                        vectorStore.add(documents);
                    }
                    knownDocumentIds.put(source, documents.stream().map(Document::getId).toList());
                }
                knownHashes.put(source, contentHash);
                knownChunkCounts.put(source, chunks.size());
                if (knowledgeGraphService != null) {
                    knowledgeGraphService.index(source, "PUBLIC", null, content);
                }
                persistDocument(source, contentHash, chunks, path, "PUBLIC", null,
                        "INDEXED", null);
                changed++;
                chunksIndexed += chunks.size();
            } catch (Exception e) {
                try {
                    deleteSource(source);
                    if (knowledgeGraphService != null) knowledgeGraphService.deleteSource(source);
                } catch (Exception cleanupError) {
                    log.warn("回滚失败 AI 文档索引失败：source={}, error={}",
                            source, conciseError(cleanupError));
                }
                failures.add(source + "：" + conciseError(e));
                persistDocument(source, contentHash == null ? "" : contentHash, List.of(), path, "PUBLIC", null,
                        "FAILED", conciseError(e));
                log.warn("索引 AI 文档失败: {}", path, e);
            }
        }

        // 删除知识目录中已经不存在的文件，避免旧文档继续参与召回。
        Set<String> persistedSources = ConcurrentHashMap.newKeySet();
        persistedSources.addAll(knownHashes.keySet());
        if (documentMapper != null) {
            try {
                documentMapper.selectList(new LambdaQueryWrapper<AiKnowledgeDocument>()
                        .eq(AiKnowledgeDocument::getScope, "PUBLIC")).forEach(document -> {
                    // Uploaded files are indexed through their own lifecycle, outside the read-only docs tree.
                    if (document.getStoragePath() == null && document.getSource() != null) {
                        persistedSources.add(document.getSource());
                    }
                });
            } catch (Exception e) {
                log.warn("读取待清理的 AI 文档清单失败：{}", e.getMessage());
            }
        }
        for (String oldSource : persistedSources) {
            if (!currentSources.contains(oldSource)) {
                try {
                    if (vectorStore != null) {
                        deleteSource(oldSource);
                    }
                    if (knowledgeGraphService != null) {
                        knowledgeGraphService.deleteSource(oldSource);
                    }
                    knownHashes.remove(oldSource);
                    knownDocumentIds.remove(oldSource);
                    knownChunkCounts.remove(oldSource);
                    if (documentMapper != null) {
                        documentMapper.delete(new LambdaQueryWrapper<AiKnowledgeDocument>()
                                .eq(AiKnowledgeDocument::getSource, oldSource));
                    }
                    deleted++;
            } catch (Throwable e) {
                    failures.add(oldSource + "：删除旧索引失败：" + conciseError(e));
                    log.warn("删除已移除 AI 文档的旧索引失败: {}", oldSource, e);
                }
            }
        }

        IndexResult result = new IndexResult(
                vectorStore != null,
                knowledgePath,
                files.size(),
                changed,
                skipped,
                deleted,
                chunksIndexed,
                knownChunkCounts.values().stream().mapToInt(Integer::intValue).sum(),
                knownHashes.size(),
                failures.size(),
                failures,
                LocalDateTime.now(BUSINESS_ZONE),
                vectorStore == null
                        ? "VectorStore 未启用，仅完成文档扫描；配置 AI_PGVECTOR_ENABLED=true 后才会生成向量。"
                        : failures.isEmpty() ? "索引完成。" : "索引完成，但有文件失败。");
        lastResult = result;
        return result;
    }

    public IndexResult status() {
        IndexResult current = lastResult;
        if (documentMapper != null) {
            try {
                List<AiKnowledgeDocument> documents = documentMapper.selectList(
                        new LambdaQueryWrapper<AiKnowledgeDocument>()
                                .eq(AiKnowledgeDocument::getScope, "PUBLIC"));
                int indexedFiles = (int) documents.stream()
                        .filter(document -> "INDEXED".equals(document.getIndexStatus()))
                        .count();
                int failedFiles = (int) documents.stream()
                        .filter(document -> "FAILED".equals(document.getIndexStatus()))
                        .count();
                int chunksTotal = documents.stream()
                        .filter(document -> "INDEXED".equals(document.getIndexStatus()))
                        .mapToInt(document -> document.getChunkCount() == null ? 0 : document.getChunkCount())
                        .sum();
                List<String> failures = documents.stream()
                        .filter(document -> "FAILED".equals(document.getIndexStatus()))
                        .map(document -> document.getSource() + "："
                                + (document.getErrorMessage() == null ? "索引失败" : document.getErrorMessage()))
                        .toList();
                LocalDateTime indexedAt = documents.stream()
                        .map(AiKnowledgeDocument::getIndexedAt)
                        .filter(java.util.Objects::nonNull)
                        .max(LocalDateTime::compareTo)
                        .orElse(current.indexedAt());
                if (!documents.isEmpty() || current.indexedAt() != null) {
                    return new IndexResult(vectorStore != null, knowledgePath, countDocuments(),
                            0, indexedFiles, 0, 0, chunksTotal, indexedFiles, failedFiles,
                            failures, indexedAt,
                            vectorStore == null
                                    ? "VectorStore 未配置（NOT_CONFIGURED），只保留 MySQL 文档清单。"
                                    : failures.isEmpty() ? "已从持久化清单恢复索引状态。" : "索引清单中存在失败文档。");
                }
            } catch (Exception e) {
                log.warn("读取 AI 文档索引状态失败：{}", e.getMessage());
            }
        }
        int scanned = countDocuments();
        if (current.indexedAt() == null) {
            return new IndexResult(vectorStore != null, knowledgePath, scanned, 0, 0, 0,
                    0, 0, 0, 0, List.of(), current.indexedAt(),
                    "尚未执行索引，请调用 POST /admin/ai/knowledge/index。");
        }
        return current;
    }

    /**
     * 索引上传文件。文件元数据先由 AiKnowledgeFileService 创建，随后在这里
     * 完成 Tika 提取、向量写入和 GraphRAG 抽取。原始文件始终只从 storagePath 读取。
     */
    public synchronized FileIndexResult indexStoredDocument(AiKnowledgeDocument document) {
        if (document == null || document.getStoragePath() == null) {
            return FileIndexResult.failed("文件元数据或存储路径为空");
        }
        Path path = Path.of(document.getStoragePath()).toAbsolutePath().normalize();
        try {
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException("文件不存在");
            }
            if (vectorStore == null) {
                throw new IllegalStateException("PGVector 未启用，文件已保存但尚未建立可检索索引");
            }
            String text = extractText(path);
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("文件未提取到可索引文本（扫描版 PDF 不支持 OCR）");
            }
            String source = document.getSource();
            String hash = sha256(text);
            List<DocumentChunk> chunks = chunk(source, document.getOriginalName(), document.getLastModifiedAt(),
                    text, DEFAULT_MAX_CHARS, DEFAULT_OVERLAP_CHARS);
            if (vectorStore != null) {
                deleteSource(source);
                List<Document> vectors = chunks.stream()
                        .map(chunk -> toDocument(chunk, hash, document.getScope(), document.getOwnerId(),
                                document.getDocumentId()))
                        .toList();
                if (!vectors.isEmpty()) vectorStore.add(vectors);
                knownDocumentIds.put(source, vectors.stream().map(Document::getId).toList());
            }
            if (knowledgeGraphService != null) {
                knowledgeGraphService.deleteSource(source);
                knowledgeGraphService.index(source, document.getScope(), document.getOwnerId(), text);
            }
            document.setContentHash(hash);
            document.setChunkCount(chunks.size());
            document.setIndexStatus("INDEXED");
            document.setErrorMessage(null);
            document.setIndexedAt(LocalDateTime.now(BUSINESS_ZONE));
            document.setLastModifiedAt(Files.getLastModifiedTime(path).toInstant()
                    .atZone(BUSINESS_ZONE).toLocalDateTime());
            if (documentMapper != null) {
                if (document.getDocumentId() == null) documentMapper.insert(document);
                else documentMapper.updateById(document);
            }
            return new FileIndexResult(true, chunks.size(), null);
        } catch (Exception e) {
            try {
                deleteDocumentIndex(document.getSource());
            } catch (Exception cleanupError) {
                log.warn("清理失败文件的部分 AI 索引失败：source={}, error={}",
                        document.getSource(), conciseError(cleanupError));
            }
            document.setIndexStatus("FAILED");
            document.setErrorMessage(conciseError(e));
            if (documentMapper != null) {
                if (document.getDocumentId() == null) documentMapper.insert(document);
                else documentMapper.updateById(document);
            }
            return FileIndexResult.failed(conciseError(e));
        }
    }

    public void deleteDocumentIndex(String source) {
        if (source == null || source.isBlank()) return;
        if (vectorStore != null) deleteSource(source);
        if (knowledgeGraphService != null) knowledgeGraphService.deleteSource(source);
        knownHashes.remove(source);
        knownDocumentIds.remove(source);
        knownChunkCounts.remove(source);
    }

    public boolean vectorStoreEnabled() {
        return vectorStore != null;
    }

    private String validateVectorStoreReadiness() {
        if (vectorStore == null || embeddingModelProvider == null) {
            return null;
        }
        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
        if (embeddingModel == null) {
            return "PGVector 未配置 Embedding 模型（NOT_CONFIGURED）";
        }
        if (configuredDimensions <= 0) {
            return "AI_PGVECTOR_DIMENSIONS 必须大于 0";
        }
        try {
            float[] probe = embeddingModel.embed("MES embedding readiness probe");
            if (probe == null || probe.length == 0) {
                return "Embedding 探测返回空向量";
            }
            if (probe.length != configuredDimensions) {
                return "Embedding 维度不匹配：实际 " + probe.length
                        + "，配置 " + configuredDimensions;
            }
            return null;
        } catch (Exception e) {
            return "Embedding 探测失败：" + conciseError(e);
        }
    }

    private Document toDocument(DocumentChunk chunk, String contentHash) {
        return toDocument(chunk, contentHash, "PUBLIC", null, null);
    }

    private Document toDocument(DocumentChunk chunk, String contentHash,
                                String scope, Long ownerId, Long storedDocumentId) {
        String source = chunk.source();
        String vectorDocumentId = UUID.nameUUIDFromBytes(
                (source + "#" + chunk.index()).getBytes(StandardCharsets.UTF_8)).toString();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", source);
        metadata.put("relativeSource", source);
        metadata.put("chunk", chunk.index());
        metadata.put("contentHash", contentHash);
        metadata.put("lastModified", chunk.lastModified().toString());
        metadata.put("indexedAt", LocalDateTime.now(BUSINESS_ZONE).toString());
        metadata.put("title", chunk.title());
        metadata.put("scope", scope == null ? "PUBLIC" : scope);
        if (ownerId != null) metadata.put("ownerId", ownerId);
        if (storedDocumentId != null) metadata.put("documentId", storedDocumentId);
        return new Document(vectorDocumentId, chunk.content(), metadata);
    }

    private void restorePersistedManifest() {
        if (documentMapper == null) return;
        try {
            documentMapper.selectList(new LambdaQueryWrapper<AiKnowledgeDocument>()
                    .eq(AiKnowledgeDocument::getScope, "PUBLIC")).forEach(document -> {
                    if (document.getStoragePath() == null
                            && "INDEXED".equals(document.getIndexStatus())
                        && document.getSource() != null && document.getContentHash() != null) {
                    knownHashes.putIfAbsent(document.getSource(), document.getContentHash());
                    int chunkCount = document.getChunkCount() == null ? 0 : document.getChunkCount();
                    knownChunkCounts.putIfAbsent(document.getSource(), chunkCount);
                    List<String> documentIds = new ArrayList<>(chunkCount);
                    for (int index = 0; index < chunkCount; index++) {
                        documentIds.add(UUID.nameUUIDFromBytes(
                                (document.getSource() + "#" + index)
                                        .getBytes(StandardCharsets.UTF_8)).toString());
                    }
                    knownDocumentIds.putIfAbsent(document.getSource(), documentIds);
                }
            });
        } catch (Exception e) {
            log.warn("恢复 AI 文档索引清单失败: {}", e.getMessage());
        }
    }

    private void persistDocument(String source, String hash, List<DocumentChunk> chunks,
                                 Path path, String scope, Long ownerId,
                                 String status, String error) {
        if (documentMapper == null) return;
        try {
            AiKnowledgeDocument document = documentMapper.selectOne(
                    new LambdaQueryWrapper<AiKnowledgeDocument>()
                            .eq(AiKnowledgeDocument::getSource, source)
                            .last("LIMIT 1"));
            if (document == null) {
                document = new AiKnowledgeDocument();
                document.setSource(source);
            }
            if (document.getScope() == null) document.setScope(scope == null ? "PUBLIC" : scope);
            if (document.getOwnerId() == null) document.setOwnerId(ownerId);
            document.setContentHash(hash);
            document.setChunkCount(chunks == null ? 0 : chunks.size());
            document.setIndexStatus(status);
            document.setErrorMessage(error);
            try {
                document.setLastModifiedAt(Files.getLastModifiedTime(path).toInstant()
                        .atZone(BUSINESS_ZONE).toLocalDateTime());
            } catch (Exception ignored) {
                document.setLastModifiedAt(LocalDateTime.now(BUSINESS_ZONE));
            }
            document.setIndexedAt("INDEXED".equals(status) ? LocalDateTime.now(BUSINESS_ZONE) : null);
            if (document.getDocumentId() == null) documentMapper.insert(document);
            else documentMapper.updateById(document);
        } catch (Exception e) {
            log.warn("保存 AI 文档索引清单失败：source={}, error={}", source, e.getMessage());
        }
    }

    private void deleteSource(String source) {
        if (vectorStore == null) return;
        // PgVectorStore 支持按 metadata 过滤删除；同时用已知 ID 做兼容性兜底。
        try {
            vectorStore.delete(new FilterExpressionBuilder().eq("source", source).build());
        } catch (RuntimeException filterFailure) {
            List<String> ids = knownDocumentIds.getOrDefault(source, List.of());
            if (!ids.isEmpty()) {
                vectorStore.delete(ids);
                return;
            }
            throw filterFailure;
        }
    }

    private List<Path> scanFiles() {
        Path root = Path.of(knowledgePath).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) return List.of();
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> !isIgnored(path))
                    .filter(this::isKnowledgeFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        } catch (Exception e) {
            log.warn("扫描 AI 文档目录失败: {}", root, e);
            return List.of();
        }
    }

    private boolean isKnowledgeFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".md") || name.endsWith(".txt")
                || name.endsWith(".pdf") || name.endsWith(".docx")
                || name.endsWith(".xlsx") || name.endsWith(".pptx");
    }

    private boolean isIgnored(Path path) {
        for (Path part : path) {
            String name = part.toString();
            if ("target".equalsIgnoreCase(name) || "node_modules".equalsIgnoreCase(name)
                    || ".git".equalsIgnoreCase(name) || "dist".equalsIgnoreCase(name)
                    || "build".equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private String relativeSource(Path path) {
        Path root = Path.of(knowledgePath).toAbsolutePath().normalize();
        return root.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private List<DocumentChunk> chunk(Path path, String content, int maxChars, int overlapChars) {
        String source = relativeSource(path);
        String title = extractTitle(content, path.getFileName().toString());
        LocalDateTime modified;
        try {
            modified = Files.getLastModifiedTime(path).toInstant().atZone(BUSINESS_ZONE).toLocalDateTime();
        } catch (Exception e) {
            modified = LocalDateTime.now(BUSINESS_ZONE);
        }
        return chunk(source, title, modified, content, maxChars, overlapChars);
    }

    private List<DocumentChunk> chunk(String source, String title, LocalDateTime modified,
                                      String content, int maxChars, int overlapChars) {
        List<DocumentChunk> result = new ArrayList<>();
        if (content == null || content.isEmpty()) return result;
        if (modified == null) modified = LocalDateTime.now(BUSINESS_ZONE);
        int start = 0;
        int index = 0;
        while (start < content.length()) {
            int end = Math.min(content.length(), start + maxChars);
            if (end < content.length()) {
                int newline = content.lastIndexOf('\n', end);
                if (newline > start + maxChars / 2) end = newline;
            }
            String text = content.substring(start, end).trim();
            if (!text.isEmpty()) {
                result.add(new DocumentChunk(source, index++, title, modified, text));
            }
            if (end >= content.length()) break;
            int nextStart = Math.max(end - overlapChars, start + 1);
            start = nextStart;
        }
        return result;
    }

    private String extractText(Path path) throws Exception {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".md") || name.endsWith(".txt")) {
            return Files.readString(path);
        }
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, path.getFileName().toString());
        BodyContentHandler handler = new BodyContentHandler(2_000_000);
        AutoDetectParser parser = new AutoDetectParser();
        try (InputStream input = Files.newInputStream(path)) {
            parser.parse(input, handler, metadata, new ParseContext());
        }
        return handler.toString();
    }

    private String extractTitle(String content, String fallback) {
        for (String line : content.split("\\R", 20)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                return trimmed.replaceFirst("^#+\\s*", "");
            }
        }
        return fallback;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("计算文档 hash 失败", e);
        }
    }

    private String conciseError(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) return error.getClass().getSimpleName();
        message = message.replaceAll("\\s+", " ").trim();
        return message.length() > 160 ? message.substring(0, 160) + "..." : message;
    }

    public record DocumentChunk(String source, int index, String title,
                                LocalDateTime lastModified, String content) {
        public DocumentChunk(String source, int index, String content) {
            this(source, index, source, LocalDateTime.MIN, content);
        }
    }

    public record FileIndexResult(boolean indexed, int chunks, String error) {
        public static FileIndexResult failed(String error) {
            return new FileIndexResult(false, 0, error);
        }
    }

    public record IndexResult(boolean vectorStoreEnabled,
                              String knowledgePath,
                              int scannedFiles,
                              int filesChanged,
                              int filesSkipped,
                              int filesDeleted,
                              int chunksIndexed,
                              int chunksTotal,
                              int indexedFiles,
                              int failedFiles,
                              List<String> failures,
                              LocalDateTime indexedAt,
                              String message) {
        private static IndexResult empty() {
            return new IndexResult(false, "", 0, 0, 0, 0, 0, 0, 0, 0, List.of(), null,
                    "尚未执行索引。");
        }
    }
}
