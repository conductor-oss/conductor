/*
 * Copyright 2022 Conductor Authors.
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
package com.netflix.conductor.client.http;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.Proxy;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.client.exception.ConductorClientException;
import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import okhttp3.Call;
import okhttp3.ConnectionPool;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.internal.http.HttpMethod;

public class ConductorClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConductorClient.class);
    protected final OkHttpClient okHttpClient;
    protected final String basePath;
    protected final ObjectMapper objectMapper;
    private final boolean verifyingSsl;
    private final InputStream sslCaCert;
    private final KeyManager[] keyManagers;
    private final List<HeaderSupplier> headerSuppliers;

    public static Builder<?> builder() {
        return new Builder<>();
    }

    @SneakyThrows
    protected ConductorClient(Builder<?> builder) {
        builder.validateAndAssignDefaults();
        final OkHttpClient.Builder okHttpBuilder = builder.okHttpClientBuilder;
        this.objectMapper = builder.objectMapperSupplier.get();
        this.basePath = builder.basePath;
        this.verifyingSsl = builder.verifyingSsl;
        this.sslCaCert = builder.sslCaCert;
        this.keyManagers = builder.keyManagers;
        this.headerSuppliers = builder.headerSupplier();

        if (builder.connectTimeout > -1) {
            okHttpBuilder.connectTimeout(builder.connectTimeout, TimeUnit.MILLISECONDS);
        }

        if (builder.readTimeout > -1) {
            okHttpBuilder.readTimeout(builder.readTimeout, TimeUnit.MILLISECONDS);
        }

        if (builder.writeTimeout > -1) {
            okHttpBuilder.writeTimeout(builder.writeTimeout, TimeUnit.MILLISECONDS);
        }

        if (builder.callTimeout > -1) {
            okHttpBuilder.callTimeout(builder.callTimeout, TimeUnit.MILLISECONDS);
        }

        if (builder.proxy != null) {
            okHttpBuilder.proxy(builder.proxy);
        }

        ConnectionPoolConfig connectionPoolConfig = builder.connectionPoolConfig;
        if (connectionPoolConfig != null) {
            okHttpBuilder.connectionPool(new ConnectionPool(
                    connectionPoolConfig.getMaxIdleConnections(),
                    connectionPoolConfig.getKeepAliveDuration(),
                    connectionPoolConfig.getTimeUnit()
            ));
        }

        if (!verifyingSsl) {
            unsafeClient(okHttpBuilder);
        } else if (sslCaCert != null) {
            trustCertificates(okHttpBuilder);
        }

        this.okHttpClient = okHttpBuilder.build();
        this.headerSuppliers.forEach(it -> it.init(this));
    }

    public ConductorClient() {
        this(new Builder<>());
    }

    public ConductorClient(String basePath) {
        this(new Builder<>().basePath(basePath));
    }

    public String getBasePath() {
        return basePath;
    }

    public void shutdown() {
        okHttpClient.dispatcher().executorService().shutdown();
        okHttpClient.connectionPool().evictAll();
        if (okHttpClient.cache() != null) {
            try {
                okHttpClient.cache().close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public ConductorClientResponse<Void> execute(ConductorClientRequest req) {
        return execute(req, null);
    }

    public <T> ConductorClientResponse<T> execute(ConductorClientRequest req, TypeReference<T> typeReference) {
        Map<String, String> headerParams = req.getHeaderParams() == null ? new HashMap<>() : new HashMap<>(req.getHeaderParams());
        List<Param> pathParams = req.getPathParams() == null ? new ArrayList<>() : new ArrayList<>(req.getPathParams());
        List<Param> queryParams = req.getQueryParams() == null ? new ArrayList<>() : new ArrayList<>(req.getQueryParams());

        Request request = buildRequest(req.getMethod().toString(),
                req.getPath(),
                pathParams,
                queryParams,
                headerParams,
                req.getBody());

        Call call = okHttpClient.newCall(request);
        if (typeReference == null) {
            execute(call, null);
            return null;
        }

        return execute(call, typeReference.getType());
    }

    private String parameterToString(Object param) {
        if (param == null) {
            return "";
        } else if (param instanceof Collection) {
            StringBuilder b = new StringBuilder();
            for (Object o : (Collection) param) {
                if (b.length() > 0) {
                    b.append(",");
                }
                b.append(o);
            }
            return b.toString();
        }

        return String.valueOf(param);
    }

    private boolean isJsonMime(String mime) {
        String jsonMime = "(?i)^(application/json|[^;/ \t]+/[^;/ \t]+[+]json)[ \t]*(;.*)?$";
        return mime != null && (mime.matches(jsonMime) || mime.equals("*/*"));
    }

    private String urlEncode(String str) {
        return URLEncoder.encode(str, StandardCharsets.UTF_8);
    }

    @SneakyThrows
    private <T> T deserialize(Response response, Type returnType) {
        if (returnType == null) {
            return null;
        }

        String body = bodyAsString(response);
        if (body == null || "".equals(body)) {
            return null;
        }

        String contentType = response.header("Content-Type");
        if (contentType == null || isJsonMime(contentType)) {
            // This is hacky. It's required because Conductor's API is returning raw strings as JSON
            if (returnType.equals(String.class)) {
                //noinspection unchecked
                return (T) body;
            }

            JavaType javaType = objectMapper.getTypeFactory().constructType(returnType);
            return objectMapper.readValue(body, javaType);
        } else if (returnType.equals(String.class)) {
            //noinspection unchecked
            return (T) body;
        }

        throw new ConductorClientException(
                "Content type \"" + contentType + "\" is not supported for type: " + returnType,
                response.code(),
                response.headers().toMultimap(),
                body);
    }

    @Nullable
    private String bodyAsString(Response response) {
        if (response.body() == null) {
            return null;
        }

        try {
            return response.body().string();
        } catch (IOException e) {
            throw new ConductorClientException(response.message(),
                    e,
                    response.code(),
                    response.headers().toMultimap());
        }
    }

    @SneakyThrows
    private RequestBody serialize(String contentType, @NotNull Object body) {
        //FIXME review this, what if we want to send something other than a JSON in the request
        if (!isJsonMime(contentType)) {
            throw new ConductorClientException("Content type \"" + contentType + "\" is not supported");
        }

        String content;
        if (body instanceof String) {
            content = (String) body;
        } else {
            content = objectMapper.writeValueAsString(body);
        }

        return RequestBody.create(content, MediaType.parse(contentType));
    }

    protected <T> T handleResponse(Response response, Type returnType) {
        if (!response.isSuccessful()) {
            String respBody = bodyAsString(response);
            throw new ConductorClientException(response.message(),
                    response.code(),
                    response.headers().toMultimap(),
                    respBody);
        }

        try {
            if (returnType == null || response.code() == 204) {
                return null;
            } else {
                return deserialize(response, returnType);
            }
        } finally {
            if (response.body() != null) {
                response.body().close();
            }
        }
    }

    protected Request buildRequest(String method,
                                 String path,
                                 List<Param> pathParams,
                                 List<Param> queryParams,
                                 Map<String, String> headers,
                                 Object body) {
        final HttpUrl url = buildUrl(replacePathParams(path, pathParams), queryParams);
        final Request.Builder requestBuilder = new Request.Builder().url(url);
        processHeaderParams(requestBuilder, addHeadersFromProviders(method, path, headers));
        RequestBody reqBody = requestBody(method, getContentType(headers), body);
        return requestBuilder.method(method, reqBody).build();
    }

    private Map<String, String> addHeadersFromProviders(String method, String path, Map<String, String> headers) {
        if (headerSuppliers.isEmpty()) {
            return headers;
        }

        Map<String, String> all = new HashMap<>();
        for (HeaderSupplier supplier : headerSuppliers) {
            all.putAll(supplier.get(method, path));
        }
        // request headers take precedence
        all.putAll(headers);
        return all;
    }

    @NotNull
    private static String getContentType(Map<String, String> headerParams) {
        String contentType = headerParams.get("Content-Type");
        if (contentType == null) {
            contentType = "application/json";
        }

        return contentType;
    }

    private String replacePathParams(String path, List<Param> pathParams) {
        for (Param param : pathParams) {
            path = path.replace("{" + param.name() + "}", urlEncode(param.value()));
        }

        return path;
    }

    @Nullable
    private RequestBody requestBody(String method, String contentType, Object body) {
        if (!HttpMethod.permitsRequestBody(method)) {
            return null;
        }

        if (body == null && "DELETE".equals(method)) {
            return null;
        } else if (body == null) {
            return RequestBody.create("", MediaType.parse(contentType));
        }

        return serialize(contentType, body);
    }

    private HttpUrl buildUrl(String path, List<Param> queryParams) {
        HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(basePath + path))
                .newBuilder();
        for (Param param : queryParams) {
            urlBuilder.addQueryParameter(param.name(), param.value());
        }

        return urlBuilder.build();
    }

    private void processHeaderParams(Request.Builder requestBuilder, Map<String, String> headers) {
        for (Entry<String, String> header : headers.entrySet()) {
            requestBuilder.header(header.getKey(), parameterToString(header.getValue()));
        }
    }

    @SneakyThrows
    private static void unsafeClient(OkHttpClient.Builder okhttpClientBuilder) {
        LOGGER.warn("Unsafe client - Disabling SSL certificate validation is dangerous and should only be used in development environments");
        // Create a trust manager that does not validate certificate chains
        final TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[]{};
                    }
                }
        };
        final SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, trustAllCerts, new SecureRandom());
        // Creates a ssl socket factory with our all-trusting manager
        final javax.net.ssl.SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
        okhttpClientBuilder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
        okhttpClientBuilder.hostnameVerifier((hostname, session) -> true);
    }

    //TODO review this - not sure if it's working 2024-08-07
    private void trustCertificates(OkHttpClient.Builder okhttpClientBuilder) throws GeneralSecurityException {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        Collection<? extends Certificate> certificates = certificateFactory.generateCertificates(sslCaCert);
        if (certificates.isEmpty()) {
            throw new IllegalArgumentException("expected non-empty set of trusted certificates");
        }
        KeyStore caKeyStore = newEmptyKeyStore(null);
        int index = 0;
        for (Certificate certificate : certificates) {
            String certificateAlias = "ca" + index++;
            caKeyStore.setCertificateEntry(certificateAlias, certificate);
        }

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(caKeyStore);
        TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagers, trustManagers, new SecureRandom());
        okhttpClientBuilder.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustManagers[0]);
    }

    private KeyStore newEmptyKeyStore(char[] password) throws GeneralSecurityException {
        try {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, password);
            return keyStore;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private <T> ConductorClientResponse<T> execute(Call call, Type returnType) {
        try {
            Response response = call.execute();
            T data = handleResponse(response, returnType);
            return new ConductorClientResponse<>(response.code(), response.headers().toMultimap(), data);
        } catch (IOException e) {
            throw new ConductorClientException(e);
        }
    }

    public static class Builder<T extends Builder<T>>  {
        private final OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder();
        private String basePath = "http://localhost:8080/api";
        private boolean verifyingSsl = true;
        private InputStream sslCaCert;
        private KeyManager[] keyManagers;
        private long connectTimeout = -1;
        private long readTimeout = -1;
        private long writeTimeout = -1;
        private long callTimeout = -1;
        private Proxy proxy;
        private ConnectionPoolConfig connectionPoolConfig;
        private Supplier<ObjectMapper> objectMapperSupplier = () -> new ObjectMapperProvider().getObjectMapper();
        private final List<HeaderSupplier> headerSuppliers = new ArrayList<>();

        protected T self() {
            //noinspection unchecked
            return (T) this;
        }

        public T basePath(String basePath) {
            this.basePath = basePath;
            return self();
        }

        public T verifyingSsl(boolean verifyingSsl) {
            this.verifyingSsl = verifyingSsl;
            return self();
        }

        public T sslCaCert(InputStream sslCaCert) {
            this.sslCaCert = sslCaCert;
            return self();
        }

        public T keyManagers(KeyManager[] keyManagers) {
            this.keyManagers = keyManagers;
            return self();
        }

        public T connectTimeout(long connectTimeout) {
            this.connectTimeout = connectTimeout;
            return self();
        }

        public T readTimeout(long readTimeout) {
            this.readTimeout = readTimeout;
            return self();
        }

        public T writeTimeout(long writeTimeout) {
            this.writeTimeout = writeTimeout;
            return self();
        }

        public T callTimeout(long callTimeout) {
            this.callTimeout = callTimeout;
            return self();
        }

        public T proxy(Proxy proxy) {
            this.proxy = proxy;
            return self();
        }

        public T connectionPoolConfig(ConnectionPoolConfig config) {
            this.connectionPoolConfig = config;
            return self();
        }

        /**
         * Use it to apply additional custom configurations to the OkHttp3 client. E.g.: add an interceptor.
         *
         * @param configurer
         * @return
         */
        public T configureOkHttp(Consumer<OkHttpClient.Builder> configurer) {
            configurer.accept(this.okHttpClientBuilder);
            return self();
        }

        /**
         * Use it to supply a custom ObjectMapper.
         *
         * @param objectMapperSupplier
         * @return
         */
        public T objectMapperSupplier(Supplier<ObjectMapper> objectMapperSupplier) {
            this.objectMapperSupplier = objectMapperSupplier;
            return self();
        }

        public T addHeaderSupplier(HeaderSupplier headerSupplier) {
            this.headerSuppliers.add(headerSupplier);
            return self();
        }

        protected List<HeaderSupplier> headerSupplier() {
            return headerSuppliers;
        }

        public ConductorClient build() {
            return new ConductorClient(this);
        }

        protected void validateAndAssignDefaults() {
            if (StringUtils.isBlank(basePath)) {
                throw new IllegalArgumentException("basePath cannot be blank");
            }

            if (basePath.endsWith("/")) {
                basePath = basePath.substring(0, basePath.length() - 1);
            }
        }
    }
}
