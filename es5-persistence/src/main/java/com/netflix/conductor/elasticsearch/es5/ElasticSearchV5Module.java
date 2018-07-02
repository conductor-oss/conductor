/**
 * Copyright 2016 Netflix, Inc.
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
/**
 *
 */
package com.netflix.conductor.elasticsearch.es5;

import com.google.inject.AbstractModule;

import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.dao.es5.index.ElasticSearchDAOV5;
import com.netflix.conductor.elasticsearch.ElasticSearchModule;
import com.netflix.conductor.elasticsearch.EmbeddedElasticSearchProvider;


/**
 * @author Viren
 * Provider for the elasticsearch transport client
 */
public class ElasticSearchV5Module extends AbstractModule {

    @Override
    protected void configure() {
        install(new ElasticSearchModule());
        bind(IndexDAO.class).to(ElasticSearchDAOV5.class);
        bind(EmbeddedElasticSearchProvider.class).to(EmbeddedElasticSearchV5Provider.class);
    }
}
