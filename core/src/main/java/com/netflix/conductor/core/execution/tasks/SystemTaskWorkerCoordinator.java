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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.metrics.Monitors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Viren
 *
 */
@Singleton
public class SystemTaskWorkerCoordinator {

	private static Logger logger = LoggerFactory.getLogger(SystemTaskWorkerCoordinator.class);

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

	private static BlockingQueue<WorkflowSystemTask> queue = new LinkedBlockingQueue<>();

	private static Set<WorkflowSystemTask> listeningTasks = new HashSet<>();

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
		if(threadCount > 0) {
			ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("system-task-worker-%d").build();
			this.executorService = new ThreadPoolExecutor(threadCount, threadCount,
	                0L, TimeUnit.MILLISECONDS,
	                workerQueue,
	                threadFactory);
			new Thread(this::listen).start();
			logger.info("System Task Worker initialized with {} threads and a callback time of {} seconds and queue size: {} with pollCount: {} and poll interval: {}", threadCount, unackTimeout, workerQueueSize, pollCount, pollInterval);
		} else {
			logger.info("System Task Worker DISABLED");
		}
	}

	static synchronized void add(WorkflowSystemTask systemTask) {
		logger.info("Adding the queue for system task: {}", systemTask.getName());
		queue.add(systemTask);
	}

	private void listen() {
		try {
			//noinspection InfiniteLoopStatement
			for(;;) {
				WorkflowSystemTask workflowSystemTask = queue.poll(60, TimeUnit.SECONDS);
				if(workflowSystemTask != null && workflowSystemTask.isAsync() && !listeningTasks.contains(workflowSystemTask)) {
					listen(workflowSystemTask);
					listeningTasks.add(workflowSystemTask);
				}
			}
		}catch(InterruptedException ie) {
			Monitors.error(className, "listen");
			logger.warn("Error listening for workflow system tasks", ie);
		}
	}

	private void listen(WorkflowSystemTask systemTask) {
		Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(() -> pollAndExecute(systemTask), 1000, pollInterval, TimeUnit.MILLISECONDS);
		logger.info("Started listening for system task: {}", systemTask.getName());
	}

	private void pollAndExecute(WorkflowSystemTask systemTask) {
		String taskName = systemTask.getName();
		try {
			if(config.disableAsyncWorkers()) {
				logger.warn("System Task Worker is DISABLED.  Not polling for system task: {}", taskName);
				return;
			}
			// get the remaining capacity of worker queue to prevent queue full exception
			int realPollCount = Math.min(workerQueue.remainingCapacity(), pollCount);
			if (realPollCount <= 0) {
                logger.warn("All workers are busy, not polling. queue size: {}, max: {}, task:{}", workerQueue.size(), workerQueueSize, taskName);
                return;
			}

			List<String> polledTaskIds = queueDAO.pop(taskName, realPollCount, 200);
			Monitors.recordTaskPoll(taskName);
			logger.debug("Polling for {}, got {} tasks", taskName, polledTaskIds.size());
			for(String taskId : polledTaskIds) {
				logger.debug("Task: {} of type: {} being sent to the workflow executor", taskId, taskName);
				try {
					executorService.submit(()-> workflowExecutor.executeSystemTask(systemTask, taskId, unackTimeout));
				} catch(RejectedExecutionException ree) {
					logger.warn("Queue full for workers. Size: {}, task:{}", workerQueue.size(), taskName);
				}
			}
		} catch (Exception e) {
			Monitors.error(className, "pollAndExecute");
			logger.error("Error executing system task:{}", taskName, e);
		}
	}
}	
