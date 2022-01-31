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
package com.netflix.conductor.core.exception;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class ApplicationException extends RuntimeException {

    public enum Code {
        INVALID_INPUT(400),
        INTERNAL_ERROR(500),
        NOT_FOUND(404),
        CONFLICT(409),
        UNAUTHORIZED(403),
        BACKEND_ERROR(500);

        private final int statusCode;

        Code(int statusCode) {
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }

    private final Code code;

    public boolean isRetryable() {
        return this.code == Code.BACKEND_ERROR;
    }

    public ApplicationException(String msg, Throwable t) {
        this(Code.INTERNAL_ERROR, msg, t);
    }

    public ApplicationException(Code code, String msg, Throwable t) {
        super(code + " - " + msg, t);
        this.code = code;
    }

    public ApplicationException(Code code, Throwable t) {
        super(code.name(), t);
        this.code = code;
    }

    public ApplicationException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public int getHttpStatusCode() {
        return this.code.getStatusCode();
    }

    public Code getCode() {
        return this.code;
    }

    public String getTrace() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        this.printStackTrace(ps);
        ps.flush();
        return baos.toString();
    }

    public Map<String, Object> toMap() {
        HashMap<String, Object> map = new LinkedHashMap<>();
        map.put("code", code.name());
        map.put("message", super.getMessage());
        map.put("retryable", isRetryable());
        return map;
    }
}
