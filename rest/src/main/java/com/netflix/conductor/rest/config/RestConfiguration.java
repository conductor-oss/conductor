/*
 * Copyright 2020 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.rest.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.TEXT_PLAIN;

@Configuration
public class RestConfiguration implements WebMvcConfigurer {

    /**
     * Disable all 3 (Accept header, url parameter, path extension) strategies of content
     * negotiation and only allow <code>application/json</code> and <code>text/plain</code> types.
     * <br>
     *
     * <p>Any "mapping" that is annotated with <code>produces=TEXT_PLAIN_VALUE</code> will be sent
     * as <code>text/plain</code> all others as <code>application/json</code>.<br>
     * More details on Spring MVC content negotiation can be found at <a
     * href="https://spring.io/blog/2013/05/11/content-negotiation-using-spring-mvc">https://spring.io/blog/2013/05/11/content-negotiation-using-spring-mvc</a>
     * <br>
     */
    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer
                .favorParameter(false)
                .favorPathExtension(false)
                .ignoreAcceptHeader(true)
                .defaultContentType(APPLICATION_JSON, TEXT_PLAIN);
    }
}
