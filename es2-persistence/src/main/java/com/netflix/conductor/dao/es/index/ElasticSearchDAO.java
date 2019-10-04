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
package com.netflix.conductor.dao.es.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.core.execution.ApplicationException.Code;
import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.dao.es.index.query.parser.Expression;
import com.netflix.conductor.dao.es.index.query.parser.ParserException;
import com.netflix.conductor.dao.es.utils.RetryUtil;
import com.netflix.conductor.metrics.Monitors;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.elasticsearch.action.admin.indices.template.get.GetIndexTemplatesResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.index.get.GetField;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.elasticsearch.indices.IndexAlreadyExistsException;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Viren
 *
 */
@Trace
@Singleton
public class ElasticSearchDAO implements IndexDAO {

	private static final Logger logger = LoggerFactory.getLogger(ElasticSearchDAO.class);

	private static final String WORKFLOW_DOC_TYPE = "workflow";

	private static final String TASK_DOC_TYPE = "task";

	private static final String LOG_DOC_TYPE = "task_log";

	private static final String EVENT_DOC_TYPE = "event";

	private static final String MSG_DOC_TYPE = "message";

	private static final String className = ElasticSearchDAO.class.getSimpleName();

	private static final int RETRY_COUNT = 3;

	private final int archiveSearchBatchSize;

	private String indexName;

	private String logIndexName;

	private String logIndexPrefix;

	private ObjectMapper objectMapper;

	private Client elasticSearchClient;

	private static final TimeZone GMT = TimeZone.getTimeZone("GMT");

	private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyyMMWW");

	private final ExecutorService executorService;
	private final ExecutorService logExecutorService;

	private ConcurrentHashMap<String, BulkRequests> bulkRequests;

	private final int indexBatchSize;
	private final int asyncBufferFlushTimeout;

	static {
		SIMPLE_DATE_FORMAT.setTimeZone(GMT);
	}

