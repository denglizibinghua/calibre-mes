package com.bikeassembly.ws;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WsSessionManagerTest {
    @Test
    void isolatesUsersAndChannelsAndRetainsOtherConnections() throws Exception {
        WsSessionManager manager = new WsSessionManager();
        WebSocketSession first = mock(WebSocketSession.class);
        WebSocketSession second = mock(WebSocketSession.class);
        WebSocketSession app = mock(WebSocketSession.class);
        manager.add("admin", 1L, first);
        manager.add("admin", 1L, second);
        manager.add("app", 1L, app);
        assertEquals(2, manager.getAllByClientType("admin").size());
        assertEquals(1, manager.getSessions("app", 1L).size());
        manager.remove("admin", 1L, first);
        assertEquals(java.util.List.of(second), manager.getSessions("admin", 1L));
        when(second.isOpen()).thenReturn(true);
        manager.closeAndRemove("admin", 1L, second);
        verify(second).close();
        assertTrue(manager.getSessions("admin", 1L).isEmpty());
        assertTrue(manager.getSessions("app", 2L).isEmpty());
    }

    @Test
    void concurrentRemovalNeverLosesANewConnection() throws Exception {
        WsSessionManager manager = new WsSessionManager();
        var pool = Executors.newFixedThreadPool(2);
        try {
            for (long id = 1; id <= 300; id++) {
                long user = id;
                WebSocketSession old = mock(WebSocketSession.class);
                WebSocketSession replacement = mock(WebSocketSession.class);
                manager.add("admin", user, old);
                CountDownLatch start = new CountDownLatch(1);
                var add = pool.submit(() -> { start.await(); manager.add("admin", user, replacement); return null; });
                var remove = pool.submit(() -> { start.await(); manager.remove("admin", user, old); return null; });
                start.countDown();
                add.get(3, TimeUnit.SECONDS);
                remove.get(3, TimeUnit.SECONDS);
                assertEquals(java.util.List.of(replacement), manager.getSessions("admin", user));
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
