/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.elasticsearch.es7;

import com.google.inject.AbstractModule;
import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.dao.es7.index.ElasticSearchRestDAOV7;
import com.netflix.conductor.elasticsearch.ElasticSearchConfiguration;
import com.netflix.conductor.elasticsearch.EmbeddedElasticSearchProvider;

/**
 * @author Viren
 * Provider for the elasticsearch index DAO.
 */
public class ElasticSearchV7Module extends AbstractModule {

    private boolean restTransport;

    public ElasticSearchV7Module(ElasticSearchConfiguration elasticSearchConfiguration) {
    }

    @Override
    protected void configure() {
        bind(IndexDAO.class).to(ElasticSearchRestDAOV7.class);
        bind(EmbeddedElasticSearchProvider.class).to(EmbeddedElasticSearchV7Provider.class);
    }
}
