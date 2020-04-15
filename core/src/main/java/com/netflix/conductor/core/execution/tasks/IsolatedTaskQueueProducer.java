/*
 * Copyright 2020 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.conductor.core.execution.tasks;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.utils.QueueUtils;
import com.netflix.conductor.service.MetadataService;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class IsolatedTaskQueueProducer {

	private static final Logger logger = LoggerFactory.getLogger(IsolatedTaskQueueProducer.class);
	private MetadataService metadataService;

	@Inject
	public IsolatedTaskQueueProducer(MetadataService metadataService, Configuration config) {

		this.metadataService = metadataService;
		boolean listenForIsolationGroups = config.getBooleanProperty("workflow.isolated.system.task.enable", false);

		if (listenForIsolationGroups) {
			int pollingTimeOut = config.getIntProperty("workflow.isolated.system.task.poll.time.secs", 10);
			logger.info("Listening for isolation groups");

			Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(this::addTaskQueues, 1000,
				pollingTimeOut, TimeUnit.SECONDS);
		} else {
			logger.info("Isolated System Task Worker DISABLED");
		}
	}

	private Set<TaskDef> getIsolationExecutionNameSpaces() {
		Set<TaskDef> isolationExecutionNameSpaces = Collections.emptySet();
		try {
			List<TaskDef> taskDefs = metadataService.getTaskDefs();
			isolationExecutionNameSpaces = taskDefs.stream()
				.filter(taskDef -> StringUtils.isNotBlank(taskDef.getIsolationGroupId())
					|| StringUtils.isNotBlank(taskDef.getExecutionNameSpace()))
				.collect(Collectors.toSet());
		} catch (RuntimeException unknownException) {
			logger.error("Unknown exception received in getting isolation groups, sleeping and retrying",
				unknownException);
		}
		return isolationExecutionNameSpaces;
	}

	@VisibleForTesting
	void addTaskQueues() {
		Set<TaskDef> isolationTaskDefs = getIsolationExecutionNameSpaces();
		logger.debug("Retrieved queues {}", isolationTaskDefs);
		Set<String> taskTypes = SystemTaskWorkerCoordinator.taskNameWorkflowTaskMapping.keySet();

		for (TaskDef isolatedTaskDef : isolationTaskDefs) {
			for (String taskType : taskTypes) {
				String taskQueue = QueueUtils.getQueueName(taskType, null,
					isolatedTaskDef.getExecutionNameSpace(), isolatedTaskDef.getIsolationGroupId());
				logger.debug("Adding taskQueue:'{}' to system task worker coordinator", taskQueue);
				SystemTaskWorkerCoordinator.queue.add(taskQueue);
			}
		}
	}
}
