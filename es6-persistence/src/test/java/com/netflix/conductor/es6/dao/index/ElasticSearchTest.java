package com.netflix.conductor.es6.dao.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.config.ObjectMapperConfiguration;
import com.netflix.conductor.es6.config.ElasticSearchProperties;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

@ContextConfiguration(classes = {ObjectMapperConfiguration.class, ElasticSearchTest.TestConfiguration.class})
@RunWith(SpringRunner.class)
@TestPropertySource(properties = {"workflow.indexing.enabled=true","workflow.elasticsearch.version=6"})
abstract class ElasticSearchTest {

    @Configuration
    static class TestConfiguration {

        @Bean
        public ElasticSearchProperties elasticSearchProperties() {
            return new ElasticSearchProperties();
        }
    }

    protected static final ElasticsearchContainer container = new ElasticsearchContainer(DockerImageName
            .parse("docker.elastic.co/elasticsearch/elasticsearch-oss")
            .withTag("6.8.12")); // this should match the client version

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected ElasticSearchProperties properties;

    @BeforeClass
    public static void startServer() {
        container.start();
    }

    @AfterClass
    public static void stopServer() {
        container.stop();
    }
}
