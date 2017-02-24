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

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.worker.PropertyFactory;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.discovery.EurekaClient;
import com.netflix.servo.monitor.Stopwatch;

/**
 * 
 * @author Viren
 * 
 */
public class WorkflowTaskCoordinator {
	
	private static final Logger logger = LoggerFactory.getLogger(WorkflowTaskCoordinator.class);

	private int threadCount;
    
	private TaskClient client;
	
	private ExecutorService es;
	
	private ScheduledExecutorService ses;
	
	private EurekaClient ec;

	private List<Worker> workers = new LinkedList<>();	
	
	/**
	 * 1 second
	 */
	private int pollInterval = 1000;
	
	/**
	 * 500 ms
	 */
	private int sleepWhenRetry = 500;
	
	private int updateRetryCount = 3;
	
	private int workerQueueSize = 100;
	
	/**
	 * 
	 * @param ec Eureka client - used to identify if the server is in discovery or not.  When the server goes out of discovery, the polling is terminated.  If passed null, discovery check is not done.
	 * @param client Task client used to communicate to conductor server. 
	 * @param threadCount # of threads assigned to the workers.  Should be at-least the size of taskWorkers to avoid starvation in a busy system.
	 * @param taskWorkers workers that will be used for polling work and task execution.
	 * Please see {@link #init()} method.  The method must be called after this constructor for the polling to start.  
	 */
	public WorkflowTaskCoordinator(EurekaClient ec, TaskClient client, int threadCount, Worker...taskWorkers) {
		this(ec, client, threadCount, Arrays.asList(taskWorkers));
	}
	
	
	/**
	 *
	 * @param ec Eureka client - used to identify if the server is in discovery or not.  When the server goes out of discovery, the polling is terminated.  If passed null, discovery check is not done.
	 * @param client TaskClient used to communicate to the conductor server
	 * @param threadCount # of threads assigned to the workers.  Should be at-least the size of taskWorkers to avoid starvation in a busy system.
	 * @param taskWorkers workers that will be used for polling work and task execution.
	 * 
	 * Please see {@link #init()} method.  The method must be called after this constructor for the polling to start.
	 */
	public WorkflowTaskCoordinator(EurekaClient ec, TaskClient client, int threadCount, Iterable<Worker> taskWorkers) {
		this.ec = ec;
		this.client = client;
		this.threadCount = threadCount;
		for (Worker worker : taskWorkers) {
			registerWorker(worker);
		}
	}
	
	/**
	 * 
	 * @param pollInterval polling interval in <b>millisecond</b>.
	 * @return Returns the current instance.
	 */
	public WorkflowTaskCoordinator withPollInterval(int pollInterval) {
		this.pollInterval = pollInterval;
		return this;
	}
	
	/**
	 * 
	 * @param sleepWhenRetry time in millisecond, for which the thread should sleep when task update call fails, before retrying the operation.
	 * @return Returns the current instance.
	 */
	public WorkflowTaskCoordinator withSleepWhenRetry(int sleepWhenRetry) {
		this.sleepWhenRetry = sleepWhenRetry;
		return this;
	}
	
	/**
	 * 
	 * @param updateRetryCount # of attempts to be made when updating task status when update status call fails.
	 * @return Returns the current instance.
	 * @see #withSleepWhenRetry(int)
	 */
	public WorkflowTaskCoordinator withUpdateRetryCount(int updateRetryCount) {
		this.updateRetryCount = updateRetryCount;
		return this;
	}
	
	/**
	 * 
	 * @param workerQueueSize Worker queue size.  
	 * @return Returns the current instance.
	 */
	public WorkflowTaskCoordinator withWorkerQueueSize(int workerQueueSize) {
		this.workerQueueSize = workerQueueSize;
		return this;
	}

	/**
	 * Starts the polling
	 */
	public synchronized void init() {
		
		AtomicInteger count = new AtomicInteger(0);
		this.es = new ThreadPoolExecutor(threadCount, threadCount,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(workerQueueSize),
                new ThreadFactory() {

			@Override
			public Thread newThread(Runnable r) {
				Thread t = new Thread(r);
				t.setName("workflow-worker-" + count.getAndIncrement());
				return t;
			}
		});
		this.ses = Executors.newScheduledThreadPool(workers.size());
		workers.forEach(worker -> {
			ses.scheduleWithFixedDelay(()->pollForTask(worker), pollInterval, pollInterval, TimeUnit.MILLISECONDS);	
		});
		
	}

	/**
	 * 
	 * @param worker Adds a new worker.
	 * If you register a worker after doing {@link #init()}, the no. of threads assigned to the poller will be less than the actual number of workers causing starvation.  
	 *  
	 */
	public void registerWorker(Worker worker) {
		workers.add(worker);
		this.threadCount++;
	}
	
