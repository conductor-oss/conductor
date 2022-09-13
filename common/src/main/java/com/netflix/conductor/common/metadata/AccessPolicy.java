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
package com.netflix.conductor.common.metadata;

import java.util.Objects;

import org.apache.commons.lang3.Validate;

public class AccessPolicy {

    public enum Permission {
        OWNER,
        OPERATOR;
    }

    private final Permission permission;
    private final String allowedAuthority;

    public AccessPolicy(Permission permission, String allowedAuthority) {
        Objects.requireNonNull(permission, "Permission can't be null");
        Validate.notBlank(allowedAuthority, "allowedAuthority can't be blank");
        this.permission = permission;
        this.allowedAuthority = allowedAuthority;
    }

    public Permission getPermission() {
        return permission;
    }

    public String getAllowedAuthority() {
        return allowedAuthority;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccessPolicy that = (AccessPolicy) o;
        return permission == that.permission && allowedAuthority.equals(that.allowedAuthority);
    }

    @Override
    public int hashCode() {
        return Objects.hash(permission, allowedAuthority);
    }
}
