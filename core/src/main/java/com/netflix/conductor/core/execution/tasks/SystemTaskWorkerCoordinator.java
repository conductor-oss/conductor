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
package com.netflix.conductor.core.execution.tasks;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.utils.QueueUtils;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.metrics.Monitors;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Viren
 *
 */
@Singleton
public class SystemTaskWorkerCoordinator {

	private static final Logger logger = LoggerFactory.getLogger(SystemTaskWorkerCoordinator.class);

	private QueueDAO queueDAO;

	private WorkflowExecutor workflowExecutor;

	private ExecutorService executorService;

	private int workerQueueSize;

	//Number of items to poll for
	private int pollCount;

	//Interval in ms at which the polling is done
	private int pollInterval;

	private LinkedBlockingQueue<Runnable> workerQueue;

	private int unackTimeout;

	private Configuration config;

	private final String executionNameSpace;

	static BlockingQueue<String> queue = new LinkedBlockingQueue<>();

	private static Set<String> listeningTaskQueues = new HashSet<>();

	ExecutionConfig defaultExecutionConfig;

	ConcurrentHashMap<String, ExecutionConfig> queueExecutionConfigMap = new ConcurrentHashMap<>();

	public static Map<String, WorkflowSystemTask> taskNameWorkFlowTaskMapping = new ConcurrentHashMap<>();

	private static final String className = SystemTaskWorkerCoordinator.class.getName();

	@Inject
	public SystemTaskWorkerCoordinator(QueueDAO queueDAO, WorkflowExecutor workflowExecutor, Configuration config) {
		this.queueDAO = queueDAO;
		this.workflowExecutor = workflowExecutor;
		this.config = config;
		this.unackTimeout = config.getIntProperty("workflow.system.task.worker.callback.seconds", 30);
		int threadCount = config.getIntProperty("workflow.system.task.worker.thread.count", 10);
		this.pollCount = config.getIntProperty("workflow.system.task.worker.poll.count", 10);
		this.pollInterval = config.getIntProperty("workflow.system.task.worker.poll.interval", 50);
		this.workerQueueSize = config.getIntProperty("workflow.system.task.worker.queue.size", 100);
		this.workerQueue = new LinkedBlockingQueue<>(workerQueueSize);
		this.executionNameSpace =config.getProperty("workflow.system.task.worker.executionNameSpace","");

		if(threadCount > 0) {
			ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("system-task-worker-%d").build();
			this.executorService = new ThreadPoolExecutor(threadCount, threadCount,
	                0L, TimeUnit.MILLISECONDS,
	                workerQueue,
	                threadFactory);
			this.defaultExecutionConfig = new ExecutionConfig(this.executorService, this.workerQueue);
			new Thread(this::listen).start();
			logger.info("System Task Worker initialized with {} threads and a callback time of {} seconds and queue size: {} with pollCount: {} and poll interval: {}", threadCount, unackTimeout, workerQueueSize, pollCount, pollInterval);
		} else {
			logger.info("System Task Worker DISABLED");
		}
	}

	static synchronized void add(WorkflowSystemTask systemTask) {
		logger.info("Adding the queue for system task: {}", systemTask.getName());
		taskNameWorkFlowTaskMapping.put(systemTask.getName(),systemTask);
		queue.add(systemTask.getName());
	}

	private void listen() {
		try {
			//noinspection InfiniteLoopStatement
			for(;;) {
				String workflowSystemTaskQueueName = queue.poll(60, TimeUnit.SECONDS);
				if (workflowSystemTaskQueueName != null && !listeningTaskQueues.contains(workflowSystemTaskQueueName) && shouldListen(workflowSystemTaskQueueName)) {
					listen(workflowSystemTaskQueueName);
					listeningTaskQueues.add(workflowSystemTaskQueueName);
				}
			}
		}catch(InterruptedException ie) {
			Monitors.error(className, "listen");
			logger.warn("Error listening for workflow system tasks", ie);
		}
	}

