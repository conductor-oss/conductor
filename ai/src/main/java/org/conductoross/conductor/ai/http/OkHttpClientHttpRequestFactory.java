/*
 * Copyright 2026 Conductor Authors.
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
package org.conductoross.conductor.ai.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.StreamingHttpOutputMessage;
import org.springframework.http.client.AbstractStreamingClientHttpRequest;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;

/**
 * A {@link ClientHttpRequestFactory} backed by OkHttp, so a Spring {@code RestClient} can be built
 * on top of an existing {@link OkHttpClient}.
 *
 * <p><b>Why this exists.</b> Spring Framework removed {@code OkHttp3ClientHttpRequestFactory} in
 * 7.0 (it was deprecated in 6.1) and ships no OkHttp-backed replacement — only the JDK, Apache
 * HttpComponents, Jetty and Reactor factories. Conductor's AI providers share a single tuned {@code
 * conductorAiHttpClient} (see {@link AIHttpClients}) carrying a connection pool, a long read
 * timeout sized for LLM generation, and a retry interceptor. Providers whose Spring AI builders
 * accept only a {@code RestClient.Builder} (Ollama, Mistral, Cohere) would otherwise have to drop
 * to a stock factory and silently lose all of that.
 *
 * <p>This is a trimmed port of Spring's removed implementation (Apache 2.0, same license). Two
 * deliberate differences:
 *
 * <ul>
 *   <li>It only ever wraps a caller-supplied client, so there is no default-client construction and
 *       no {@code DisposableBean} cleanup — the client's lifecycle belongs to whoever owns the
 *       bean. Closing a shared client here would break every other provider using it.
 *   <li>The timeout setters are gone. Callers that need a different timeout hand in a client
 *       derived via {@code newBuilder()}, which keeps configuration in one place instead of letting
 *       the factory silently rebuild the client underneath it.
 * </ul>
 */
public final class OkHttpClientHttpRequestFactory implements ClientHttpRequestFactory {

    /**
     * Methods that must carry a body, even an empty one. Mirrors OkHttp's {@code
     * internal.http.HttpMethod.requiresRequestBody}, reimplemented here rather than depending on an
     * {@code internal} package that carries no compatibility guarantee.
     */
    private static final Set<String> REQUIRES_REQUEST_BODY =
            Set.of("POST", "PUT", "PATCH", "PROPPATCH", "REPORT");

    private final OkHttpClient client;

    public OkHttpClientHttpRequestFactory(OkHttpClient client) {
        Assert.notNull(client, "OkHttpClient must not be null");
        this.client = client;
    }

    @Override
    public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) {
        return new OkHttpClientHttpRequest(this.client, uri, httpMethod);
    }

    private static final class OkHttpClientHttpRequest extends AbstractStreamingClientHttpRequest {

        private final OkHttpClient client;

        private final URI uri;

        private final HttpMethod method;

        private OkHttpClientHttpRequest(OkHttpClient client, URI uri, HttpMethod method) {
            this.client = client;
            this.uri = uri;
            this.method = method;
        }

        @Override
        public HttpMethod getMethod() {
            return this.method;
        }

        @Override
        public URI getURI() {
            return this.uri;
        }

        @Override
        protected ClientHttpResponse executeInternal(
                HttpHeaders headers, StreamingHttpOutputMessage.Body body) throws IOException {
            RequestBody requestBody;
            if (body != null) {
                requestBody = new StreamingRequestBody(headers, body);
            } else if (REQUIRES_REQUEST_BODY.contains(this.method.name())) {
                String header = headers.getFirst(HttpHeaders.CONTENT_TYPE);
                MediaType contentType = (header != null) ? MediaType.parse(header) : null;
                requestBody = RequestBody.create(new byte[0], contentType);
            } else {
                requestBody = null;
            }

            Request.Builder builder = new Request.Builder().url(this.uri.toURL());
            builder.method(this.method.name(), requestBody);
            headers.forEach(
                    (headerName, headerValues) -> {
                        for (String headerValue : headerValues) {
                            builder.addHeader(headerName, (headerValue != null) ? headerValue : "");
                        }
                    });

            return new OkHttpClientHttpResponse(this.client.newCall(builder.build()).execute());
        }
    }

    private static final class StreamingRequestBody extends RequestBody {

        private final HttpHeaders headers;

        private final StreamingHttpOutputMessage.Body body;

        private StreamingRequestBody(HttpHeaders headers, StreamingHttpOutputMessage.Body body) {
            this.headers = headers;
            this.body = body;
        }

        @Override
        public long contentLength() {
            return this.headers.getContentLength();
        }

        @Override
        public MediaType contentType() {
            String contentType = this.headers.getFirst(HttpHeaders.CONTENT_TYPE);
            return StringUtils.hasText(contentType) ? MediaType.parse(contentType) : null;
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            this.body.writeTo(sink.outputStream());
        }

        @Override
        public boolean isOneShot() {
            return !this.body.repeatable();
        }
    }

    private static final class OkHttpClientHttpResponse implements ClientHttpResponse {

        private final Response response;

        private volatile HttpHeaders headers;

        private OkHttpClientHttpResponse(Response response) {
            Assert.notNull(response, "Response must not be null");
            this.response = response;
        }

        @Override
        public HttpStatusCode getStatusCode() {
            return HttpStatusCode.valueOf(this.response.code());
        }

        @Override
        public String getStatusText() {
            return this.response.message();
        }

        @Override
        public InputStream getBody() {
            ResponseBody body = this.response.body();
            return (body != null) ? body.byteStream() : InputStream.nullInputStream();
        }

        @Override
        public HttpHeaders getHeaders() {
            HttpHeaders result = this.headers;
            if (result == null) {
                result = new HttpHeaders();
                for (String headerName : this.response.headers().names()) {
                    for (String headerValue : this.response.headers(headerName)) {
                        result.add(headerName, headerValue);
                    }
                }
                this.headers = result;
            }
            return result;
        }

        @Override
        public void close() {
            ResponseBody body = this.response.body();
            if (body != null) {
                body.close();
            }
        }
    }
}
