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
package com.netflix.conductor.client.task;

import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.worker.PropertyFactory;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.discovery.EurekaClient;
import com.netflix.servo.monitor.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author Viren
 * Manages the Task workers thread pool and server communication (poll, task update and acknowledgement).
 */
public class WorkflowTaskCoordinator {

	private static final Logger logger = LoggerFactory.getLogger(WorkflowTaskCoordinator.class);

	private TaskClient client;

	private ExecutorService executorService;

	private ScheduledExecutorService scheduledExecutorService;

	private EurekaClient eurekaClient;

	private List<Worker> workers = new LinkedList<>();

	private int sleepWhenRetry;

	private int updateRetryCount;

	private int workerQueueSize;

	private LinkedBlockingQueue<Runnable> workerQueue;

	private int threadCount;

	private String workerNamePrefix;

	private static final String DOMAIN = "domain";

	private static final String ALL_WORKERS = "all";

	/**
	 * @param eurekaClient Eureka client - used to identify if the server is in discovery or not.  When the server goes out of discovery, the polling is terminated. If passed null, discovery check is not done.
	 * @param client TaskClient used to communicate to the Conductor server
	 * @param threadCount # of threads assigned to the workers. Should be at-least the size of taskWorkers to avoid starvation in a busy system.
	 * @param sleepWhenRetry sleep time in millisecond for Conductor server retries (poll, ack, update task)
	 * @param updateRetryCount number of times to retry the failed updateTask operation
	 * @param workerQueueSize queue size for the polled task.
	 * @param taskWorkers workers that will be used for polling work and task execution.
	 * @param workerNamePrefix String prefix that will be used for all the workers.
	 * <p>
	 * Please see {@link #init()} method. The method must be called after this constructor for the polling to start.
	 * </p>
	 * @see Builder
	 */
	public WorkflowTaskCoordinator(EurekaClient eurekaClient, TaskClient client, int threadCount, int sleepWhenRetry,
								   int updateRetryCount, int workerQueueSize, Iterable<Worker> taskWorkers,
								   String workerNamePrefix) {
		this.eurekaClient = eurekaClient;
		this.client = client;
		this.threadCount = threadCount;
		this.sleepWhenRetry = sleepWhenRetry;
		this.updateRetryCount = updateRetryCount;
		this.workerQueueSize = workerQueueSize;
		this.workerNamePrefix = workerNamePrefix;
		taskWorkers.forEach(workers::add);
	}

	/**
	 *
	 * Builder used to create the instances of WorkflowTaskCoordinator
	 *
	 */
	public static class Builder {

		private String workerNamePrefix = "workflow-worker-";

		private int sleepWhenRetry = 500;

		private int updateRetryCount = 3;

		private int workerQueueSize = 100;

		private int threadCount = -1;

		private Iterable<Worker> taskWorkers;

		private EurekaClient eurekaClient;

		private TaskClient client;

		/**
		 *
		 * @param workerNamePrefix prefix to be used for worker names, defaults to workflow-worker- if not supplied.
		 * @return Returns the current instance.
		 */
		public Builder withWorkerNamePrefix(String workerNamePrefix) {
			this.workerNamePrefix = workerNamePrefix;
			return this;
		}

		/**
		 *
		 * @param sleepWhenRetry time in millisecond, for which the thread should sleep when task update call fails, before retrying the operation.
		 * @return Returns the current instance.
		 */
		public Builder withSleepWhenRetry(int sleepWhenRetry) {
			this.sleepWhenRetry = sleepWhenRetry;
			return this;
		}

		/**
		 *
		 * @param updateRetryCount # of attempts to be made when updating task status when update status call fails.
		 * @return Builder instance
		 * @see #withSleepWhenRetry(int)
		 */
		public Builder withUpdateRetryCount(int updateRetryCount) {
			this.updateRetryCount = updateRetryCount;
			return this;
		}

		/**
		 *
		 * @param workerQueueSize Worker queue size.
		 * @return Builder instance
		 */
		public Builder withWorkerQueueSize(int workerQueueSize) {
			this.workerQueueSize = workerQueueSize;
			return this;
		}

		/**
		 *
		 * @param threadCount # of threads assigned to the workers.  Should be at-least the size of taskWorkers to avoid starvation in a busy system.
		 * @return Builder instance
		 */
		public Builder withThreadCount(int threadCount) {
			if(threadCount < 1) {
				throw new IllegalArgumentException("No. of threads cannot be less than 1");
			}
			this.threadCount = threadCount;
			return this;
		}

		/**
		 *
		 * @param client Task Client used to communicate to Conductor server
		 * @return Builder instance
		 */
		public Builder withTaskClient(TaskClient client) {
			this.client = client;
			return this;
		}

