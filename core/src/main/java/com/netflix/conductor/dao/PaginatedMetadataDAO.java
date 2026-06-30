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
package com.netflix.conductor.dao;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.SearchResult;

/**
 * Optional interface for MetadataDAO implementations that support database-level pagination for
 * workflow definitions. DAOs that implement this interface can provide efficient pagination at the
 * persistence layer. If not implemented, the service layer will fall back to in-memory pagination.
 */
public interface PaginatedMetadataDAO {

    /**
     * Search for the latest versions of workflow definitions with pagination support.
     *
     * @param start Starting index for pagination (0-based)
     * @param size Number of results to return per page
     * @return SearchResult containing total count and paginated list of latest workflow definitions
     */
    SearchResult<WorkflowDef> searchWorkflowDefsLatestVersions(int start, int size);

    /**
     * Search for the latest versions of workflow definitions with pagination and field-level
     * filtering. When filterField and filterValue are both non-null and non-empty, only workflow
     * definitions where the specified field contains the given value (case-insensitive) are
     * returned.
     *
     * @param start Starting index for pagination (0-based)
     * @param size Number of results to return per page
     * @param filterField The workflow definition field to filter on (e.g., "name", "description",
     *     "ownerEmail")
     * @param filterValue The substring to match against the specified field (case-insensitive)
     * @return SearchResult containing total count and filtered, paginated list of latest workflow
     *     definitions
     */
    SearchResult<WorkflowDef> searchWorkflowDefsLatestVersions(
            int start, int size, String filterField, String filterValue);
}
