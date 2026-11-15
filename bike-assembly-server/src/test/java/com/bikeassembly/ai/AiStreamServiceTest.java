package com.bikeassembly.ai;

import com.bikeassembly.dto.AiQueryDTO;
import com.bikeassembly.service.AiQueryService;
import com.bikeassembly.service.AiStreamService;
import com.bikeassembly.ws.WebSocketNotifier;
import com.bikeassembly.ws.WsMessageDTO;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AiStreamServiceTest {

    @Test
    void duplicateIdempotencyKeyDoesNotStartAnotherRequest() {
        WebSocketNotifier notifier = mock(WebSocketNotifier.class);
        AiStreamService service = new AiStreamService(
                notifier, mock(ChatClient.Builder.class), mock(AiQueryService.class), Runnable::run);
        AiQueryDTO request = request("检查今天的生产情况");

        String firstRequestId = service.submit(7L, "same-key", request);
        String duplicateRequestId = service.submit(7L, "same-key", request);

        assertEquals(firstRequestId, duplicateRequestId);
        assertEquals("FAILED", service.getStatus(7L, firstRequestId).getStatus());
        assertNull(service.getStatus(8L, firstRequestId));
        verify(notifier).pushToAdminUser(
                eq(7L), eq(WsMessageDTO.BIZ_AI), eq(WsMessageDTO.EVENT_AI_REQUEST_ACCEPTED),
                argThat(data -> firstRequestId.equals(data.get("requestId"))));
    }

    @Test
    void sameIdempotencyKeyWithDifferentRequestIsRejected() {
        AiStreamService service = new AiStreamService(
                mock(WebSocketNotifier.class), mock(ChatClient.Builder.class),
                mock(AiQueryService.class), Runnable::run);
        service.submit(7L, "same-key", request("检查今天的生产情况"));

        assertThrows(IllegalStateException.class,
                () -> service.submit(7L, "same-key", request("查询本周质量情况")));
    }

    private AiQueryDTO request(String question) {
        AiQueryDTO request = new AiQueryDTO();
        request.setQuestion(question);
        request.setDays(7);
        request.setSessionId("session-1");
        return request;
    }
}