		/**
		 *
		 * @param eurekaClient Eureka client
		 * @return Builder instance
		 */
		public Builder withEurekaClient(EurekaClient eurekaClient) {
			this.eurekaClient = eurekaClient;
			return this;
		}

		/**
		 *
		 * @param taskWorkers workers that will be used for polling work and task execution.
		 * @return Builder instance
		 */
		public Builder withWorkers(Iterable<Worker> taskWorkers) {
			this.taskWorkers = taskWorkers;
			return this;
		}

		/**
		 *
		 * @param taskWorkers workers that will be used for polling work and task execution.
		 * @return Builder instance
		 */
		public Builder withWorkers(Worker... taskWorkers) {
			this.taskWorkers = Arrays.asList(taskWorkers);
			return this;
		}

		/**
		 * Builds an instance of the WorkflowTaskCoordinator.
		 * <p>
		 * Please see {@link WorkflowTaskCoordinator#init()} method. The method must be called after this constructor for the polling to start.
		 * </p>
		 */
		public WorkflowTaskCoordinator build() {
			if(taskWorkers == null) {
				throw new IllegalArgumentException("No task workers are specified. use withWorkers() to add one mor more task workers");
			}

			if(client == null) {
				throw new IllegalArgumentException("No TaskClient provided. use withTaskClient() to provide one");
			}
			return new WorkflowTaskCoordinator(eurekaClient, client, threadCount, sleepWhenRetry, updateRetryCount,
											   workerQueueSize, taskWorkers, workerNamePrefix);
		}
	}

	/**
	 * Starts the polling.
     * Must be called after the constructor {@link #WorkflowTaskCoordinator(EurekaClient, TaskClient, int, int, int, int, Iterable, String)}
     * or the builder {@link Builder#build()} method
	 */
	public synchronized void init() {

		if(threadCount == -1) {
			threadCount = workers.size();
		}

		logger.info("Initialized the worker with {} threads", threadCount);

        this.workerQueue = new LinkedBlockingQueue<Runnable>(workerQueueSize);
        AtomicInteger count = new AtomicInteger(0);
		this.executorService = new ThreadPoolExecutor(threadCount, threadCount,
                0L, TimeUnit.MILLISECONDS,
                workerQueue,
                new ThreadFactory() {

			@Override
			public Thread newThread(Runnable runnable) {
				Thread thread = new Thread(runnable);
				thread.setName(workerNamePrefix + count.getAndIncrement());
				return thread;
			}
		});
		this.scheduledExecutorService = Executors.newScheduledThreadPool(workers.size());
		workers.forEach(worker -> {
			scheduledExecutorService.scheduleWithFixedDelay(()->pollForTask(worker), worker.getPollingInterval(), worker.getPollingInterval(), TimeUnit.MILLISECONDS);
		});
	}

	private void pollForTask(Worker worker) {

		if(eurekaClient != null && !eurekaClient.getInstanceRemoteStatus().equals(InstanceStatus.UP)) {
			logger.debug("Instance is NOT UP in discovery - will not poll");
			return;
		}

		if(worker.paused()) {
			WorkflowTaskMetrics.taskPausedCounter(worker.getTaskDefName());
			logger.debug("Worker {} has been paused. Not polling anymore!", worker.getClass());
			return;
		}
		String domain = PropertyFactory.getString(worker.getTaskDefName(), DOMAIN, null);
		if(domain == null){
			domain = PropertyFactory.getString(ALL_WORKERS, DOMAIN, null);
		}
		logger.debug("Polling {}, domain={}, count = {} timeout = {} ms", worker.getTaskDefName(), domain, worker.getPollCount(), worker.getLongPollTimeoutInMS());

		try{
            // get the remaining capacity of worker queue to prevent queue full exception
            int realPollCount = Math.min(workerQueue.remainingCapacity(), worker.getPollCount());
            if (realPollCount <= 0) {
				logger.warn("All workers are busy, not polling. queue size = {}, max = {}", workerQueue.size(), workerQueueSize);
				return;
            }
			String taskType = worker.getTaskDefName();
			Stopwatch sw = WorkflowTaskMetrics.startPollTimer(worker.getTaskDefName());
			List<Task> tasks = client.batchPollTasksInDomain(taskType, domain, worker.getIdentity(), realPollCount, worker.getLongPollTimeoutInMS());
            sw.stop();
            logger.debug("Polled {}, domain {}, received {} tasks in worker - {}", worker.getTaskDefName(), domain, tasks.size(), worker.getIdentity());

			tasks.forEach(task -> executorService.submit(() -> {
					try {
					    logger.debug("Executing task {}, taskId - {} in worker - {}", task.getTaskDefName(), task.getTaskId(), worker.getIdentity());
						execute(worker, task);
					} catch (Throwable t) {
						task.setStatus(Task.Status.FAILED);
						TaskResult result = new TaskResult(task);
						handleException(t, result, worker, true, task);
					}
				})
			);
		}catch(RejectedExecutionException qfe) {
			WorkflowTaskMetrics.taskExecutionQueueFullCounter(worker.getTaskDefName());
			logger.error("Execution queue is full", qfe);
		} catch (Exception e) {
			WorkflowTaskMetrics.taskPollErrorCounter(worker.getTaskDefName(), e);
			logger.error("Error when polling for task", e);
		}
	}

