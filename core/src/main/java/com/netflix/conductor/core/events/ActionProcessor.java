/**
 * Copyright 2017 Netflix, Inc.
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
/**
 * 
 */
package com.netflix.conductor.core.events;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.common.metadata.events.EventHandler.Action;
import com.netflix.conductor.common.metadata.events.EventHandler.StartWorkflow;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.execution.ParametersUtils;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.service.MetadataService;

/**
 * @author Viren
 * Action Processor subscribes to the Event Actions queue and processes the actions (e.g. start workflow etc)
 * <p><font color=red>Warning</font> This is a work in progress and may be changed in future.  Not ready for production yet.
 */
@Singleton
public class ActionProcessor {

	private static Logger logger = LoggerFactory.getLogger(EventProcessor.class);

	private WorkflowExecutor executor;

	private MetadataService metadata;
	
	private ParametersUtils pu = new ParametersUtils();
	
	@Inject
	public ActionProcessor(WorkflowExecutor executor, MetadataService metadata) {
		this.executor = executor;
		this.metadata = metadata;
	}

	public Map<String, Object> execute(Action action, String payload) throws Exception {

		logger.info("Executing {}", action.getAction());

		switch (action.getAction()) {
		case start_workflow:
			Map<String, Object> op = startWorkflow(action, payload);
			return op;
		}
		throw new UnsupportedOperationException("Action not supported " + action.getAction());

	}

	private Map<String, Object> startWorkflow(Action action, String payload) throws Exception {
		StartWorkflow params = action.getStart_workflow();
		Map<String, Object> op = new HashMap<>();
		try {
			
			WorkflowDef def = metadata.getWorkflowDef(params.getName(), params.getVersion());
			Map<String, Object> inputParams = params.getInput();
			Map<String, Object> workflowInput = pu.replace(inputParams, payload);
			String id = executor.startWorkflow(def.getName(), def.getVersion(), params.getCorrelationId(), workflowInput);
			op.put("workflowId", id);
			
		}catch(Exception e) {
			logger.error(e.getMessage(), e);
			op.put("error", e.getMessage());
		}
		
		return op;
	}
}
