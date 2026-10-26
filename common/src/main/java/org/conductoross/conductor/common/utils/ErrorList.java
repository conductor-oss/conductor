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
package org.conductoross.conductor.common.utils;

import java.util.LinkedList;
import java.util.List;

public final class ErrorList {
    private final List<String> errors = new LinkedList<>();

    public ErrorList add(String error) {
        if (error != null && !error.isBlank()) {
            error = error.trim();

            while (error.endsWith(".")) {
                error = error.substring(0, error.length() - 1);
            }

            errors.add(error);
        }

        return this;
    }

    public ErrorList add(String message, Exception e) {
        if (e != null) {
            return this.add(message + ": " + e.getMessage());
        } else {
            return this.add(message);
        }
    }

    public String getMessage(String delim, String suffix) {
        if (errors.isEmpty()) {
            return null;
        }

        return String.join(delim, this.errors) + suffix;
    }

    public String getMessage() {
        return getMessage(", ", ".");
    }

    public boolean isEmpty() {
        return this.errors.isEmpty();
    }

    public boolean arePresent() {
        return !this.errors.isEmpty();
    }

    public static ErrorList empty() {
        return new ErrorList();
    }

    public static ErrorList singleton(String error) {
        return new ErrorList().add(error);
    }

    public static ErrorList singleton(String message, Exception e) {
        return new ErrorList().add(message, e);
    }
}
