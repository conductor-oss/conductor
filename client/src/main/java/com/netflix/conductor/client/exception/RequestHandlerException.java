/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.client.exception;

import java.io.InputStream;

public class RequestHandlerException extends RuntimeException {

    private InputStream response;
    private int status;

    public RequestHandlerException(InputStream response, int status) {
        this.response = response;
        this.status = status;
    }

    public RequestHandlerException(String message, Throwable cause) {
        super(message, cause);
    }

    public RequestHandlerException(String message) {
        super(message);
    }

    public InputStream getResponse() {
        return response;
    }

    public int getStatus() {
        return status;
    }

    public boolean hasResponse() {
        return response != null;
    }
}