	private void pollForTask(Worker worker) {
		
		if(ec != null && !ec.getInstanceRemoteStatus().equals(InstanceStatus.UP)) {
			logger.debug("Instance is NOT UP in discovery - will not poll");
			return;
		}
		
		if(worker.paused()) {
			WorkflowTaskMetrics.paused(worker.getTaskDefName());
			logger.debug("Worker {} has been paused. Not polling anymore!", worker.getClass());
			return;
		}
		
		logger.debug("Polling {}, count = {} timeout = {} ms", worker.getTaskDefName(), worker.getPollCount(), worker.getLongPollTimeoutInMS());
		
		try{
			
			String taskType = worker.getTaskDefName();
			Stopwatch sw = WorkflowTaskMetrics.pollTimer(worker.getTaskDefName());
			List<Task> tasks = client.poll(taskType, worker.getIdentity(), worker.getPollCount(), worker.getLongPollTimeoutInMS());
			sw.stop();
			logger.debug("Polled {} and receivd {} tasks", worker.getTaskDefName(), tasks.size());
			for(Task task : tasks) {
				es.submit(()->execute(worker, task));	
			}
			
		}catch(RejectedExecutionException qfe) {
			WorkflowTaskMetrics.queueFull(worker.getTaskDefName());
			logger.error("Execution queue is full", qfe);
		} catch (Exception e) {
			WorkflowTaskMetrics.pollingException(worker.getTaskDefName(), e);
			logger.error("Error when polling for task " + e.getMessage(), e);
		}
	}
	
	private void execute(Worker worker, Task task) {
		
		String taskType = task.getTaskDefName();
		if (!taskType.equals(task.getTaskType())) {
			logger.error("Queue name '{}' did not match type of task retrieved '{}' for task id '{}'.", taskType, task.getTaskType(),task.getTaskId());
			return;
		}
		
		try {
			
			if(!worker.preAck(task)) {
				logger.debug("Worker {} decided not to ack the task {}", taskType, task.getTaskId());
				return;
			}
			
			if (!client.ack(task.getTaskId(), worker.getIdentity())) {
				WorkflowTaskMetrics.ackFailed(worker.getTaskDefName());
				logger.error("Ack failed for {}, id {}", taskType, task.getTaskId());
				return;
			}
			
		} catch (Exception e) {
			logger.error("ack exception for " + worker.getTaskDefName(), e);
			WorkflowTaskMetrics.ackException(worker.getTaskDefName(), e);
			return;
		}
		
		TaskResult result = new TaskResult(task);
		result.getLog().getEnvironment().putAll(getEnvData(worker));
		Stopwatch sw = WorkflowTaskMetrics.executionTimer(worker.getTaskDefName());
		
		try {
			
			logger.debug("Executing task {} on worker {}", task, worker.getClass().getSimpleName());
			result = worker.execute(task);
			
		} catch (Exception e) {
			logger.error("Unable to execute task {}", task, e);
			
			WorkflowTaskMetrics.executionException(worker.getTaskDefName(), e);
			result.setStatus(TaskResult.Status.FAILED);
			result.setReasonForIncompletion("Error while executing the task: " + e);
			TaskExecLog execLog = result.getLog();
			execLog.setError(e.getMessage());
			for (StackTraceElement ste : e.getStackTrace()) {
				execLog.getErrorTrace().add(ste.toString());
			}
			
		} finally {
			sw.stop();
		}
		
		logger.debug("Task {} executed by worker {} with status {}", task.getTaskId(), worker.getClass().getSimpleName(), task.getStatus());
		updateWithRetry(updateRetryCount, task, result, worker);
		
	}
	
	private Map<String, Object> getEnvData(Worker worker) {
		String props = worker.getLoggingEnvProps();
		Map<String, Object> data = new HashMap<>();
		if(props == null || props.trim().length() == 0) {
			return data;
		}
		String[] properties = props.split(",");
		String workerName = worker.getTaskDefName();
		for(String property : properties) {
			String value = PropertyFactory.getString(workerName, property, System.getenv(property));
			data.put(property, value);
		}
		
		return data;
	}
	
	private void updateWithRetry(int count, Task task, TaskResult result, Worker worker) {
		
		if(count < 0) {
			worker.onErrorUpdate(task);
			return;
		}
		
		try{
			client.updateTask(result);
			return;
		}catch(Throwable t) {
			WorkflowTaskMetrics.updateTaskError(worker.getTaskDefName(), t);
			logger.error("Unable to update {} on count {}", result, count, t);
			try {
				Thread.sleep(sleepWhenRetry);
				updateWithRetry(--count, task, result, worker);
			} catch (InterruptedException e) {
				// exit retry loop and propagate
				Thread.currentThread().interrupt();
			}
		}
	}

		

}
