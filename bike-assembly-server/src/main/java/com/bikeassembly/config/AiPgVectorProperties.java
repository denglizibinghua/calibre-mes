package com.bikeassembly.config;

import lombok.Data;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 独立 PGVector 连接配置。MySQL 仍作为 MES 主数据源，只有显式开启时才创建这些 Bean。
 */
@Data
@ConfigurationProperties(prefix = "ai.pgvector")
public class AiPgVectorProperties {

    private boolean enabled;
    private String url = "jdbc:postgresql://localhost:5432/mes_ai";
    private String username = "mes";
    private String password = "";
    private int dimensions = 1024;
    private String tableName = "vector_store_bge_m3_1024";
    private String schemaName = "public";
    private PgVectorStore.PgIdType idType = PgVectorStore.PgIdType.UUID;
    private PgVectorStore.PgIndexType indexType = PgVectorStore.PgIndexType.HNSW;
    private PgVectorStore.PgDistanceType distanceType = PgVectorStore.PgDistanceType.COSINE_DISTANCE;
    private boolean initializeSchema;
    private boolean schemaValidation;
    private boolean removeExistingVectorStoreTable;
    private int maxDocumentBatchSize = 10000;
}