	private void listen(String queueName) {
		Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(() -> pollAndExecute(queueName), 1000, pollInterval, TimeUnit.MILLISECONDS);
		logger.info("Started listening for queue: {}", queueName);
	}

	@VisibleForTesting
	void pollAndExecute(String queueName) {
		try {
			if(config.disableAsyncWorkers()) {
				logger.warn("System Task Worker is DISABLED.  Not polling for system task in queue : {}", queueName);
				return;
			}
			// get the remaining capacity of worker queue to prevent queue full exception
			ExecutionConfig executionConfig = getExecutionConfig(queueName);
			LinkedBlockingQueue<Runnable> workerQueue = executionConfig.workerQueue;
			int realPollCount = Math.min(workerQueue.remainingCapacity(), pollCount);
			if (realPollCount <= 0) {
                logger.debug("All workers are busy, not polling. queue size: {}, max: {}, task:{}", workerQueue.size(),
					workerQueueSize, queueName);
                return;
			}

			List<String> polledTaskIds = queueDAO.pop(queueName, realPollCount, 200);
			Monitors.recordTaskPoll(queueName);
			logger.debug("Polling for {}, got {} tasks", queueName, polledTaskIds.size());
			for(String taskId : polledTaskIds) {
				logger.debug("Task: {} from queue: {} being sent to the workflow executor", taskId, queueName);
				try {
					String taskName = QueueUtils.getTaskType(queueName);
					WorkflowSystemTask systemTask = taskNameWorkFlowTaskMapping.get(taskName);
					ExecutorService executorService = executionConfig.service;
					executorService.submit(() -> workflowExecutor.executeSystemTask(systemTask, taskId, unackTimeout));
				} catch(RejectedExecutionException ree) {
					logger.warn("Queue full for workers. Size: {}, queue:{}", workerQueue.size(), queueName);
				}
			}
		} catch (Exception e) {
			Monitors.error(className, "pollAndExecute");
			logger.error("Error executing system task in queue:{}", queueName, e);
		}
	}


	public boolean isFromCoordinatorExecutionNameSpace(String queueName) {
		String queueExecutionNameSpace = QueueUtils.getExecutionNameSpace(queueName);
		return StringUtils.equals(queueExecutionNameSpace, this.executionNameSpace);
	}

	private boolean shouldListen(String workflowSystemTaskQueueName) {
		return isFromCoordinatorExecutionNameSpace(workflowSystemTaskQueueName) && isSystemTask(workflowSystemTaskQueueName);
	}


	public static boolean isSystemTask(String queue) {

		String taskType = QueueUtils.getTaskType(queue);

		if(StringUtils.isNotBlank(taskType)) {

			WorkflowSystemTask task = taskNameWorkFlowTaskMapping.get(taskType);
			return Objects.nonNull(task) && task.isAsync();

		}

		return false;
	}



	public ExecutionConfig getExecutionConfig(String taskQueue) {

		if (!QueueUtils.isIsolatedQueue(taskQueue)) {
			return this.defaultExecutionConfig;
		}

		return queueExecutionConfigMap.computeIfAbsent(taskQueue, __ -> this.createExecutionConfig());

	}

	private ExecutionConfig createExecutionConfig() {

		int workerQueueSize = config.getIntProperty("workflow.isolated.system.task.worker.queue.size", 100);
		LinkedBlockingQueue<Runnable> workerQueue = new LinkedBlockingQueue<>(workerQueueSize);
		int threadCount = config.getIntProperty("workflow.isolated.system.task.worker.thread.count", 1);
		ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("isolated-system-task-worker-%d").build();

		return new ExecutionConfig(new ThreadPoolExecutor(threadCount, threadCount,
				0L, TimeUnit.MILLISECONDS,
				workerQueue,
				threadFactory), workerQueue);

	}
}	
