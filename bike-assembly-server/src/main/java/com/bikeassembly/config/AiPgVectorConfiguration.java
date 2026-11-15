package com.bikeassembly.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * PGVector 使用独立 PostgreSQL 数据源（ai.pgvector.*, database mes_ai），
 * 避免把 MES 关系数据源（database bike_assembly_mes）误传给向量库。
 * Spring AI 自带的 PGVector 自动配置被排除，确保该配置只在显式开关打开时生效。
 */
@Configuration
@ConditionalOnProperty(prefix = "ai.pgvector", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AiPgVectorProperties.class)
public class AiPgVectorConfiguration {

    // A custom PG datasource disables Boot's default datasource creation.
    @Bean(name = "mesDataSourceProperties")
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties mesDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "dataSource")
    @Primary
    public DataSource mesDataSource(
            @Qualifier("mesDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean(name = "aiPgVectorDataSource")
    public DataSource aiPgVectorDataSource(AiPgVectorProperties properties) {
        return DataSourceBuilder.create()
                .url(properties.getUrl())
                .username(properties.getUsername())
                .password(properties.getPassword())
                .build();
    }

    @Bean(name = "aiPgVectorJdbcTemplate")
    public JdbcTemplate aiPgVectorJdbcTemplate(
            @Qualifier("aiPgVectorDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    public VectorStore aiPgVectorStore(
            @Qualifier("aiPgVectorJdbcTemplate") JdbcTemplate jdbcTemplate,
            EmbeddingModel embeddingModel,
            AiPgVectorProperties properties) {
        if (properties.getDimensions() <= 0) {
            throw new IllegalStateException("PGVector 维度必须大于 0，请配置 AI_PGVECTOR_DIMENSIONS");
        }
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .schemaName(properties.getSchemaName())
                .vectorTableName(properties.getTableName())
                .idType(properties.getIdType())
                // Spring AI 1.0.0-M6 validates before running initializeSchema.
                .vectorTableValidationsEnabled(
                        properties.isSchemaValidation() && !properties.isInitializeSchema())
                .dimensions(properties.getDimensions())
                .distanceType(properties.getDistanceType())
                .removeExistingVectorStoreTable(properties.isRemoveExistingVectorStoreTable())
                .indexType(properties.getIndexType())
                .initializeSchema(properties.isInitializeSchema())
                .maxDocumentBatchSize(properties.getMaxDocumentBatchSize())
                .build();
    }
}
