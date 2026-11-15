package com.bikeassembly.service;

import com.bikeassembly.entity.AiKnowledgeIndexTask;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bikeassembly.mapper.AiKnowledgeIndexTaskMapper;
import com.bikeassembly.utils.RedisService;
import com.bikeassembly.vo.AiKnowledgeTaskVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class AiKnowledgeTaskService {
    private static final String LOCK_KEY = "ai:knowledge:index:lock";

    private final AiDocumentIndexService indexService;
    private final AiKnowledgeIndexTaskMapper taskMapper;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;
    private final Executor executor;
    private final AtomicBoolean localRunning = new AtomicBoolean();

    public AiKnowledgeTaskService(AiDocumentIndexService indexService,
                                  AiKnowledgeIndexTaskMapper taskMapper,
                                  RedisService redisService,
                                  ObjectMapper objectMapper,
                                  @Qualifier("aiKnowledgeExecutor") Executor executor) {
        this.indexService = indexService;
        this.taskMapper = taskMapper;
        this.redisService = redisService;
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    public AiKnowledgeTaskVO submit(int maxChars, int overlapChars) {
        boolean redisAvailable = redisService.isAvailable();
        boolean locked = redisService.setIfAbsent(LOCK_KEY, UUID.randomUUID().toString(), Duration.ofMinutes(30));
        if (redisAvailable && !locked) {
            throw new IllegalStateException("知识库索引任务正在执行");
        }
        if (!redisAvailable && !localRunning.compareAndSet(false, true)) {
            throw new IllegalStateException("知识库索引任务正在执行");
        }
        String taskId = UUID.randomUUID().toString();
        AiKnowledgeIndexTask task = new AiKnowledgeIndexTask();
        task.setTaskId(taskId);
        task.setStatus("QUEUED");
        task.setMaxChars(maxChars);
        task.setOverlapChars(overlapChars);
        task.setScannedFiles(0);
        task.setFilesChanged(0);
        task.setFilesSkipped(0);
        task.setFilesDeleted(0);
        task.setChunksIndexed(0);
        task.setChunksTotal(0);
        task.setFailuresJson("[]");
        task.setCreatedAt(LocalDateTime.now());
        taskMapper.insert(task);
        try {
            executor.execute(() -> run(taskId, maxChars, overlapChars, redisAvailable && locked));
        } catch (RuntimeException e) {
            task.setStatus("FAILED");
            task.setMessage("任务提交失败");
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            release(redisAvailable && locked);
            throw e;
        }
        return toVO(task);
    }

    public AiKnowledgeTaskVO get(String taskId) {
        AiKnowledgeIndexTask task = taskMapper.selectById(taskId);
        return task == null ? null : toVO(task);
    }

    public AiKnowledgeTaskVO latest() {
        AiKnowledgeIndexTask task = taskMapper.selectOne(new LambdaQueryWrapper<AiKnowledgeIndexTask>()
                .orderByDesc(AiKnowledgeIndexTask::getCreatedAt)
                .last("LIMIT 1"));
        return task == null ? null : toVO(task);
    }

    private void run(String taskId, int maxChars, int overlapChars, boolean locked) {
        AiKnowledgeIndexTask task = taskMapper.selectById(taskId);
        if (task == null) {
            release(locked);
            return;
        }
        task.setStatus("RUNNING");
        task.setStartedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        try {
            AiDocumentIndexService.IndexResult result = indexService.indexKnowledge(maxChars, overlapChars);
            task.setStatus(result.failures().isEmpty() ? "COMPLETED" : "FAILED");
            task.setScannedFiles(result.scannedFiles());
            task.setFilesChanged(result.filesChanged());
            task.setFilesSkipped(result.filesSkipped());
            task.setFilesDeleted(result.filesDeleted());
            task.setChunksIndexed(result.chunksIndexed());
            task.setChunksTotal(result.chunksTotal());
            task.setFailuresJson(objectMapper.writeValueAsString(result.failures()));
            task.setMessage(result.message());
        } catch (Throwable e) {
            log.error("索引任务异常终止: taskId={}", taskId, e);
            task.setStatus("FAILED");
            task.setFailuresJson("[\"" + escape(e.getMessage()) + "\"]");
            task.setMessage("索引任务失败");
        } finally {
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            release(locked);
        }
    }

    private void release(boolean redisLocked) {
        if (redisLocked) redisService.delete(LOCK_KEY);
        localRunning.set(false);
    }

    private AiKnowledgeTaskVO toVO(AiKnowledgeIndexTask task) {
        List<String> failures;
        try {
            failures = objectMapper.readValue(task.getFailuresJson() == null ? "[]" : task.getFailuresJson(),
                    new TypeReference<>() {});
        } catch (Exception e) {
            failures = List.of(task.getFailuresJson());
        }
        return AiKnowledgeTaskVO.builder()
                .taskId(task.getTaskId())
                .status(task.getStatus())
                .scannedFiles(value(task.getScannedFiles()))
                .filesChanged(value(task.getFilesChanged()))
                .filesSkipped(value(task.getFilesSkipped()))
                .filesDeleted(value(task.getFilesDeleted()))
                .chunksIndexed(value(task.getChunksIndexed()))
                .chunksTotal(value(task.getChunksTotal()))
                .failures(failures)
                .message(task.getMessage())
                .createdAt(task.getCreatedAt())
                .startedAt(task.getStartedAt())
                .finishedAt(task.getFinishedAt())
                .build();
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private String escape(String value) {
        if (value == null) return "unknown";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}
