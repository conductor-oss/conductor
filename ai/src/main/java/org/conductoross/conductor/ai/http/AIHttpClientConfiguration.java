package org.conductoross.conductor.ai.http;

import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;

@Configuration
public class AIHttpClientConfiguration {

    @Bean
    public OkHttpClient conductorAiHttpClient(AIHttpClientProperties props) {
        return new OkHttpClient.Builder()
                .connectTimeout(props.getConnectTimeout())
                .readTimeout(props.getReadTimeout())
                .writeTimeout(props.getWriteTimeout())
                .connectionPool(new ConnectionPool(
                        props.getMaxIdleConnections(),
                        props.getKeepAlive().toMillis(),
                        TimeUnit.MILLISECONDS))
                .addInterceptor(new RetryInterceptor(props.getMaxRetries()))
                .build();
    }
}
