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
import java.util.Set;

import lombok.*;

@EqualsAndHashCode
@ToString
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConductorUser {

    private Boolean applicationUser = null;

    private List<Group> groups = null;

    private String id = null;

    private String name = null;

    private List<Role> roles = null;

    private String uuid = null;

    private Boolean encryptedId = null;

    private String encryptedIdDisplayValue = null;

    public ConductorUser addGroupsItem(Group groupsItem) {
        if (this.groups == null) {
            this.groups = new ArrayList<>();
        }
        this.groups.add(groupsItem);
        return this;
    }

    public ConductorUser addRolesItem(Role rolesItem) {
        if (this.roles == null) {
            this.roles = new ArrayList<>();
        }
        this.roles.add(rolesItem);
        return this;
    }
}