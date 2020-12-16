package com.netflix.conductor.contribs.tasks.http;

import org.springframework.web.client.RestTemplate;

import javax.annotation.Nonnull;

@FunctionalInterface
public interface RestTemplateProvider {

    RestTemplate getRestTemplate(@Nonnull HttpTask.Input input);
}
