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
/**
 * 
 */
package com.netflix.conductor.service;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.metrics.Monitors;

/**
 * @author Viren
 *
 */
@Singleton
public class WorkflowMonitor {

	private static Logger logger = LoggerFactory.getLogger(WorkflowMonitor.class);

	private MetadataDAO metadata;

	private ExecutionDAO edao;

	private QueueDAO queue;

	private ScheduledExecutorService ses;

	private List<TaskDef> tasks;

	private List<WorkflowDef> workflows;

	private int refreshCounter = 0;

	private int metadataRefreshInterval;

	private int statsFrequencyInSeconds;

	@Inject
	public WorkflowMonitor(MetadataDAO metadata, ExecutionDAO edao, QueueDAO queue, Configuration config) {
		this.metadata = metadata;
		this.edao = edao;
		this.queue = queue;
		this.metadataRefreshInterval = config.getIntProperty("workflow.monitor.metadata.refresh.counter", 10);
		this.statsFrequencyInSeconds = config.getIntProperty("workflow.monitor.stats.freq.seconds", 60);
		init();
	}


	public void init() {

		this.ses = Executors.newScheduledThreadPool(1);
		this.ses.scheduleWithFixedDelay(() -> {
			try {

				if (refreshCounter <= 0) {
					workflows = metadata.getAll();
					tasks = metadata.getAllTaskDefs().stream().collect(Collectors.toList());
					refreshCounter = metadataRefreshInterval;
				}

				workflows.forEach(wf -> {
					String name = wf.getName();
					String version = "" + wf.getVersion();
					String ownerApp = wf.getOwnerApp();
					long count = edao.getPendingWorkflowCount(name);
					Monitors.recordRunningWorkflows(count, name, version, ownerApp);
				});

				tasks.forEach(task -> {
					long size = queue.getSize(task.getName());
					long inProgressCount = edao.getInProgressTaskCount(task.getName());
					Monitors.recordQueueDepth(task.getName(), size, task.getOwnerApp());
					if(task.concurrencyLimit() > 0) {
						Monitors.recordTaskInProgress(task.getName(), inProgressCount, task.getOwnerApp());
					}
				});

				refreshCounter--;

			} catch (Exception e) {
				logger.error("Error while publishing scheduled metrics", e);
			}
		}, 120, statsFrequencyInSeconds, TimeUnit.SECONDS);

	}
}
