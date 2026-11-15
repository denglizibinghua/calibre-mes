package com.bikeassembly.ai;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.bikeassembly.config.AiPgVectorConfiguration;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AiDataSourceConfigurationTest {
    private static final String MES_URL =
            "jdbc:postgresql://mes-db:5432/bike_assembly_mes";
    private static final String VECTOR_URL = "jdbc:postgresql://vector-db:5432/mes_ai";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,
                    DataSourceTransactionManagerAutoConfiguration.class,
                    MybatisPlusAutoConfiguration.class))
            .withUserConfiguration(AiPgVectorConfiguration.class)
            .withPropertyValues(
                    "spring.datasource.url=" + MES_URL,
                    "spring.datasource.username=mes",
                    "spring.datasource.password=test-only",
                    "ai.pgvector.url=" + VECTOR_URL,
                    "ai.pgvector.username=vector",
                    "ai.pgvector.password=test-only",
                    "ai.pgvector.initialize-schema=false")
            // This test checks datasource routing without contacting either database.
            .withBean(VectorStore.class, () -> mock(VectorStore.class));

    @Test
    void enablingVectorStoreKeepsMybatisAndTransactionsOnDefaultDatasource() {
        runner.withPropertyValues("ai.pgvector.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            DataSource mes = context.getBean("dataSource", DataSource.class);
            DataSource vector = context.getBean("aiPgVectorDataSource", DataSource.class);
            assertThat(context.getBeansOfType(DataSource.class)).hasSize(2);
            assertThat(context.getBean(DataSource.class)).isSameAs(mes);
            assertThat(((HikariDataSource) mes).getJdbcUrl()).isEqualTo(MES_URL);
            assertThat(((HikariDataSource) vector).getJdbcUrl()).isEqualTo(VECTOR_URL);
            assertThat(context.getBean(SqlSessionFactory.class).getConfiguration()
                    .getEnvironment().getDataSource()).isSameAs(mes);
            assertThat(context.getBean(DataSourceTransactionManager.class).getDataSource())
                    .isSameAs(mes);
            assertThat(context.getBean("aiPgVectorJdbcTemplate", JdbcTemplate.class).getDataSource())
                    .isSameAs(vector);
        });
    }

    @Test
    void disablingVectorStoreRetainsTheDefaultDatasource() {
        runner.withPropertyValues("ai.pgvector.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean("aiPgVectorDataSource");
            assertThat(context.getBeansOfType(DataSource.class)).hasSize(1);
            DataSource mes = context.getBean(DataSource.class);
            assertThat(((HikariDataSource) mes).getJdbcUrl()).isEqualTo(MES_URL);
            assertThat(context.getBean(SqlSessionFactory.class).getConfiguration()
                    .getEnvironment().getDataSource()).isSameAs(mes);
            assertThat(context.getBean(DataSourceTransactionManager.class).getDataSource())
                    .isSameAs(mes);
        });
    }
}
