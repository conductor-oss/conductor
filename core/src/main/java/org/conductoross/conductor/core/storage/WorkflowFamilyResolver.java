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
package org.conductoross.conductor.core.storage;

import java.util.Set;

/**
 * Resolves the full family tree of a workflow: self, all ancestors (parentWorkflowId chain), and
 * all descendants (recursive children), with no depth limit.
 *
 * <p>The given workflowId is always included in the returned set — a workflow is, by definition, a
 * member of its own family. This holds even when the workflow record has been archived from the
 * active execution DAO, which would otherwise prevent a long-running workflow from accessing files
 * it owns once its record ages out.
 */
public interface WorkflowFamilyResolver {

    /**
     * Returns the family of the given workflowId.
     *
     * <p>The result always contains {@code workflowId} itself (when non-null). Ancestors and
     * descendants are added when the underlying execution DAO can resolve them; an unknown
     * workflowId still resolves to a single-element set containing only itself.
     *
     * @return a non-null set; empty only when {@code workflowId} is {@code null}
     */
    Set<String> getFamily(String workflowId);
}
