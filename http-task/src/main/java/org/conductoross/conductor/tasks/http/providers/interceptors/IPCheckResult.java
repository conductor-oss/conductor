/*
 * Copyright 2025 Conductor Authors.
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
package org.conductoross.conductor.tasks.http.providers.interceptors;

public class IPCheckResult {

    private final boolean isBlocked;
    private final String blockedBy;

    private IPCheckResult(boolean isBlocked, String blockedBy) {
        this.isBlocked = isBlocked;
        this.blockedBy = blockedBy;
    }

    public static IPCheckResult allowed() {
        return new IPCheckResult(false, null);
    }

    public static IPCheckResult blockedBy(String pattern) {
        return new IPCheckResult(true, pattern);
    }

    public boolean isBlocked() {
        return isBlocked;
    }

    public String getBlockedBy() {
        return blockedBy;
    }
}
