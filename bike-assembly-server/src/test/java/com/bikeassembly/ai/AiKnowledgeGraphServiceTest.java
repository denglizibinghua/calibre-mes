package com.bikeassembly.ai;

import com.bikeassembly.service.AiKnowledgeGraphService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiKnowledgeGraphServiceTest {
    @Test
    void searchUsesParameterizedQueryAndCurrentOwnerScope() {
        ObjectProvider<Driver> driverProvider = mock(ObjectProvider.class);
        Driver driver = mock(Driver.class);
        Session session = mock(Session.class);
        Result result = mock(Result.class);
        when(driverProvider.getIfAvailable()).thenReturn(driver);
        when(driver.session()).thenReturn(session);
        when(session.run(anyString(), any(Value.class))).thenReturn(result);
        when(result.hasNext()).thenReturn(false);

        AiKnowledgeGraphService service = new AiKnowledgeGraphService(
                driverProvider, mock(ChatClient.Builder.class), new ObjectMapper());
        ReflectionTestUtils.setField(service, "enabled", true);

        service.search("装配工艺流程", 42L);

        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Value> parameters = ArgumentCaptor.forClass(Value.class);
        verify(session).run(query.capture(), parameters.capture());
        assertTrue(query.getValue().contains("a.ownerId = $ownerId"));
        assertTrue(query.getValue().contains("b.ownerId = $ownerId"));
        assertTrue(query.getValue().contains("r.ownerId = $ownerId"));
        assertFalse(query.getValue().contains("42"));
        assertEquals(42L, parameters.getValue().get("ownerId").asLong());
        assertFalse(parameters.getValue().get("terms").asList(Value::asString).isEmpty());
    }
}
