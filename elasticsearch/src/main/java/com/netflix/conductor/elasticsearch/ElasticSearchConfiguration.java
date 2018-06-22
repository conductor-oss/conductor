package com.netflix.conductor.elasticsearch;

import com.netflix.conductor.core.config.Configuration;

public interface ElasticSearchConfiguration extends Configuration {

    String ELASTIC_SEARCH_URL_PROPERTY_NAME = "workflow.elasticsearch.url";
    String ELASTIC_SEARCH_URL_DEFAULT_VALUE = "localhost:9300";

    String ELASTIC_SEARCH_INDEX_NAME_PROPERTY_NAME = "workflow.elasticsearch.index.name";
    String ELASTIC_SEARCH_INDEX_NAME_DEFAULT_VALUE = "conductor";

    default String getURL(){
        return getProperty(ELASTIC_SEARCH_URL_PROPERTY_NAME, ELASTIC_SEARCH_URL_DEFAULT_VALUE);
    }

    default String getIndexName(){
        return getProperty(ELASTIC_SEARCH_INDEX_NAME_PROPERTY_NAME, ELASTIC_SEARCH_INDEX_NAME_DEFAULT_VALUE);
    }
}
