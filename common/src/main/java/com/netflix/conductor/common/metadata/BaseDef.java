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

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import com.netflix.conductor.common.metadata.acl.Permission;

/**
 * A base class for {@link com.netflix.conductor.common.metadata.workflow.WorkflowDef} and {@link
 * com.netflix.conductor.common.metadata.tasks.TaskDef}.
 */
public abstract class BaseDef extends Auditable {

    private final Map<Permission, String> accessPolicy = new EnumMap<>(Permission.class);

    public void addPermission(Permission permission, String allowedAuthority) {
        this.accessPolicy.put(permission, allowedAuthority);
    }

    public void addPermissionIfAbsent(Permission permission, String allowedAuthority) {
        this.accessPolicy.putIfAbsent(permission, allowedAuthority);
    }

    public void removePermission(Permission permission) {
        this.accessPolicy.remove(permission);
    }

    public String getAllowedAuthority(Permission permission) {
        return this.accessPolicy.get(permission);
    }

    public void clearAccessPolicy() {
        this.accessPolicy.clear();
    }

    public Map<Permission, String> getAccessPolicy() {
        return Collections.unmodifiableMap(this.accessPolicy);
    }

    public void setAccessPolicy(Map<Permission, String> accessPolicy) {
        this.accessPolicy.putAll(accessPolicy);
    }
}
