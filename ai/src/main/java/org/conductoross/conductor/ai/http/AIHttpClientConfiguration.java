package org.conductoross.conductor.ai.http;

import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;

@Configuration
public class AIHttpClientConfiguration {

    private OkHttpClient client;

    @Bean
    public OkHttpClient conductorAiHttpClient(AIHttpClientProperties props) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(props.getConnectTimeout())
                .readTimeout(props.getReadTimeout())
                .writeTimeout(props.getWriteTimeout())
                .connectionPool(new ConnectionPool(
                        props.getMaxIdleConnections(),
                        props.getKeepAlive().toMillis(),
                        TimeUnit.MILLISECONDS));

        if (props.getMaxRetries() > 0) {
            builder.addInterceptor(new RetryInterceptor(props.getMaxRetries()));
        }

        client = builder.build();
        return client;
    }

    @PreDestroy
    public void shutdown() {
        if (client != null) {
            client.dispatcher().executorService().shutdown();
            client.connectionPool().evictAll();
        }
    }
}
