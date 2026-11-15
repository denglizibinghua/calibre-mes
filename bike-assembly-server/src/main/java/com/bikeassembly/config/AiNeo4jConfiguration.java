package com.bikeassembly.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "ai.neo4j", name = "enabled", havingValue = "true")
public class AiNeo4jConfiguration {
    @Bean(destroyMethod = "close")
    public Driver aiNeo4jDriver(@Value("${ai.neo4j.uri}") String uri,
                                @Value("${ai.neo4j.username}") String username,
                                @Value("${ai.neo4j.password}") String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("AI_NEO4J_PASSWORD 必须配置");
        }
        return GraphDatabase.driver(uri, AuthTokens.basic(username, password));
    }
}
