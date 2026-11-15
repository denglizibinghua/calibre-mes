package com.bikeassembly.ai;

import com.bikeassembly.dto.AiQueryDTO;
import com.bikeassembly.mapper.AiMesDataMapper;
import com.bikeassembly.service.AiQueryService;
import com.bikeassembly.service.AiRequestAuditService;
import com.bikeassembly.service.DashboardScreenService;
import com.bikeassembly.service.DataScopeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiQueryServiceTest {
    private VectorStore vectorStore;
    private AiQueryService service;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        ObjectProvider<VectorStore> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(vectorStore);
        DataScopeService scopes = mock(DataScopeService.class);
        when(scopes.current()).thenReturn(
                new DataScopeService.Scope(1L, "ALL", null, List.of(), true));
        service = new AiQueryService(
                mock(ChatClient.Builder.class), mock(DashboardScreenService.class),
                mock(AiMesDataMapper.class),
                new ObjectMapper().registerModule(new JavaTimeModule()),
                provider, scopes, mock(AiRequestAuditService.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "如何处理不合格品？请依据知识库简要说明。",
            "工单有哪些状态，如何流转？",
            "物料出入库怎么操作？"
    })
    void proceduralQuestionsIncludeRetrievedEvidence(String question) {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                new Document("retrieved-evidence",
                        Map.of("relativeSource", "guide.md", "scope", "PUBLIC"))));

        var context = service.buildStreamContext(request(question));

        assertTrue(context.prompt().contains("retrieved-evidence"));
        assertTrue(context.sources().stream().anyMatch(source -> source.contains("guide.md")));
        verify(vectorStore).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void inventoryCountDoesNotRequireDocumentRetrieval() {
        service.buildStreamContext(request("当前物料库存还剩多少？"));

        verifyNoInteractions(vectorStore);
    }

    @Test
    void noMatchesDoesNotInventDocumentSources() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        var context = service.buildStreamContext(request("请查询知识库中的工单操作流程"));

        verify(vectorStore).similaritySearch(any(SearchRequest.class));
        assertFalse(context.sources().stream().anyMatch(source -> source.startsWith("[来源")));
        assertTrue(context.prompt().contains("未检索到相关文档"));
    }

    @Test
    void filtersPrivateAndUnscopedDocumentsEvenIfVectorStoreReturnsThem() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                new Document("public evidence", Map.of("scope", "PUBLIC", "source", "public.md")),
                new Document("own evidence", Map.of("scope", "PRIVATE", "ownerId", 1L, "source", "own.md")),
                new Document("other user's evidence",
                        Map.of("scope", "PRIVATE", "ownerId", 2L, "source", "other.md")),
                new Document("unscoped legacy evidence", Map.of("source", "legacy.md"))));

        var context = service.buildStreamContext(request("请依据知识库说明作业指导流程"));

        assertTrue(context.prompt().contains("public evidence"));
        assertTrue(context.prompt().contains("own evidence"));
        assertFalse(context.prompt().contains("other user's evidence"));
        assertFalse(context.prompt().contains("unscoped legacy evidence"));
        assertFalse(context.sources().stream().anyMatch(source -> source.contains("other.md")));
        assertFalse(context.sources().stream().anyMatch(source -> source.contains("legacy.md")));
    }

    private AiQueryDTO request(String question) {
        AiQueryDTO request = new AiQueryDTO();
        request.setQuestion(question);
        request.setDays(7);
        return request;
    }
}
