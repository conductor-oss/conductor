/*
 * Copyright 2025 Conductor Authors.
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
package org.conductoross.conductor;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.extern.slf4j.Slf4j;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;
import static org.springframework.http.MediaType.TEXT_PLAIN;

@Configuration
@Slf4j
public class RestConfiguration implements WebMvcConfigurer {

    private final SpaInterceptor spaInterceptor;

    public RestConfiguration(SpaInterceptor spaInterceptor) {
        this.spaInterceptor = spaInterceptor;
        log.info("spaInterceptor: {}", spaInterceptor);
    }

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer
                .favorParameter(false)
                .favorPathExtension(false)
                .ignoreAcceptHeader(true)
                .defaultContentType(APPLICATION_JSON, TEXT_PLAIN, APPLICATION_OCTET_STREAM);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (spaInterceptor != null) {
            registry.addInterceptor(spaInterceptor)
                    .excludePathPatterns("/api/**")
                    .excludePathPatterns("/actuator/**")
                    .excludePathPatterns("/health/**")
                    .excludePathPatterns("/v3/api-docs")
                    .excludePathPatterns("/v3/api-docs/**")
                    .excludePathPatterns("/swagger-ui/**")
                    .order(Ordered.HIGHEST_PRECEDENCE);
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (spaInterceptor != null) {
            log.info("Serving static resources");
            registry.addResourceHandler("/static/ui/**")
                    .addResourceLocations("classpath:/static/ui/");
        }
    }
}
