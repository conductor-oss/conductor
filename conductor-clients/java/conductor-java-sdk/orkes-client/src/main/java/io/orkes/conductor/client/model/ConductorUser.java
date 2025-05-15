/*
 * Copyright 2022 Conductor Authors.
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
package io.orkes.conductor.client.model;

import java.util.ArrayList;
import java.util.List;

import lombok.*;

@EqualsAndHashCode
@ToString
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ConductorUser {

    @Deprecated
    private Boolean applicationUser = null;

    private List<Group> groups = null;

    private String id = null;

    private String name = null;

    private List<Role> roles = null;

    private String uuid = null;

    private Boolean encryptedId;

    private String encryptedIdDisplayValue;

    public ConductorUser applicationUser(Boolean applicationUser) {
        this.applicationUser = applicationUser;
        return this;
    }

    @Deprecated
    public Boolean isApplicationUser() {
        return applicationUser;
    }

    public ConductorUser groups(List<Group> groups) {
        this.groups = groups;
        return this;
    }

    public ConductorUser addGroupsItem(Group groupsItem) {
        if (this.groups == null) {
            this.groups = new ArrayList<>();
        }
        this.groups.add(groupsItem);
        return this;
    }

    public ConductorUser id(String id) {
        this.id = id;
        return this;
    }

    public ConductorUser name(String name) {
        this.name = name;
        return this;
    }

    public ConductorUser roles(List<Role> roles) {
        this.roles = roles;
        return this;
    }

    public ConductorUser addRolesItem(Role rolesItem) {
        if (this.roles == null) {
            this.roles = new ArrayList<>();
        }
        this.roles.add(rolesItem);
        return this;
    }

    public ConductorUser uuid(String uuid) {
        this.uuid = uuid;
        return this;
    }
}