package com.bikeassembly.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bikeassembly.entity.AiKnowledgeDocument;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.mapper.AiKnowledgeDocumentMapper;
import com.bikeassembly.vo.AiKnowledgeFileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 私有/公共知识文件的所有权和生命周期边界。
 */
@Service
@RequiredArgsConstructor
public class AiKnowledgeFileService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final List<String> ALLOWED_EXTENSIONS =
            List.of("md", "txt", "pdf", "docx", "xlsx", "pptx");

    private final AiKnowledgeDocumentMapper documentMapper;
    private final AiDocumentIndexService indexService;

    @Value("${ai.knowledge.public-upload-path:${AI_PUBLIC_KNOWLEDGE_UPLOAD_PATH:data/ai/public-uploads}}")
    private String publicUploadPath;
    @Value("${ai.knowledge.private-path:${AI_PRIVATE_KNOWLEDGE_PATH:data/ai/private}}")
    private String privatePath;
    @Value("${ai.knowledge.max-file-bytes:52428800}")
    private long maxFileBytes;

    public List<AiKnowledgeFileVO> list(Long userId, String scope) {
        String requested = scope == null || scope.isBlank() ? null : normalizeScope(scope);
        List<AiKnowledgeDocument> documents = documentMapper.selectList(
                new LambdaQueryWrapper<AiKnowledgeDocument>()
                        .and(wrapper -> wrapper.eq(AiKnowledgeDocument::getScope, "PUBLIC")
                                .or(inner -> inner.eq(AiKnowledgeDocument::getScope, "PRIVATE")
                                        .eq(AiKnowledgeDocument::getOwnerId, userId)))
                        .eq(requested != null, AiKnowledgeDocument::getScope, requested)
                        .orderByDesc(AiKnowledgeDocument::getUpdatedAt)
                        .orderByDesc(AiKnowledgeDocument::getDocumentId));
        return documents.stream().map(this::toVO).toList();
    }

    public AiKnowledgeFileVO upload(Long userId, MultipartFile file, String scope,
                                    boolean canManagePublic) {
        String normalizedScope = normalizeScope(scope);
        if ("PUBLIC".equals(normalizedScope) && !canManagePublic) {
            throw new BaseException("只有知识库管理员可以上传公共资料");
        }
        if ("PRIVATE".equals(normalizedScope) && userId == null) {
            throw new BaseException("未获取当前用户");
        }
        validateFile(file);
        String originalName = cleanName(file.getOriginalFilename());
        String extension = extension(originalName);
        String generatedName = UUID.randomUUID() + "." + extension;
        Path root = "PUBLIC".equals(normalizedScope)
                ? Path.of(publicUploadPath).toAbsolutePath().normalize()
                : Path.of(privatePath).toAbsolutePath().normalize().resolve(String.valueOf(userId));
        ensureDirectory(root);
        Path target = root.resolve(generatedName).normalize();
        if (!target.startsWith(root)) throw new BaseException("非法文件名");
        try {
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            AiKnowledgeDocument document = new AiKnowledgeDocument();
            document.setSource(("PUBLIC".equals(normalizedScope) ? "uploads/" : "private/" + userId + "/")
                    + generatedName);
            document.setScope(normalizedScope);
            document.setOwnerId("PRIVATE".equals(normalizedScope) ? userId : null);
            document.setOriginalName(originalName);
            document.setMimeType(file.getContentType());
            document.setFileSize(file.getSize());
            document.setStoragePath(target.toString());
            document.setContentHash("");
            document.setChunkCount(0);
            document.setIndexStatus("PENDING");
            document.setCreatedAt(LocalDateTime.now(ZONE));
            document.setUpdatedAt(LocalDateTime.now(ZONE));
            try {
                documentMapper.insert(document);
            } catch (RuntimeException e) {
                deleteQuietly(target);
                throw e;
            }
            AiDocumentIndexService.FileIndexResult result = indexService.indexStoredDocument(document);
            if (!result.indexed() && document.getErrorMessage() == null) {
                document.setIndexStatus("FAILED");
                document.setErrorMessage(result.error());
                documentMapper.updateById(document);
            }
            return toVO(document);
        } catch (IOException e) {
            deleteQuietly(target);
            throw new BaseException("保存知识文件失败");
        }
    }

    public AiKnowledgeFileVO replace(Long userId, Long documentId, MultipartFile file,
                                     boolean canManagePublic) {
        AiKnowledgeDocument existing = requireAccessible(userId, documentId, true, canManagePublic);
        validateFile(file);
        Path oldStorage = existing.getStoragePath() == null ? null : storedPath(existing);
        String originalName = cleanName(file.getOriginalFilename());
        String extension = extension(originalName);
        Path root = "PUBLIC".equals(existing.getScope())
                ? Path.of(publicUploadPath).toAbsolutePath().normalize()
                : Path.of(privatePath).toAbsolutePath().normalize().resolve(String.valueOf(userId));
        ensureDirectory(root);
        Path target = root.resolve(UUID.randomUUID() + "." + extension).normalize();
        try {
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            existing.setOriginalName(originalName);
            existing.setMimeType(file.getContentType());
            existing.setFileSize(file.getSize());
            existing.setStoragePath(target.toString());
            existing.setContentHash("");
            existing.setChunkCount(0);
            existing.setIndexStatus("PENDING");
            existing.setErrorMessage(null);
            existing.setUpdatedAt(LocalDateTime.now(ZONE));
            documentMapper.updateById(existing);
            indexService.deleteDocumentIndex(existing.getSource());
            AiDocumentIndexService.FileIndexResult result = indexService.indexStoredDocument(existing);
            if (!result.indexed()) documentMapper.updateById(existing);
            if (oldStorage != null && !oldStorage.equals(target)) deleteQuietly(oldStorage);
            return toVO(existing);
        } catch (IOException e) {
            deleteQuietly(target);
            throw new BaseException("更新知识文件失败");
        }
    }

    public void delete(Long userId, Long documentId, boolean canManagePublic) {
        AiKnowledgeDocument document = requireAccessible(userId, documentId, true, canManagePublic);
        indexService.deleteDocumentIndex(document.getSource());
        deleteStorage(document);
        documentMapper.deleteById(documentId);
    }

    public DownloadedFile download(Long userId, Long documentId) {
        AiKnowledgeDocument document = requireAccessible(userId, documentId, false, false);
        try {
            Path path = storedPath(document);
            if (!Files.isRegularFile(path)) throw new BaseException("文件不存在");
            Resource resource = new UrlResource(path.toUri());
            return new DownloadedFile(resource, document.getOriginalName(), document.getMimeType());
        } catch (IOException e) {
            throw new BaseException("读取知识文件失败");
        }
    }

    private AiKnowledgeDocument requireAccessible(Long userId, Long documentId,
                                                   boolean mutate, boolean canManagePublic) {
        AiKnowledgeDocument document = documentMapper.selectById(documentId);
        if (document == null) throw new BaseException("知识文件不存在");
        boolean publicFile = "PUBLIC".equalsIgnoreCase(document.getScope());
        boolean owner = "PRIVATE".equalsIgnoreCase(document.getScope())
                && userId != null && userId.equals(document.getOwnerId());
        if (publicFile && mutate && !canManagePublic) throw new BaseException("无权管理公共资料");
        if (!publicFile && !owner) throw new BaseException("无权访问该私有资料");
        return document;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BaseException("文件不能为空");
        if (file.getSize() > maxFileBytes) throw new BaseException("文件超过大小限制");
        String name = cleanName(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension(name))) {
            throw new BaseException("仅支持 MD/TXT/PDF/DOCX/XLSX/PPTX");
        }
    }

    private String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) return "PRIVATE";
        String normalized = scope.trim().toUpperCase(Locale.ROOT);
        if (!"PUBLIC".equals(normalized) && !"PRIVATE".equals(normalized)) {
            throw new BaseException("scope 只能为 PUBLIC 或 PRIVATE");
        }
        return normalized;
    }

    private String cleanName(String value) {
        String name = StringUtils.cleanPath(value == null ? "upload" : value);
        name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        return name.length() > 180 ? name.substring(name.length() - 180) : name;
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 1 || dot == name.length() - 1) throw new BaseException("文件必须包含受支持的扩展名");
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private void ensureDirectory(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new BaseException("无法创建知识库目录");
        }
    }

    private void deleteStorage(AiKnowledgeDocument document) {
        if (document.getStoragePath() != null) {
            try {
                deleteQuietly(storedPath(document));
            } catch (BaseException ignored) {
                // Never delete a path outside the configured knowledge storage roots.
            }
        }
    }

    private Path storedPath(AiKnowledgeDocument document) {
        if (document.getStoragePath() == null || document.getStoragePath().isBlank()) {
            throw new BaseException("该知识条目没有可下载的上传文件");
        }
        Path root = "PUBLIC".equalsIgnoreCase(document.getScope())
                ? Path.of(publicUploadPath).toAbsolutePath().normalize()
                : Path.of(privatePath).toAbsolutePath().normalize()
                        .resolve(String.valueOf(document.getOwnerId()));
        Path path = Path.of(document.getStoragePath()).toAbsolutePath().normalize();
        if (!path.startsWith(root)) throw new BaseException("知识文件存储路径无效");
        return path;
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private AiKnowledgeFileVO toVO(AiKnowledgeDocument document) {
        return AiKnowledgeFileVO.builder()
                .documentId(document.getDocumentId())
                .name(document.getOriginalName() == null ? document.getSource() : document.getOriginalName())
                .scope(document.getScope())
                .mimeType(document.getMimeType())
                .size(document.getFileSize())
                .chunkCount(document.getChunkCount())
                .indexStatus(document.getIndexStatus())
                .indexedAt(document.getIndexedAt())
                .build();
    }

    public record DownloadedFile(Resource resource, String fileName, String contentType) {
    }
}