	private void execute(Worker worker, Task task) {
		String taskType = task.getTaskDefName();
		try {

			if(!worker.preAck(task)) {
				logger.debug("Worker decided not to ack the task {}, taskId = {}", taskType, task.getTaskId());
				return;
			}

			if (!client.ack(task.getTaskId(), worker.getIdentity())) {
				WorkflowTaskMetrics.taskAckFailedCounter(worker.getTaskDefName());
				logger.error("Ack failed for {}, taskId = {}", taskType, task.getTaskId());
				return;
			}

		} catch (Exception e) {
			logger.error(String.format("ack exception for task %s, taskId = %s in worker - %s", task.getTaskDefName(), task.getTaskId(), worker.getIdentity()), e);
			WorkflowTaskMetrics.taskAckErrorCounter(worker.getTaskDefName(), e);
			return;
		}

		Stopwatch sw = WorkflowTaskMetrics.startExecutionTimer(worker.getTaskDefName());

		TaskResult result = null;
		try {
			logger.debug("Executing task {} in worker {} at {}", task, worker.getClass().getSimpleName(), worker.getIdentity());
			result = worker.execute(task);
			result.setWorkflowInstanceId(task.getWorkflowInstanceId());
			result.setTaskId(task.getTaskId());
            result.setWorkerId(worker.getIdentity());
		} catch (Exception e) {
			logger.error("Unable to execute task {}", task, e);
			if (result == null) {
				task.setStatus(Task.Status.FAILED);
				result = new TaskResult(task);
			}
			handleException(e, result, worker, false, task);
		} finally {
			sw.stop();
		}

		logger.debug("Task {} executed by worker {} at {} with status {}", task.getTaskId(), worker.getClass().getSimpleName(), worker.getIdentity(), task.getStatus());
		updateWithRetry(updateRetryCount, task, result, worker);
	}

	/**
	 *
	 * @return Thread Count for the executor pool
	 */
	public int getThreadCount() {
		return threadCount;
	}

	/**
	 *
	 * @return Size of the queue used by the executor pool
	 */
	public int getWorkerQueueSize() {
		return workerQueueSize;
	}

	/**
	 *
	 * @return sleep time in millisecond before task update retry is done when receiving error from the Conductor server
	 */
	public int getSleepWhenRetry() {
		return sleepWhenRetry;
	}

	/**
	 *
	 * @return Number of times updateTask should be retried when receiving error from Conductor server
	 */
	public int getUpdateRetryCount() {
		return updateRetryCount;
	}

	/**
	 *
	 * @return prefix used for worker names
	 */
	public String getWorkerNamePrefix()
	{
		return workerNamePrefix;
	}

	private void updateWithRetry(int count, Task task, TaskResult result, Worker worker) {

		if(count < 0) {
			worker.onErrorUpdate(task);
			return;
		}

		try{
			client.updateTask(result);
		}catch(Exception e) {
			WorkflowTaskMetrics.taskUpdateErrorCounter(worker.getTaskDefName(), e);
			logger.error(String.format("Unable to update %s, count = %d", result.toString(), count), e);
			try {
				Thread.sleep(sleepWhenRetry);
				updateWithRetry(--count, task, result, worker);
			} catch (InterruptedException ie) {
				// exit retry loop and propagate
				logger.debug("Update interrupted", ie);
				Thread.currentThread().interrupt();
			}
		}
	}

	private void handleException(Throwable t, TaskResult result, Worker worker, boolean updateTask, Task task) {
		logger.error(String.format("Error while executing task %s", task.toString()), t);
		WorkflowTaskMetrics.taskExecutionErrorCounter(worker.getTaskDefName(), t);
		result.setStatus(TaskResult.Status.FAILED);
		result.setReasonForIncompletion("Error while executing the task: " + t);

		StringWriter stringWriter = new StringWriter();
		t.printStackTrace(new PrintWriter(stringWriter));
		result.log(stringWriter.toString());

		updateWithRetry(updateRetryCount, task, result, worker);
	}
}
