/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.conductor.common.validation;

import java.util.StringJoiner;

/** Captures a validation error that can be returned in {@link ErrorResponse}. */
public class ValidationError {

    private String path;
    private String message;
    private String invalidValue;

    public ValidationError() {}

    public ValidationError(String path, String message, String invalidValue) {
        this.path = path;
        this.message = message;
        this.invalidValue = invalidValue;
    }

    public String getPath() {
        return path;
    }

    public String getMessage() {
        return message;
    }

    public String getInvalidValue() {
        return invalidValue;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setInvalidValue(String invalidValue) {
        this.invalidValue = invalidValue;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", ValidationError.class.getSimpleName() + "[", "]")
                .add("path='" + path + "'")
                .add("message='" + message + "'")
                .add("invalidValue='" + invalidValue + "'")
                .toString();
    }
}
