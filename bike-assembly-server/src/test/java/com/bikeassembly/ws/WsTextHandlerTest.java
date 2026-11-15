package com.bikeassembly.ws;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WsTextHandlerTest {
    private WebSocketSession session() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(Map.of("clientType", "app", "userId", 1L));
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    @Test
    void registersRepliesOnlyToPingAndRemovesOnClose() throws Exception {
        WsSessionManager manager = new WsSessionManager();
        WsTextHandler handler = new WsTextHandler(manager);
        WebSocketSession session = session();
        handler.afterConnectionEstablished(session);
        assertEquals(1, manager.getSessions("app", 1L).size());
        handler.handleTextMessage(session, new TextMessage("ping"));
        handler.handleTextMessage(session, new TextMessage("unexpected"));
        verify(session, times(1)).sendMessage(new TextMessage("pong"));
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
        assertTrue(manager.getSessions("app", 1L).isEmpty());
    }

    @Test
    void cleansUpTransportAndHeartbeatFailures() throws Exception {
        WsSessionManager manager = new WsSessionManager();
        WsTextHandler handler = new WsTextHandler(manager);
        WebSocketSession transport = session();
        handler.afterConnectionEstablished(transport);
        handler.handleTransportError(transport, new IOException("transport failure"));
        assertTrue(manager.getSessions("app", 1L).isEmpty());
        verify(transport).close();

        WebSocketSession heartbeat = session();
        handler.afterConnectionEstablished(heartbeat);
        doThrow(new IOException("send failure")).when(heartbeat).sendMessage(any());
        handler.handleTextMessage(heartbeat, new TextMessage("ping"));
        assertTrue(manager.getSessions("app", 1L).isEmpty());
        verify(heartbeat).close();
    }
}
