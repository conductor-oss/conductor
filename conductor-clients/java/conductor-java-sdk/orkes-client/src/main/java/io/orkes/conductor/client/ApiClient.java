/*
 * Copyright 2024 Orkes, Inc.
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
package io.orkes.conductor.client;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import com.netflix.conductor.client.http.ConductorClient;

import io.orkes.conductor.client.http.ApiCallback;
import io.orkes.conductor.client.http.ApiException;
import io.orkes.conductor.client.http.ApiResponse;
import io.orkes.conductor.client.http.OrkesAuthentication;
import io.orkes.conductor.client.http.Pair;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * This class exists to maintain backward compatibility and facilitate the migration for
 * users of orkes-conductor-client v2 to v3.
 */
@Deprecated
public final class ApiClient extends ConductorClient {

    public ApiClient(String rootUri, String keyId, String secret) {
        super(ConductorClient.builder()
                .basePath(rootUri)
                .addHeaderSupplier(new OrkesAuthentication(keyId, secret)));
    }

    public ApiClient(String rootUri) {
        super(rootUri);
    }

    @Deprecated
    public Call buildCall(
            String path,
            String method,
            List<Pair> queryParams,
            List<Pair> collectionQueryParams,
            Object body,
            Map<String, String> headers) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(path).newBuilder();

        if (queryParams != null) {
            for (Pair param : queryParams) {
                urlBuilder.addQueryParameter(param.getName(), param.getValue());
            }
        }

        if (collectionQueryParams != null) {
            for (Pair param : collectionQueryParams) {
                urlBuilder.addQueryParameter(param.getName(), param.getValue());
            }
        }

        RequestBody requestBody = null;
        if (body != null) {
            if (body instanceof String) {
                requestBody = RequestBody.create((String) body, MediaType.parse("application/json; charset=utf-8"));
            } else {
                // Handle other body types (e.g., JSON objects) as needed
                requestBody = RequestBody.create(body.toString(), MediaType.parse("application/json; charset=utf-8"));
            }
        }

        Request.Builder requestBuilder = new Request.Builder()
                .url(urlBuilder.build())
                .method(method, requestBody);

        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                requestBuilder.addHeader(header.getKey(), header.getValue());
            }
        }

        Request request = requestBuilder.build();

        return okHttpClient.newCall(request);
    }

    /**
     * Escape the given string to be used as URL query value.
     *
     * @param str String to be escaped
     * @return Escaped string
     */
    @Deprecated
    public String escapeString(String str) {
        try {
            return URLEncoder.encode(str, "utf8").replaceAll("\\+", "%20");
        } catch (UnsupportedEncodingException e) {
            return str;
        }
    }

    /**
     * {@link #executeAsync(Call, Type, ApiCallback)}
     *
     * @param <T>      Type
     * @param call     An instance of the Call object
     * @param callback ApiCallback&lt;T&gt;
     */
    @Deprecated
    public <T> void executeAsync(Call call, ApiCallback<T> callback) {
        executeAsync(call, null, callback);
    }

    /**
     * Execute HTTP call asynchronously.
     *
     * @param <T>        Type
     * @param call       The callback to be executed when the API call finishes
     * @param returnType Return type
     * @param callback   ApiCallback
     */
    @SuppressWarnings("unchecked")
    @Deprecated
    public <T> void executeAsync(Call call, final Type returnType, final ApiCallback<T> callback) {
        call.enqueue(new Callback() {
            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                T result;
                try {
                    result = (T) handleResponse(response, returnType);
                } catch (ApiException e) {
                    callback.onFailure(e, response.code(), response.headers().toMultimap());
                    return;
                }
                callback.onSuccess(
                        result, response.code(), response.headers().toMultimap());
            }

            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                callback.onFailure(new ApiException(e), 0, null);
            }
        });
    }

    @Deprecated
    public <T> ApiResponse<T> execute(Call call) throws ApiException {
        return execute(call, null);
    }

    /**
     * Execute HTTP call and deserialize the HTTP response body into the given return type.
     *
     * @param returnType The return type used to deserialize HTTP response body
     * @param <T> The return type corresponding to (same with) returnType
     * @param call Call
     * @return ApiResponse object containing response status, headers and data, which is a Java
     *     object deserialized from response body and would be null when returnType is null.
     * @throws ApiException If fail to execute the call
     */
    @Deprecated
    public <T> ApiResponse<T> execute(Call call, Type returnType) throws ApiException {
        try {
            Response response = call.execute();
            T data = handleResponse(response, returnType);
            return new ApiResponse<T>(response.code(), response.headers().toMultimap(), data);
        } catch (IOException e) {
            throw new ApiException(e);
        }
    }

}
