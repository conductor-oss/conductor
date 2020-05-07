package com.netflix.conductor.tests.utils;

import com.netflix.conductor.elasticsearch.EmbeddedElasticSearch;
import com.netflix.conductor.elasticsearch.EmbeddedElasticSearchProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;


/**
 * A helper class to be used only during tests, this class has Guice hooks to start the embedded elastic search on construction
 * and stop before destruction
 *
 */
@Singleton
public class EmbeddedTestElasticSearch {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddedTestElasticSearch.class);

    private final EmbeddedElasticSearch embeddedElasticSearch;

    @Inject
    public EmbeddedTestElasticSearch(EmbeddedElasticSearchProvider embeddedElasticSearchV5Provider) {
        embeddedElasticSearch = embeddedElasticSearchV5Provider.get()
                .orElseThrow(() -> new RuntimeException("Unable to load in memory elastic search"));
    }

    @PostConstruct
    public void init() {
        try {
            embeddedElasticSearch.start();
        } catch (Exception e) {
            logger.error("Error starting the Embedded elastic search", e);
            throw new RuntimeException(e);
        }
    }

    @PreDestroy
    public void cleanup() {
        try {
            embeddedElasticSearch.stop();
        } catch (Exception e) {
            logger.error("Error stopping the Embedded elastic search", e);
            throw new RuntimeException(e);
        }
    }

}
