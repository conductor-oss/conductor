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
package com.netflix.conductor.common.metadata;

public abstract class Auditable {

    private String ownerApp;

    private Long createTime;

    private Long updateTime;

    private String createdBy;

    private String updatedBy;

    /** @return the ownerApp */
    public String getOwnerApp() {
        return ownerApp;
    }

    /** @param ownerApp the ownerApp to set */
    public void setOwnerApp(String ownerApp) {
        this.ownerApp = ownerApp;
    }

    /** @return the createTime */
    public Long getCreateTime() {
        return createTime;
    }

    /** @param createTime the createTime to set */
    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    /** @return the updateTime */
    public Long getUpdateTime() {
        return updateTime;
    }

    /** @param updateTime the updateTime to set */
    public void setUpdateTime(Long updateTime) {
        this.updateTime = updateTime;
    }

    /** @return the createdBy */
    public String getCreatedBy() {
        return createdBy;
    }

    /** @param createdBy the createdBy to set */
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    /** @return the updatedBy */
    public String getUpdatedBy() {
        return updatedBy;
    }

    /** @param updatedBy the updatedBy to set */
    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
