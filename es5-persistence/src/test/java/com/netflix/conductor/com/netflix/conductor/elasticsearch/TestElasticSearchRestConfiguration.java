package com.netflix.conductor.com.netflix.conductor.elasticsearch;

import com.netflix.conductor.elasticsearch.ElasticSearchConfiguration;
import com.netflix.conductor.elasticsearch.SystemPropertiesElasticSearchConfiguration;

public class TestElasticSearchRestConfiguration extends SystemPropertiesElasticSearchConfiguration implements ElasticSearchConfiguration {

    public String getURL() {
        return "http://localhost:9200";
    }
}
