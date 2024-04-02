/*
 * Copyright 2024 Conductor Authors.
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
package com.netflix.conductor.contribs.listener;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLException;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.ServiceUnavailableRetryStrategy;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestClientManager {
    private static final Logger logger = LoggerFactory.getLogger(RestClientManager.class);
    private StatusNotifierNotificationProperties config;
    private CloseableHttpClient client;
    private String notifType;
    private String notifId;

    public enum NotificationType {
        TASK,
        WORKFLOW
    };

    public RestClientManager(StatusNotifierNotificationProperties config) {
        logger.info("created RestClientManager" + System.currentTimeMillis());
        this.config = config;
        this.client = prepareClient();
    }

    private PoolingHttpClientConnectionManager prepareConnManager() {
        PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
        connManager.setMaxTotal(config.getConnectionPoolMaxRequest());
        connManager.setDefaultMaxPerRoute(config.getConnectionPoolMaxRequestPerRoute());
        return connManager;
    }

    private RequestConfig prepareRequestConfig() {
        return RequestConfig.custom()
                // The time to establish the connection with the remote host
                // [http.connection.timeout].
                // Responsible for java.net.SocketTimeoutException: connect timed out.
                .setConnectTimeout(config.getRequestTimeOutMsConnect())

                // The time waiting for data after the connection was established
                // [http.socket.timeout]. The maximum time
                // of inactivity between two data packets. Responsible for
                // java.net.SocketTimeoutException: Read timed out.
                .setSocketTimeout(config.getRequestTimeoutMsread())

                // The time to wait for a connection from the connection manager/pool
                // [http.connection-manager.timeout].
                // Responsible for org.apache.http.conn.ConnectionPoolTimeoutException.
                .setConnectionRequestTimeout(config.getRequestTimeoutMsConnMgr())
                .build();
    }

    /**
     * Custom HttpRequestRetryHandler implementation to customize retries for different IOException
     */
    private class CustomHttpRequestRetryHandler implements HttpRequestRetryHandler {
        int maxRetriesCount = config.getRequestRetryCount();
        int retryIntervalInMilisec = config.getRequestRetryCountIntervalMs();

        /**
         * Triggered only in case of exception
         *
         * @param exception The cause
         * @param executionCount Retry attempt sequence number
         * @param context {@link HttpContext}
         * @return True if we want to retry request, false otherwise
         */
        public boolean retryRequest(
                IOException exception, int executionCount, HttpContext context) {
            Throwable rootCause = ExceptionUtils.getRootCause(exception);
            logger.warn(
                    "Retrying {} notification. Id: {}, root cause: {}",
                    notifType,
                    notifId,
                    rootCause.toString());

            if (executionCount >= maxRetriesCount) {
                logger.warn(
                        "{} notification failed after {} retries. Id: {} .",
                        notifType,
                        executionCount,
                        notifId);
                return false;
            } else if (rootCause instanceof SocketException
                    || rootCause instanceof InterruptedIOException
                    || exception instanceof SSLException) {
                try {
                    Thread.sleep(retryIntervalInMilisec);
                } catch (InterruptedException e) {
                    e.printStackTrace(); // do nothing
                }
                return true;
            } else return false;
        }
    }

    /**
     * Custom ServiceUnavailableRetryStrategy implementation to retry on HTTP 503 (= service
     * unavailable)
     */
    private class CustomServiceUnavailableRetryStrategy implements ServiceUnavailableRetryStrategy {
        int maxRetriesCount = config.getRequestRetryCount();
        int retryIntervalInMilisec = config.getRequestRetryCountIntervalMs();

        @Override
        public boolean retryRequest(
                final HttpResponse response, final int executionCount, final HttpContext context) {

            int httpStatusCode = response.getStatusLine().getStatusCode();
            if (httpStatusCode != 503) return false; // retry only on HTTP 503

            if (executionCount >= maxRetriesCount) {
                logger.warn(
                        "HTTP 503 error. {} notification failed after {} retries. Id: {} .",
                        notifType,
                        executionCount,
                        notifId);
                return false;
            } else {
                logger.warn(
                        "HTTP 503 error. {} notification failed after {} retries. Id: {} .",
                        notifType,
                        executionCount,
                        notifId);
                return true;
            }
        }

        @Override
        public long getRetryInterval() {
            // Retry interval between subsequent requests, in milliseconds.
            // If not set, the default value is 1000 milliseconds.
            return retryIntervalInMilisec;
        }
    }

    // By default retries 3 times
    private CloseableHttpClient prepareClient() {
        return HttpClients.custom()
                .setConnectionManager(prepareConnManager())
                .setDefaultRequestConfig(prepareRequestConfig())
                .setRetryHandler(new CustomHttpRequestRetryHandler())
                .setServiceUnavailableRetryStrategy(new CustomServiceUnavailableRetryStrategy())
                .build();
    }

    public void postNotification(
            RestClientManager.NotificationType notifType,
            String data,
            String id,
            StatusNotifier statusNotifier)
            throws IOException {
        this.notifType = notifType.toString();
        notifId = id;
        String url = prepareUrl(notifType, statusNotifier);

        Map<String, String> headers = new HashMap<>();
        headers.put(config.getHeaderPrefer(), config.getHeaderPreferValue());

        HttpPost request = createPostRequest(url, data, headers);
        long start = System.currentTimeMillis();
        executePost(request);
        long duration = System.currentTimeMillis() - start;
        if (duration > 100) {
            logger.info("Round trip response time = " + (duration) + " millis");
        }
    }

    private String prepareUrl(
            RestClientManager.NotificationType notifType, StatusNotifier statusNotifier) {
        String urlEndPoint = "";

        if (notifType == RestClientManager.NotificationType.TASK) {
            if (statusNotifier != null
                    && StringUtils.isNotBlank(statusNotifier.getEndpointTask())) {
                urlEndPoint = statusNotifier.getEndpointTask();
            } else {
                urlEndPoint = config.getEndpointTask();
            }
        } else if (notifType == RestClientManager.NotificationType.WORKFLOW) {
            if (statusNotifier != null
                    && StringUtils.isNotBlank(statusNotifier.getEndpointTask())) {
                urlEndPoint = statusNotifier.getEndpointWorkflow();
            } else {
                urlEndPoint = config.getEndpointWorkflow();
            }
        }
        String url;
        if (statusNotifier != null) {
            url = statusNotifier.getUrl();
        } else {
            url = config.getUrl();
        }

        return url + "/" + urlEndPoint;
    }

    private HttpPost createPostRequest(String url, String data, Map<String, String> headers)
            throws IOException {
        HttpPost httpPost = new HttpPost(url);
        StringEntity entity = new StringEntity(data);
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept", "application/json");
        httpPost.setHeader("Content-type", "application/json");
        headers.forEach(httpPost::setHeader);
        return httpPost;
    }

    private void executePost(HttpPost httpPost) throws IOException {
        try (CloseableHttpResponse response = client.execute(httpPost)) {
            int sc = response.getStatusLine().getStatusCode();
            if (!(sc == HttpStatus.SC_ACCEPTED || sc == HttpStatus.SC_OK)) {
                throw new ClientProtocolException("Unexpected response status: " + sc);
            }
        } finally {
            httpPost.releaseConnection(); // Release the connection gracefully so the connection can
            // be reused by connection manager
        }
    }
}
