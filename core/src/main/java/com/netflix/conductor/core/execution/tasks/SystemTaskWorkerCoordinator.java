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

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.metrics.Monitors;

/**
 * @author Viren
 *
 */
@Singleton
public class SystemTaskWorkerCoordinator {

	private static Logger logger = LoggerFactory.getLogger(SystemTaskWorkerCoordinator.class);
	
	private QueueDAO taskQueues;
	
	private WorkflowExecutor executor;
	
	private ExecutorService es;
	
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
	public SystemTaskWorkerCoordinator(QueueDAO taskQueues, WorkflowExecutor executor, Configuration config) {
		this.taskQueues = taskQueues;
		this.executor = executor;
		this.config = config;
		this.unackTimeout = config.getIntProperty("workflow.system.task.worker.callback.seconds", 30);
		int threadCount = config.getIntProperty("workflow.system.task.worker.thread.count", 10);
		this.pollCount = config.getIntProperty("workflow.system.task.worker.poll.count", 10);
		this.pollInterval = config.getIntProperty("workflow.system.task.worker.poll.interval", 50);
		this.workerQueueSize = config.getIntProperty("workflow.system.task.worker.queue.size", 100);
		this.workerQueue = new LinkedBlockingQueue<Runnable>(workerQueueSize);
		if(threadCount > 0) {
			ThreadFactory tf = new ThreadFactoryBuilder().setNameFormat("system-task-worker-%d").build();
			this.es = new ThreadPoolExecutor(threadCount, threadCount,
	                0L, TimeUnit.MILLISECONDS,
	                workerQueue,
	                tf);

			new Thread(()->listen()).start();
			logger.info("System Task Worker Initialized with {} threads and a callback time of {} second and queue size {} with pollCount {}", threadCount, unackTimeout, workerQueueSize, pollCount);
		} else {
			logger.info("System Task Worker DISABLED");
		}
	}

	static synchronized void add(WorkflowSystemTask systemTask) {
		logger.info("Adding system task {}", systemTask.getName());
		queue.add(systemTask);
	}
	
	private void listen() {
		try {
			for(;;) {
				WorkflowSystemTask st = queue.poll(60, TimeUnit.SECONDS);				
				if(st != null && st.isAsync() && !listeningTasks.contains(st)) {
					listen(st);
					listeningTasks.add(st);
				}
			}
		}catch(InterruptedException ie) {
			logger.warn(ie.getMessage(), ie);
		}
	}
	
	private void listen(WorkflowSystemTask systemTask) {
		Executors.newScheduledThreadPool(1).scheduleWithFixedDelay(()->pollAndExecute(systemTask), 1000, pollInterval, TimeUnit.MILLISECONDS);
		logger.info("Started listening {}", systemTask.getName());
	}

	private void pollAndExecute(WorkflowSystemTask systemTask) {
		try {
			
			if(config.disableAsyncWorkers()) {
				logger.warn("System Task Worker is DISABLED.  Not polling.");
				return;
			}

			// get the remaining capacity of worker queue to prevent queue full exception
			int realPollCount = Math.min(workerQueue.remainingCapacity(), pollCount);
			if (realPollCount <= 0) {				
                logger.warn("All workers are busy, not polling.  queue size {}, max {}", workerQueue.size(), workerQueueSize);
                return;
			}

			String name = systemTask.getName();
			List<String> polled = taskQueues.pop(name, realPollCount, 200);
			Monitors.recordTaskPoll(name);
			Monitors.recordTaskPoll(className);
			logger.debug("Polling for {}, got {}", name, polled.size());
			for(String task : polled) {
				logger.debug("Task: {} being sent to the workflow executor", task);
				try {
					es.submit(()->executor.executeSystemTask(systemTask, task, unackTimeout));
				}catch(RejectedExecutionException ree) {
					logger.warn("Queue full for workers {}", workerQueue.size());
				}
			}
			
		} catch (Exception e) {
			Monitors.error(className, "pollAndExecute");
			logger.error(e.getMessage(), e);
		}
	}
	
}	
