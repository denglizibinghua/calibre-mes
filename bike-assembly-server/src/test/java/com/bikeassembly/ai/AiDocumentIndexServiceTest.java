package com.bikeassembly.ai;

import com.bikeassembly.service.AiDocumentIndexService;
import com.bikeassembly.entity.AiKnowledgeDocument;
import com.bikeassembly.mapper.AiKnowledgeDocumentMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AiDocumentIndexServiceTest {
    @Test
    void scansAndChunksMarkdownDocuments() throws Exception {
        Path root = Files.createTempDirectory("mes-ai-docs");
        Files.writeString(root.resolve("guide.md"), "abcdefghij");
        ObjectProvider<VectorStore> provider = mock(ObjectProvider.class);
        org.mockito.Mockito.when(provider.getIfAvailable()).thenReturn(null);
        AiDocumentIndexService service = new AiDocumentIndexService(provider);
        ReflectionTestUtils.setField(service, "knowledgePath", root.toString());

        assertEquals(1, service.countDocuments());
        var chunks = service.loadChunks(4);
        assertEquals(3, chunks.size());
        assertTrue(chunks.get(0).content().startsWith("abcd"));
    }

    @Test
    void skipsUnchangedFilesAndReindexesChangedFiles() throws Exception {
        Path root = Files.createTempDirectory("mes-ai-incremental");
        Path document = root.resolve("guide.md");
        Files.writeString(document, "# 工艺\n第一版");
        ObjectProvider<VectorStore> provider = mock(ObjectProvider.class);
        org.mockito.Mockito.when(provider.getIfAvailable()).thenReturn(null);
        AiDocumentIndexService service = new AiDocumentIndexService(provider);
        ReflectionTestUtils.setField(service, "knowledgePath", root.toString());

        var first = service.indexKnowledge(200, 0);
        var second = service.indexKnowledge(200, 0);
        Files.writeString(document, "# 工艺\n第二版");
        var third = service.indexKnowledge(200, 0);

        assertEquals(1, first.filesChanged());
        assertEquals(1, second.filesSkipped());
        assertEquals(1, third.filesChanged());
    }

    @Test
    void writesStableMetadataAndDeletesPreviousSource() throws Exception {
        Path root = Files.createTempDirectory("mes-ai-vector");
        Path document = root.resolve("guide.md");
        Files.writeString(document, "# 装配规范\n拧紧扭矩要求");
        VectorStore vectorStore = mock(VectorStore.class);
        ObjectProvider<VectorStore> provider = mock(ObjectProvider.class);
        org.mockito.Mockito.when(provider.getIfAvailable()).thenReturn(vectorStore);
        AiDocumentIndexService service = new AiDocumentIndexService(provider);
        ReflectionTestUtils.setField(service, "knowledgePath", root.toString());

        var result = service.indexKnowledge(200, 0);

        assertTrue(result.vectorStoreEnabled());
        assertEquals(1, result.chunksIndexed());
        org.mockito.ArgumentCaptor<List<org.springframework.ai.document.Document>> documents =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(documents.capture());
        assertDoesNotThrow(() -> UUID.fromString(documents.getValue().get(0).getId()));
        org.mockito.Mockito.verify(vectorStore).delete(any(org.springframework.ai.vectorstore.filter.Filter.Expression.class));
    }

    @Test
    void rejectsEmbeddingDimensionMismatchBeforeIndexing() throws Exception {
        Path root = Files.createTempDirectory("mes-ai-dimension");
        Files.writeString(root.resolve("guide.md"), "# 装配规范\n拧紧扭矩要求");
        VectorStore vectorStore = mock(VectorStore.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        org.mockito.Mockito.when(embeddingModel.embed("MES embedding readiness probe"))
                .thenReturn(new float[768]);
        ObjectProvider<VectorStore> vectorProvider = mock(ObjectProvider.class);
        ObjectProvider<EmbeddingModel> embeddingProvider = mock(ObjectProvider.class);
        org.mockito.Mockito.when(vectorProvider.getIfAvailable()).thenReturn(vectorStore);
        org.mockito.Mockito.when(embeddingProvider.getIfAvailable()).thenReturn(embeddingModel);
        AiDocumentIndexService service = new AiDocumentIndexService(vectorProvider, embeddingProvider);
        ReflectionTestUtils.setField(service, "knowledgePath", root.toString());
        ReflectionTestUtils.setField(service, "configuredDimensions", 1024);

        var result = service.indexKnowledge(200, 0);

        assertEquals(1, result.failures().size());
        assertTrue(result.failures().get(0).contains("实际 768"));
        assertTrue(result.failures().get(0).contains("配置 1024"));
        org.mockito.Mockito.verify(vectorStore, never()).add(any(List.class));
    }

    @Test
    void doesNotDeleteUploadedPublicFilesDuringDocsDirectoryReconciliation() throws Exception {
        Path root = Files.createTempDirectory("mes-ai-uploaded-files");
        AiKnowledgeDocument uploaded = new AiKnowledgeDocument();
        uploaded.setSource("uploads/managed-file.md");
        uploaded.setScope("PUBLIC");
        uploaded.setStoragePath(root.resolve("managed-file.md").toString());
        uploaded.setIndexStatus("INDEXED");
        uploaded.setContentHash("persisted-hash");

        AiKnowledgeDocumentMapper mapper = mock(AiKnowledgeDocumentMapper.class);
        org.mockito.Mockito.when(mapper.selectList(any())).thenReturn(List.of(uploaded));
        ObjectProvider<VectorStore> provider = mock(ObjectProvider.class);
        org.mockito.Mockito.when(provider.getIfAvailable()).thenReturn(null);
        AiDocumentIndexService service = new AiDocumentIndexService(provider);
        ReflectionTestUtils.setField(service, "knowledgePath", root.toString());
        ReflectionTestUtils.setField(service, "documentMapper", mapper);

        service.indexKnowledge(200, 0);

        verify(mapper, never()).delete(any(Wrapper.class));
    }
}
