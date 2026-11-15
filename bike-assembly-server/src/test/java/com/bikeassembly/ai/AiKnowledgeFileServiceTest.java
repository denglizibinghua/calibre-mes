package com.bikeassembly.ai;

import com.bikeassembly.entity.AiKnowledgeDocument;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.mapper.AiKnowledgeDocumentMapper;
import com.bikeassembly.service.AiDocumentIndexService;
import com.bikeassembly.service.AiKnowledgeFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class AiKnowledgeFileServiceTest {
    private AiKnowledgeDocumentMapper documentMapper;
    private AiDocumentIndexService indexService;
    private AiKnowledgeFileService service;
    private Path storageRoot;

    @BeforeEach
    void setUp() throws Exception {
        documentMapper = mock(AiKnowledgeDocumentMapper.class);
        indexService = mock(AiDocumentIndexService.class);
        service = new AiKnowledgeFileService(documentMapper, indexService);
        storageRoot = Files.createTempDirectory("mes-ai-file-access");
        ReflectionTestUtils.setField(service, "publicUploadPath", storageRoot.resolve("public").toString());
        ReflectionTestUtils.setField(service, "privatePath", storageRoot.resolve("private").toString());
        ReflectionTestUtils.setField(service, "maxFileBytes", 52_428_800L);
    }

    @Test
    void anotherUserCannotDownloadPrivateKnowledgeFile() {
        when(documentMapper.selectById(5L)).thenReturn(document(5L, "PRIVATE", 22L,
                storageRoot.resolve("private/22/secret.txt")));

        assertThrows(BaseException.class, () -> service.download(11L, 5L));
        verifyNoInteractions(indexService);
    }

    @Test
    void adminWithoutPublicManagePermissionCannotDeletePublicFile() {
        when(documentMapper.selectById(6L)).thenReturn(document(6L, "PUBLIC", null,
                storageRoot.resolve("public/shared.txt")));

        assertThrows(BaseException.class, () -> service.delete(1L, 6L, false));
        verifyNoInteractions(indexService);
        verify(documentMapper, never()).deleteById(6L);
    }

    @Test
    void downloadRejectsStoragePathsOutsideConfiguredRoots() {
        when(documentMapper.selectById(7L)).thenReturn(document(7L, "PUBLIC", null,
                storageRoot.resolve("outside.txt")));

        assertThrows(BaseException.class, () -> service.download(1L, 7L));
    }

    @Test
    void ownerCanDownloadTheirPrivateFile() throws Exception {
        Path ownerRoot = storageRoot.resolve("private/22");
        Files.createDirectories(ownerRoot);
        Path filePath = ownerRoot.resolve("procedure.txt");
        Files.writeString(filePath, "private procedure");
        when(documentMapper.selectById(8L)).thenReturn(document(8L, "PRIVATE", 22L, filePath));

        var downloaded = service.download(22L, 8L);

        assertTrue(downloaded.resource().exists());
    }

    @Test
    void defaultFileListIncludesPublicAndOwnPrivateEntries() {
        when(documentMapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.List.of(
                        document(9L, "PUBLIC", null, storageRoot.resolve("public/shared.txt")),
                        document(10L, "PRIVATE", 22L, storageRoot.resolve("private/22/mine.txt"))));

        assertEquals(2, service.list(22L, null).size());
    }

    @Test
    void privateUploadRequiresAnAuthenticatedOwner() {
        var file = new MockMultipartFile("file", "private.txt", "text/plain", "content".getBytes());

        assertThrows(BaseException.class, () -> service.upload(null, file, "PRIVATE", false));
        verifyNoInteractions(documentMapper, indexService);
    }

    @Test
    void privateUploadInitializesRequiredManifestFieldsBeforeIndexing() {
        var file = new MockMultipartFile("file", "private.txt", "text/plain", "content".getBytes());
        when(indexService.indexStoredDocument(any(AiKnowledgeDocument.class)))
                .thenReturn(AiDocumentIndexService.FileIndexResult.failed("embedding unavailable"));

        service.upload(22L, file, "PRIVATE", false);

        ArgumentCaptor<AiKnowledgeDocument> captor = ArgumentCaptor.forClass(AiKnowledgeDocument.class);
        verify(documentMapper).insert(captor.capture());
        assertEquals("", captor.getValue().getContentHash());
        assertEquals(0, captor.getValue().getChunkCount());
    }

    @Test
    void privateUploadRemovesCopiedFileWhenManifestInsertFails() throws Exception {
        var file = new MockMultipartFile("file", "private.txt", "text/plain", "content".getBytes());
        doThrow(new IllegalStateException("database unavailable"))
                .when(documentMapper).insert(any(AiKnowledgeDocument.class));

        assertThrows(IllegalStateException.class, () -> service.upload(22L, file, "PRIVATE", false));

        Path ownerRoot = storageRoot.resolve("private/22");
        try (Stream<Path> files = Files.list(ownerRoot)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void replaceRejectsAnExistingStoragePathOutsideConfiguredRoots() {
        when(documentMapper.selectById(11L)).thenReturn(document(11L, "PUBLIC", null,
                storageRoot.resolve("outside.txt")));
        var file = new MockMultipartFile("file", "replacement.txt", "text/plain", "content".getBytes());

        assertThrows(BaseException.class, () -> service.replace(1L, 11L, file, true));
        verifyNoInteractions(indexService);
        verify(documentMapper, never()).updateById(
                org.mockito.ArgumentMatchers.any(AiKnowledgeDocument.class));
    }

    private AiKnowledgeDocument document(Long id, String scope, Long ownerId, Path storagePath) {
        AiKnowledgeDocument document = new AiKnowledgeDocument();
        document.setDocumentId(id);
        document.setScope(scope);
        document.setOwnerId(ownerId);
        document.setStoragePath(storagePath.toString());
        document.setOriginalName("procedure.txt");
        return document;
    }
}
