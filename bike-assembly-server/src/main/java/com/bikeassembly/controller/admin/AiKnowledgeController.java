package com.bikeassembly.controller.admin;

import com.bikeassembly.annotation.OperLog;
import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.PermissionConstant;
import com.bikeassembly.context.BaseContext;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.AiDocumentIndexService;
import com.bikeassembly.service.AiKnowledgeFileService;
import com.bikeassembly.service.AiKnowledgeTaskService;
import com.bikeassembly.service.PermissionService;
import com.bikeassembly.vo.AiKnowledgeFileVO;
import com.bikeassembly.vo.AiKnowledgeTaskVO;
import com.bikeassembly.vo.AiKnowledgeStatusVO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * AI 知识库管理接口。
 *
 * <p>索引动作只读本地知识目录并写入向量库，不会执行 MES SQL，
 * 也不会修改工单、库存或设备业务数据。</p>
 */
@RestController
@RequestMapping("/admin/ai/knowledge")
@RequiredArgsConstructor
public class AiKnowledgeController {
    private final AiDocumentIndexService indexService;
    private final AiKnowledgeTaskService taskService;
    private final AiKnowledgeFileService fileService;
    private final PermissionService permissionService;

    @RequirePermission(PermissionConstant.ADMIN_AI_VIEW)
    @GetMapping("/files")
    public Result<List<AiKnowledgeFileVO>> files(
            @RequestParam(required = false) String scope) {
        return Result.success(fileService.list(BaseContext.getCurrentId(), scope));
    }

    @RequirePermission(PermissionConstant.ADMIN_AI_VIEW)
    @PostMapping("/files")
    @OperLog(module = "AI助手", operType = "上传知识文件")
    public Result<AiKnowledgeFileVO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "PRIVATE") String scope) {
        return Result.success(fileService.upload(BaseContext.getCurrentId(), file, scope,
                permissionService.hasPermission(BaseContext.getCurrentId(),
                        PermissionConstant.ADMIN_AI_KNOWLEDGE_MANAGE)));
    }

    @RequirePermission(PermissionConstant.ADMIN_AI_VIEW)
    @PutMapping("/files/{documentId}")
    @OperLog(module = "AI助手", operType = "更新知识文件")
    public Result<AiKnowledgeFileVO> replace(
            @PathVariable Long documentId,
            @RequestParam("file") MultipartFile file) {
        return Result.success(fileService.replace(BaseContext.getCurrentId(), documentId, file,
                permissionService.hasPermission(BaseContext.getCurrentId(),
                        PermissionConstant.ADMIN_AI_KNOWLEDGE_MANAGE)));
    }

    @RequirePermission(PermissionConstant.ADMIN_AI_VIEW)
    @DeleteMapping("/files/{documentId}")
    @OperLog(module = "AI助手", operType = "删除知识文件")
    public Result<Void> delete(@PathVariable Long documentId) {
        fileService.delete(BaseContext.getCurrentId(), documentId,
                permissionService.hasPermission(BaseContext.getCurrentId(),
                        PermissionConstant.ADMIN_AI_KNOWLEDGE_MANAGE));
        return Result.success();
    }

    @RequirePermission(PermissionConstant.ADMIN_AI_VIEW)
    @GetMapping("/files/{documentId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long documentId) {
        AiKnowledgeFileService.DownloadedFile file =
                fileService.download(BaseContext.getCurrentId(), documentId);
        MediaType mediaType;
        try {
            mediaType = file.contentType() == null
                    ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(file.contentType());
        } catch (Exception ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .body(file.resource());
    }

    @RequirePermission(PermissionConstant.ADMIN_AI_KNOWLEDGE_MANAGE)
    @PostMapping("/index")
    @OperLog(module = "AI助手", operType = "索引知识库")
    public Result<AiKnowledgeTaskVO> index(
            @RequestParam(defaultValue = "1200") int maxChars,
            @RequestParam(defaultValue = "160") int overlapChars) {
        if (maxChars < 200 || maxChars > 10000) {
            return Result.error("maxChars 必须在 200 到 10000 之间");
        }
        if (overlapChars < 0 || overlapChars >= maxChars / 2) {
            return Result.error("overlapChars 必须大于等于 0 且小于 maxChars 的一半");
        }
        try {
            return Result.success(taskService.submit(maxChars, overlapChars));
        } catch (IllegalStateException e) {
            return Result.error(e.getMessage());
        }
    }

    @RequirePermission(PermissionConstant.ADMIN_AI_VIEW)
    @GetMapping("/status")
    public Result<AiKnowledgeStatusVO> status() {
        AiDocumentIndexService.IndexResult index = indexService.status();
        AiKnowledgeTaskVO latest = taskService.latest();
        List<String> failures = index.failures();
        if ((failures == null || failures.isEmpty()) && latest != null && latest.getFailures() != null) {
            failures = latest.getFailures();
        }
        boolean unavailable = index.vectorStoreEnabled() && failures != null
                && failures.stream().anyMatch(this::isVectorStoreFailure);
        return Result.success(AiKnowledgeStatusVO.builder()
                .vectorStoreEnabled(index.vectorStoreEnabled())
                .vectorStoreStatus(!index.vectorStoreEnabled() ? "NOT_CONFIGURED"
                        : unavailable ? "UNAVAILABLE" : "ENABLED")
                .knowledgePath(index.knowledgePath())
                .scannedFiles(index.scannedFiles())
                .indexedFiles(index.indexedFiles())
                .failedFiles(index.failedFiles())
                .filesChanged(index.filesChanged())
                .filesSkipped(index.filesSkipped())
                .filesDeleted(index.filesDeleted())
                .chunksIndexed(index.chunksIndexed())
                .chunksTotal(index.chunksTotal())
                .lastTaskId(latest == null ? null : latest.getTaskId())
                .lastTaskStatus(latest == null ? null : latest.getStatus())
                .failures(failures)
                .indexedAt(index.indexedAt())
                .message(latest != null && "FAILED".equals(latest.getStatus())
                        && latest.getMessage() != null ? latest.getMessage() : index.message())
                .build());
    }

    private boolean isVectorStoreFailure(String failure) {
        if (failure == null) return false;
        String normalized = failure.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("embedding") || normalized.contains("pgvector")
                || normalized.contains("维度") || normalized.contains("向量");
    }

    @RequirePermission(PermissionConstant.ADMIN_AI_VIEW)
    @GetMapping("/tasks/{taskId}")
    public Result<AiKnowledgeTaskVO> task(@org.springframework.web.bind.annotation.PathVariable String taskId) {
        AiKnowledgeTaskVO task = taskService.get(taskId);
        return task == null ? Result.error("索引任务不存在") : Result.success(task);
    }
}