	@Inject
	public ElasticSearchDAO(Client elasticSearchClient, Configuration config, ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
		this.elasticSearchClient = elasticSearchClient;
		this.indexName = config.getProperty("workflow.elasticsearch.index.name", null);
		this.archiveSearchBatchSize = config.getIntProperty("workflow.elasticsearch.archive.search.batchSize", 5000);

		this.bulkRequests = new ConcurrentHashMap<>();
		this.indexBatchSize = config.getIntProperty("workflow.elasticsearch.index.batchSize", 1);
		this.asyncBufferFlushTimeout = config.getIntProperty("workflow.elasticsearch.async.buffer.flush.timeout.seconds", 10);

		int corePoolSize = 4;
		int maximumPoolSize = config.getIntProperty("workflow.elasticsearch.async.dao.max.pool.size", 12);
		long keepAliveTime = 1L;
		int workerQueueSize = config.getIntProperty("workflow.elasticsearch.async.dao.worker.queue.size", 100);
		this.executorService = new ThreadPoolExecutor(corePoolSize,
			maximumPoolSize,
			keepAliveTime,
			TimeUnit.MINUTES,
			new LinkedBlockingQueue<>(workerQueueSize),
			(runnable, executor) -> {
				logger.warn("Request {} to async dao discarded in executor {}", runnable, executor);
				Monitors.recordDiscardedIndexingCount("indexQueue");
			});

		corePoolSize = 1;
		maximumPoolSize = 2;
		keepAliveTime = 30L;
		this.logExecutorService = new ThreadPoolExecutor(corePoolSize,
			maximumPoolSize,
			keepAliveTime,
			TimeUnit.SECONDS,
			new LinkedBlockingQueue<>(workerQueueSize),
			(runnable, executor) -> {
				logger.warn("Request {} to async log dao discarded in executor {}", runnable, executor);
				Monitors.recordDiscardedIndexingCount("logQueue");
			});

		Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(this::flushBulkRequests, 60, 30, TimeUnit.SECONDS);

		try {
			initIndex();
			updateIndexName(config);
			Executors.newScheduledThreadPool(1).scheduleAtFixedRate(() -> updateIndexName(config), 0, 1, TimeUnit.HOURS);

		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	private void updateIndexName(Configuration config) {
		this.logIndexPrefix = config.getProperty("workflow.elasticsearch.tasklog.index.name", "task_log");
		this.logIndexName = this.logIndexPrefix + "_" + SIMPLE_DATE_FORMAT.format(new Date());

		try {
			elasticSearchClient.admin().indices().prepareGetIndex().addIndices(logIndexName).execute().actionGet();
		} catch (IndexNotFoundException infe) {
			try {
				elasticSearchClient.admin().indices().prepareCreate(logIndexName).execute().actionGet();
			} catch (IndexAlreadyExistsException ignored) {

			} catch (Exception e) {
				logger.error(e.getMessage(), e);
			}
		}
	}

	/**
	 * Initializes the index with required templates and mappings.
	 */
	private void initIndex() throws Exception {

		//0. Add the tasklog template
		GetIndexTemplatesResponse result = elasticSearchClient.admin().indices().prepareGetTemplates("tasklog_template").execute().actionGet();
		if (result.getIndexTemplates().isEmpty()) {
			logger.info("Creating the index template 'tasklog_template'");
			InputStream stream = ElasticSearchDAO.class.getResourceAsStream("/template_tasklog.json");
			byte[] templateSource = IOUtils.toByteArray(stream);

			try {
				elasticSearchClient.admin().indices().preparePutTemplate("tasklog_template").setSource(templateSource).execute().actionGet();
			} catch (Exception e) {
				logger.error("Failed to init tasklog_template", e);
			}
		}

		//1. Create the required index
		try {
			elasticSearchClient.admin().indices().prepareGetIndex().addIndices(indexName).execute().actionGet();
		} catch (IndexNotFoundException infe) {
			try {
				elasticSearchClient.admin().indices().prepareCreate(indexName).execute().actionGet();
			} catch (IndexAlreadyExistsException ignored) {
			}
		}

		//2. Add Mappings for the workflow document type
		GetMappingsResponse getMappingsResponse = elasticSearchClient.admin().indices().prepareGetMappings(indexName).addTypes(WORKFLOW_DOC_TYPE).execute().actionGet();
		if (getMappingsResponse.mappings().isEmpty()) {
			logger.info("Adding the workflow type mappings");
			InputStream stream = ElasticSearchDAO.class.getResourceAsStream("/mappings_docType_workflow.json");
			byte[] bytes = IOUtils.toByteArray(stream);
			String source = new String(bytes);
			try {
				elasticSearchClient.admin().indices().preparePutMapping(indexName).setType(WORKFLOW_DOC_TYPE).setSource(source).execute().actionGet();
			} catch (Exception e) {
				logger.error(e.getMessage(), e);
			}
		}

		//3. Add Mappings for task document type
		getMappingsResponse = elasticSearchClient.admin().indices().prepareGetMappings(indexName).addTypes(TASK_DOC_TYPE).execute().actionGet();
		if (getMappingsResponse.mappings().isEmpty()) {
			logger.info("Adding the task type mappings");
			InputStream stream = ElasticSearchDAO.class.getResourceAsStream("/mappings_docType_task.json");
			byte[] bytes = IOUtils.toByteArray(stream);
			String source = new String(bytes);
			try {
				elasticSearchClient.admin().indices().preparePutMapping(indexName).setType(TASK_DOC_TYPE).setSource(source).execute().actionGet();
			} catch (Exception e) {
				logger.error(e.getMessage(), e);
			}
		}
	}

	@Override
	public void indexWorkflow(Workflow workflow) {
		try {
			long startTime = Instant.now().toEpochMilli();
			String workflowId = workflow.getWorkflowId();
			WorkflowSummary summary = new WorkflowSummary(workflow);
			byte[] doc = objectMapper.writeValueAsBytes(summary);

			UpdateRequest req = new UpdateRequest(indexName, WORKFLOW_DOC_TYPE, workflowId);
			req.doc(doc);
			req.upsert(doc);
			req.retryOnConflict(5);

			new RetryUtil<UpdateResponse>().retryOnException(
				() -> elasticSearchClient.update(req).actionGet(),
				null,
				null,
				RETRY_COUNT,
				"Indexing workflow document: " + workflow.getWorkflowId(),
				"indexWorkflow"
			);

			long endTime = Instant.now().toEpochMilli();
			logger.debug("Time taken {} for indexing workflow: {}", endTime - startTime, workflow.getWorkflowId());
			Monitors.recordESIndexTime("index_workflow", WORKFLOW_DOC_TYPE, endTime - startTime);
			Monitors.recordWorkerQueueSize("indexQueue", ((ThreadPoolExecutor) executorService).getQueue().size());
		} catch (Exception e) {
			logger.error("Failed to index workflow: {}", workflow.getWorkflowId(), e);
		}
	}

	@Override
	public CompletableFuture<Void> asyncIndexWorkflow(Workflow workflow) {
		return CompletableFuture.runAsync(() -> indexWorkflow(workflow), executorService);
	}

	@Override
	public void indexTask(Task task) {
		try {
			long startTime = Instant.now().toEpochMilli();
			String id = task.getTaskId();
			TaskSummary summary = new TaskSummary(task);
			byte[] doc = objectMapper.writeValueAsBytes(summary);

			UpdateRequest req = new UpdateRequest(indexName, TASK_DOC_TYPE, id);
			req.doc(doc);
			req.upsert(doc);
			logger.debug("Indexing task document: {} for workflow: {}" + id, task.getWorkflowInstanceId());
			indexObject(req, TASK_DOC_TYPE);
			long endTime = Instant.now().toEpochMilli();
			logger.debug("Time taken {} for  indexing task:{} in workflow: {}", endTime - startTime, task.getTaskId(), task.getWorkflowInstanceId());
			Monitors.recordESIndexTime("index_task", TASK_DOC_TYPE, endTime - startTime);
			Monitors.recordWorkerQueueSize("indexQueue", ((ThreadPoolExecutor) executorService).getQueue().size());
		} catch (Exception e) {
			logger.error("Failed to index task: {}", task.getTaskId(), e);
		}
	}

	@Override
	public CompletableFuture<Void> asyncIndexTask(Task task) {
		return CompletableFuture.runAsync(() -> indexTask(task), executorService);
	}

	@Override
	public void addTaskExecutionLogs(List<TaskExecLog> taskExecLogs) {
		if (taskExecLogs.isEmpty()) {
			return;
		}

		try {
			long startTime = Instant.now().toEpochMilli();
			BulkRequestBuilder bulkRequestBuilder = elasticSearchClient.prepareBulk();
			for (TaskExecLog taskExecLog : taskExecLogs) {
				IndexRequest request = new IndexRequest(logIndexName, LOG_DOC_TYPE);
				request.source(objectMapper.writeValueAsBytes(taskExecLog));
				bulkRequestBuilder.add(request);
			}
			new RetryUtil<BulkResponse>().retryOnException(
				() -> bulkRequestBuilder.execute().actionGet(),
				null,
				BulkResponse::hasFailures,
				RETRY_COUNT,
				"Indexing task execution logs",
				"addTaskExecutionLogs"
			);
			long endTime = Instant.now().toEpochMilli();
			logger.debug("Time taken {} for indexing taskExecutionLogs", endTime - startTime);
			Monitors.recordESIndexTime("index_task_execution_logs", LOG_DOC_TYPE, endTime - startTime);
			Monitors.recordWorkerQueueSize("logQueue", ((ThreadPoolExecutor) logExecutorService).getQueue().size());
		} catch (Exception e) {
			List<String> taskIds = taskExecLogs.stream()
				.map(TaskExecLog::getTaskId)
				.collect(Collectors.toList());
			logger.error("Failed to index task execution logs for tasks: {}", taskIds, e);
		}
	}

	@Override
	public CompletableFuture<Void> asyncAddTaskExecutionLogs(List<TaskExecLog> logs) {
		return CompletableFuture.runAsync(() -> addTaskExecutionLogs(logs), logExecutorService);
	}

	@Override
	public List<TaskExecLog> getTaskExecutionLogs(String taskId) {

		try {

			QueryBuilder qf;
			Expression expression = Expression.fromString("taskId='" + taskId + "'");
			qf = expression.getFilterBuilder();

			BoolQueryBuilder filterQuery = QueryBuilders.boolQuery().must(qf);
			QueryStringQueryBuilder stringQuery = QueryBuilders.queryStringQuery("*");
			BoolQueryBuilder fq = QueryBuilders.boolQuery().must(stringQuery).must(filterQuery);

			final SearchRequestBuilder srb = elasticSearchClient.prepareSearch(logIndexPrefix + "*").setQuery(fq).setTypes(LOG_DOC_TYPE).addSort(SortBuilders.fieldSort("createdTime").order(SortOrder.ASC).unmappedType("long"));
			SearchResponse response = srb.execute().actionGet();
			SearchHit[] hits = response.getHits().getHits();
			List<TaskExecLog> logs = new ArrayList<>(hits.length);
			for (SearchHit hit : hits) {
				String source = hit.getSourceAsString();
				TaskExecLog tel = objectMapper.readValue(source, TaskExecLog.class);
				logs.add(tel);
			}

			return logs;

		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}

		return null;
	}

	@Override
	public void addMessage(String queue, Message msg) {
		try {
			long startTime = Instant.now().toEpochMilli();
			Map<String, Object> doc = new HashMap<>();
			doc.put("messageId", msg.getId());
			doc.put("payload", msg.getPayload());
			doc.put("queue", queue);
			doc.put("created", System.currentTimeMillis());

			UpdateRequest request = new UpdateRequest(logIndexName, MSG_DOC_TYPE, msg.getId());
			request.doc(doc, XContentType.JSON);
			request.upsert(doc, XContentType.JSON);
			indexObject(request, MSG_DOC_TYPE);
			long endTime = Instant.now().toEpochMilli();
			logger.debug("Time taken {} for  indexing message: {}", endTime - startTime, msg.getId());
			Monitors.recordESIndexTime("add_message", MSG_DOC_TYPE, endTime - startTime);
		}catch (Exception e) {
			logger.error("Failed to index message: {}", msg.getId(), e);
		}
	}

	@Override
	public void addEventExecution(EventExecution eventExecution) {
		try {
			long startTime = Instant.now().toEpochMilli();
			byte[] doc = objectMapper.writeValueAsBytes(eventExecution);
			String id = eventExecution.getName() + "." + eventExecution.getEvent() + "." + eventExecution.getMessageId() + "." + eventExecution.getId();
			UpdateRequest req = new UpdateRequest(logIndexName, EVENT_DOC_TYPE, id);
			req.doc(doc);
			req.upsert(doc);
			req.retryOnConflict(5);
			indexObject(req, EVENT_DOC_TYPE);
			long endTime = Instant.now().toEpochMilli();
			logger.debug("Time taken {} for indexing event execution: {}", endTime - startTime, eventExecution.getId());
			Monitors.recordESIndexTime("add_event_execution", EVENT_DOC_TYPE, endTime - startTime);
		} catch (Exception e) {
			logger.error("Failed to index event execution: {}", eventExecution.getId(), e);
		}
	}

	@Override
	public CompletableFuture<Void> asyncAddEventExecution(EventExecution eventExecution) {
		return CompletableFuture.runAsync(() -> addEventExecution(eventExecution), logExecutorService);
	}

	private void indexObject(UpdateRequest req, String docType) {
		if (bulkRequests.get(docType) == null) {
			bulkRequests.put(docType, new BulkRequests(System.currentTimeMillis(), elasticSearchClient.prepareBulk()));
		}
		bulkRequests.get(docType).getBulkRequestBuilder().add(req);
		if (bulkRequests.get(docType).getBulkRequestBuilder().numberOfActions() >= this.indexBatchSize) {
			indexBulkRequest(docType);
		}
	}

	private void indexBulkRequest(String docType) {
		updateWithRetry(bulkRequests.get(docType).getBulkRequestBuilder(), docType);
		bulkRequests.put(docType, new BulkRequests(System.currentTimeMillis(), elasticSearchClient.prepareBulk()));
	}

	private synchronized void updateWithRetry(BulkRequestBuilder request, String docType) {
		try {
			long startTime = Instant.now().toEpochMilli();
			new RetryUtil<BulkResponse>().retryOnException(
				() -> request.execute().actionGet(),
				null,
				BulkResponse::hasFailures,
				RETRY_COUNT,
				"Bulk Indexing "+ docType,
				"indexObject"
			);
			long endTime = Instant.now().toEpochMilli();
			logger.debug("Time taken {} for indexing object of type: {}", endTime - startTime, docType);
			Monitors.recordESIndexTime("index_object", docType, endTime - startTime);
		} catch (Exception e) {
			Monitors.error(className, "index");
			logger.error("Failed to index object of type: {}", docType, e);
		}
	}

	@Override
	public SearchResult<String> searchWorkflows(String query, String freeText, int start, int count, List<String> sort) {
		try {
			return search(query, start, count, sort, freeText, WORKFLOW_DOC_TYPE);
		} catch (ParserException e) {
			throw new ApplicationException(Code.BACKEND_ERROR, e.getMessage(), e);
		}
	}

	@Override
	public SearchResult<String> searchTasks(String query, String freeText, int start, int count, List<String> sort) {
		try {
			return search(query, start, count, sort, freeText, TASK_DOC_TYPE);
		} catch (ParserException e) {
			throw new ApplicationException(Code.BACKEND_ERROR, e.getMessage(), e);
		}
	}

	@Override
	public void removeWorkflow(String workflowId) {
		try {
			long startTime = Instant.now().toEpochMilli();
			DeleteRequest req = new DeleteRequest(indexName, WORKFLOW_DOC_TYPE, workflowId);
			DeleteResponse response = elasticSearchClient.delete(req).actionGet();
			if (!response.isFound()) {
				logger.error("Index removal failed - document not found by id " + workflowId);
			}
			long endTime = Instant.now().toEpochMilli();
			logger.debug("Time taken {} for removing workflow: {}", endTime - startTime, workflowId);
			Monitors.recordESIndexTime("remove_workflow", WORKFLOW_DOC_TYPE, endTime - startTime);
			Monitors.recordWorkerQueueSize("indexQueue", ((ThreadPoolExecutor) executorService).getQueue().size());
		} catch (Exception e) {
			logger.error("Failed to remove workflow {} from index", workflowId, e);
			Monitors.error(className, "remove");
		}
	}

	@Override
	public CompletableFuture<Void> asyncRemoveWorkflow(String workflowId) {
		return CompletableFuture.runAsync(() -> removeWorkflow(workflowId), executorService);
	}

	@Override
	public void updateWorkflow(String workflowInstanceId, String[] keys, Object[] values) {
		if (keys.length != values.length) {
			throw new ApplicationException(Code.INVALID_INPUT,
				"Number of keys and values do not match");
		}

		long startTime = Instant.now().toEpochMilli();
		UpdateRequest request = new UpdateRequest(indexName, WORKFLOW_DOC_TYPE, workflowInstanceId);
		Map<String, Object> source = IntStream.range(0, keys.length).boxed()
				.collect(Collectors.toMap(i -> keys[i], i -> values[i]));
		request.doc(source);
		logger.debug("Updating workflow {} in elasticsearch index: {}", workflowInstanceId, indexName);
		new RetryUtil<>().retryOnException(
			() -> elasticSearchClient.update(request).actionGet(),
			null,
			null,
			RETRY_COUNT,
			"Updating index for doc_type workflow",
			"updateWorkflow"
		);

		long endTime = Instant.now().toEpochMilli();
		logger.debug("Time taken {} for updating workflow: {}", endTime - startTime, workflowInstanceId);
		Monitors.recordESIndexTime("update_workflow", WORKFLOW_DOC_TYPE, endTime - startTime);
		Monitors.recordWorkerQueueSize("indexQueue", ((ThreadPoolExecutor) executorService).getQueue().size());
	}

	@Override
	public CompletableFuture<Void> asyncUpdateWorkflow(String workflowInstanceId, String[] keys, Object[] values) {
		return CompletableFuture.runAsync(() -> updateWorkflow(workflowInstanceId, keys, values), executorService);
	}

	@Override
	public String get(String workflowInstanceId, String fieldToGet) {
		Object value = null;
		GetRequest request = new GetRequest(indexName, WORKFLOW_DOC_TYPE, workflowInstanceId).fields(fieldToGet);
		GetResponse response = elasticSearchClient.get(request).actionGet();
		Map<String, GetField> fields = response.getFields();
		if (fields == null) {
			return null;
		}
		GetField field = fields.get(fieldToGet);
		if (field != null) value = field.getValue();
		if (value != null) {
			return value.toString();
		}
		return null;
	}

	private SearchResult<String> search(String structuredQuery, int start, int size, List<String> sortOptions, String freeTextQuery, String docType) throws ParserException {
		QueryBuilder qf = QueryBuilders.matchAllQuery();
		if (StringUtils.isNotEmpty(structuredQuery)) {
			Expression expression = Expression.fromString(structuredQuery);
			qf = expression.getFilterBuilder();
		}

		BoolQueryBuilder filterQuery = QueryBuilders.boolQuery().must(qf);
		QueryStringQueryBuilder stringQuery = QueryBuilders.queryStringQuery(freeTextQuery);
		BoolQueryBuilder fq = QueryBuilders.boolQuery().must(stringQuery).must(filterQuery);
		final SearchRequestBuilder srb = elasticSearchClient.prepareSearch(indexName).setQuery(fq).setTypes(docType).setNoFields().setFrom(start).setSize(size);
		if (sortOptions != null) {
			sortOptions.forEach(sortOption -> {
				SortOrder order = SortOrder.ASC;
				String field = sortOption;
				int indx = sortOption.indexOf(':');
				if (indx > 0) {    //Can't be 0, need the field name at-least
					field = sortOption.substring(0, indx);
					order = SortOrder.valueOf(sortOption.substring(indx + 1));
				}
				srb.addSort(field, order);
			});
		}
		List<String> result = new LinkedList<>();
		SearchResponse response = srb.execute().actionGet();
		response.getHits().forEach(hit -> result.add(hit.getId()));
		long count = response.getHits().getTotalHits();
		return new SearchResult<>(count, result);
	}


	@Override
	public List<String> searchArchivableWorkflows(String indexName, long archiveTtlDays) {
		QueryBuilder q = QueryBuilders.boolQuery()
			.must(QueryBuilders.rangeQuery("endTime").lt(LocalDate.now().minusDays(archiveTtlDays).toString()))
			.should(QueryBuilders.termQuery("status", "COMPLETED"))
			.should(QueryBuilders.termQuery("status", "FAILED"))
			.should(QueryBuilders.termQuery("status", "TIMED_OUT"))
			.should(QueryBuilders.termQuery("status", "TERMINATED"))
			.mustNot(QueryBuilders.existsQuery("archived"))
			.minimumNumberShouldMatch(1);

		SearchRequestBuilder s = elasticSearchClient.prepareSearch(indexName)
				.setTypes("workflow")
				.setQuery(q)
				.addSort("endTime", SortOrder.ASC)
				.setSize(archiveSearchBatchSize);

		SearchResponse response = s.execute().actionGet();

		SearchHits hits = response.getHits();
		logger.info("Archive search totalHits - {}", hits.getTotalHits());

		List<String> ids = new LinkedList<>();
		for (SearchHit hit : hits.getHits()) {
			ids.add(hit.getId());
		}
		return ids;
	}

	//copy paste from com.netflix.conductor.dao.es.index.ElasticSearchDAO5.searchRecentIncompletedWorkflows
	public List<String> searchRecentRunningWorkflows(int lastModifiedHoursAgoFrom, int lastModifiedHoursAgoTo) {
		DateTime dateTime = new DateTime();
		QueryBuilder q = QueryBuilders.boolQuery()
				.must(QueryBuilders.rangeQuery("updateTime")
						.gt(dateTime.minusHours(lastModifiedHoursAgoFrom)))
				.must(QueryBuilders.rangeQuery("updateTime")
						.lt(dateTime.minusHours(lastModifiedHoursAgoTo)))
				.must(QueryBuilders.termQuery("status", "RUNNING"));

		SearchRequestBuilder s = elasticSearchClient.prepareSearch(indexName)
				.setTypes("workflow")
				.setQuery(q)
				.setSize(5000)
				.addSort("updateTime", SortOrder.ASC);

		SearchResponse response = s.execute().actionGet();
		SearchHits hits = response.getHits();
		List<String> ids = new LinkedList<>();
		for (SearchHit hit : hits.getHits()) {
			ids.add(hit.getId());
		}
		return ids;
	}

	/**
	 * Flush the buffers if bulk requests have not been indexed for the past "workflow.elasticsearch.async.buffer.flush.timeout.seconds" seconds
	 * This is to prevent data loss in case the instance is terminated, while the buffer still holds documents to be indexed.
	 */
	private void flushBulkRequests() {
		bulkRequests.entrySet().stream()
			.filter(entry -> (System.currentTimeMillis() - entry.getValue().getLastFlushTime()) >= asyncBufferFlushTimeout * 1000)
			.forEach(entry -> {
				logger.debug("Flushing bulk request buffer for type {}, size: {}", entry.getKey(), entry.getValue().getBulkRequestBuilder().numberOfActions());
				indexBulkRequest(entry.getKey());
			});
	}

	private static class BulkRequests {
		private long lastFlushTime;
		private BulkRequestBuilder bulkRequestBuilder;

		public long getLastFlushTime() {
			return lastFlushTime;
		}

		public void setLastFlushTime(long lastFlushTime) {
			this.lastFlushTime = lastFlushTime;
		}

		public BulkRequestBuilder getBulkRequestBuilder() {
			return bulkRequestBuilder;
		}

		public void setBulkRequestBuilder(BulkRequestBuilder bulkRequestBuilder) {
			this.bulkRequestBuilder = bulkRequestBuilder;
		}

		BulkRequests(long lastFlushTime, BulkRequestBuilder bulkRequestBuilder) {
			this.lastFlushTime = lastFlushTime;
			this.bulkRequestBuilder = bulkRequestBuilder;
		}
	}
}