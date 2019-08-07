package com.netflix.conductor.core.execution.tasks;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.utils.QueueUtils;
import com.netflix.conductor.service.MetadataService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Singleton
public class IsolatedTaskQueueProducer {

	private static Logger logger = LoggerFactory.getLogger(IsolatedTaskQueueProducer.class);
	private MetadataService metadataService;
	private int pollingTimeOut;


	@Inject
	public IsolatedTaskQueueProducer(MetadataService metadataService, Configuration config) {

		this.metadataService = metadataService;
		boolean listenForIsolationGroups = config.getBooleanProperty("workflow.isolated.system.task.enable", false);

		if (listenForIsolationGroups) {

			this.pollingTimeOut = config.getIntProperty("workflow.isolated.system.task.poll.time.secs", 10);
			logger.info("Listening for isolation groups");

			Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(() -> addTaskQueues(), 1000, pollingTimeOut, TimeUnit.SECONDS);
		} else {
			logger.info("Isolated System Task Worker DISABLED");
		}

	}


	private Set<TaskDef> getIsolationExecutionNameSpaces() {

		Set<TaskDef> isolationExecutionNameSpaces = Collections.emptySet();

		try {

			List<TaskDef> taskDefs = metadataService.getTaskDefs();
			isolationExecutionNameSpaces = taskDefs.stream().
					filter(taskDef -> StringUtils.isNotBlank(taskDef.getIsolationGroupId())|| StringUtils.isNotBlank(taskDef.getExecutionNameSpace())).
					collect(Collectors.toSet());

		} catch (RuntimeException unknownException) {

			logger.error("Unknown exception received in getting isolation groups, sleeping and retrying", unknownException);
		}
		return isolationExecutionNameSpaces;
	}

	@VisibleForTesting
	void addTaskQueues() {

		Set<TaskDef> isolationDefs = getIsolationExecutionNameSpaces();
		logger.debug("Retrieved queues {}", isolationDefs);
		Set<String> taskTypes = SystemTaskWorkerCoordinator.taskNameWorkFlowTaskMapping.keySet();

		for (TaskDef isolatedTaskDef : isolationDefs) {
			for (String taskType : taskTypes) {
				String taskQueue = QueueUtils.getQueueName(taskType,null, isolatedTaskDef.getExecutionNameSpace(), isolatedTaskDef.getIsolationGroupId());
				logger.debug("Adding task={} to coordinator queue", taskQueue);
				SystemTaskWorkerCoordinator.queue.add(taskQueue);

			}
		}

	}

}
