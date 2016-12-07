/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.conductor.dao;

import com.netflix.conductor.common.run.Workflow;

/**
 * 
 * @author Viren
 * DAO used to archive completed workflows after the retention period.
 */
public interface WorkflowArchiveDAO {

	/**
	 * 
	 * @param workflow Workflow to be archived
	 */
	public abstract void archive(Workflow workflow);
	
	/**
	 * 
	 * @param workflowId Retrieve workflow using ID
	 * @return Workflow identified by workflowId
	 */
	public abstract Workflow get(String workflowId);

}