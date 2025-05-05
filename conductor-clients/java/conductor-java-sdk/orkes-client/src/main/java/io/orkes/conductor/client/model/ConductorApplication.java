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

import lombok.*;

@EqualsAndHashCode
@ToString
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ConductorApplication {

    private String createdBy = null;

    private String id = null;

    private String name = null;

    private Long createTime = null;

    private Long updateTime = null;

    private String updatedBy = null;

    public ConductorApplication createdBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }

    public ConductorApplication id(String id) {
        this.id = id;
        return this;
    }

    public ConductorApplication name(String name) {
        this.name = name;
        return this;
    }
}