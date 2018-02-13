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
package com.netflix.conductor.dao.index;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.elasticsearch.action.ActionFuture;
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
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.index.get.GetField;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.elasticsearch.indices.IndexAlreadyExistsException;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.Uninterruptibles;
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
import com.netflix.conductor.dao.index.query.parser.Expression;
import com.netflix.conductor.dao.index.query.parser.ParserException;
import com.netflix.conductor.metrics.Monitors;

/**
 * @author Viren
 *
 */
@Trace
@Singleton
public class ElasticSearchDAO implements IndexDAO {

	private static Logger log = LoggerFactory.getLogger(ElasticSearchDAO.class);
	
	private static final String WORKFLOW_DOC_TYPE = "workflow";
	
	private static final String TASK_DOC_TYPE = "task";
	
	private static final String LOG_DOC_TYPE = "task";
	
	private static final String EVENT_DOC_TYPE = "event";
	
	private static final String MSG_DOC_TYPE = "message";
	
	private static final String className = ElasticSearchDAO.class.getSimpleName();
	
	private String indexName;
	
	private String logIndexName;
	
	private String logIndexPrefix;

	private ObjectMapper om;
	
	private Client client;
	
	
	private static final TimeZone gmt = TimeZone.getTimeZone("GMT");
	    
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMWW");

	private final ExecutorService executorService;
	
    static {
    	sdf.setTimeZone(gmt);
    }
	
	@Inject
	public ElasticSearchDAO(Client client, Configuration config, ObjectMapper om) {
		this.om = om;
		this.client = client;
		this.indexName = config.getProperty("workflow.elasticsearch.index.name", null);
		
		try {
			
			initIndex();
			updateIndexName(config);
			Executors.newScheduledThreadPool(1).scheduleAtFixedRate(() -> updateIndexName(config), 0, 1, TimeUnit.HOURS);
			
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		int corePoolSize = 6;
		int maximumPoolSize = 12;
		long keepAliveTime = 1L;
		this.executorService = new ThreadPoolExecutor(corePoolSize,
		                                              maximumPoolSize,
		                                              keepAliveTime,
		                                              TimeUnit.MINUTES,
		                                              new LinkedBlockingQueue<>());
	}
	
	private void updateIndexName(Configuration config) {
		this.logIndexPrefix = config.getProperty("workflow.elasticsearch.tasklog.index.name", "task_log");
		this.logIndexName = this.logIndexPrefix + "_" + sdf.format(new Date());

		try {
			client.admin().indices().prepareGetIndex().addIndices(logIndexName).execute().actionGet();
		} catch (IndexNotFoundException infe) {
			try {
				client.admin().indices().prepareCreate(logIndexName).execute().actionGet();
			} catch (IndexAlreadyExistsException ignored) {

			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
		}
	}
	
	/**
	 * Initializes the index with required templates and mappings.
	 */
	private void initIndex() throws Exception {

		//0. Add the index template
		GetIndexTemplatesResponse result = client.admin().indices().prepareGetTemplates("wfe_template").execute().actionGet();
		if(result.getIndexTemplates().isEmpty()) {
			log.info("Creating the index template 'wfe_template'");
			InputStream stream = ElasticSearchDAO.class.getResourceAsStream("/template.json");
			byte[] templateSource = IOUtils.toByteArray(stream);
			
			try {
				client.admin().indices().preparePutTemplate("wfe_template").setSource(templateSource).execute().actionGet();
			}catch(Exception e) {
				log.error(e.getMessage(), e);
			}
		}
	
		//1. Create the required index
		try {
			client.admin().indices().prepareGetIndex().addIndices(indexName).execute().actionGet();
		}catch(IndexNotFoundException infe) {
			try {
				client.admin().indices().prepareCreate(indexName).execute().actionGet();
			}catch(IndexAlreadyExistsException ignored) {}
		}
				
		//2. Mapping for the workflow document type
		GetMappingsResponse response = client.admin().indices().prepareGetMappings(indexName).addTypes(WORKFLOW_DOC_TYPE).execute().actionGet();
		if(response.mappings().isEmpty()) {
			log.info("Adding the workflow type mappings");
			InputStream stream = ElasticSearchDAO.class.getResourceAsStream("/wfe_type.json");
			byte[] bytes = IOUtils.toByteArray(stream);
			String source = new String(bytes);
			try {
				client.admin().indices().preparePutMapping(indexName).setType(WORKFLOW_DOC_TYPE).setSource(source).execute().actionGet();
			}catch(Exception e) {
				log.error(e.getMessage(), e);
			}
		}
	}
	
	@Override
	public void index(Workflow workflow) {
		try {

			String id = workflow.getWorkflowId();
			WorkflowSummary summary = new WorkflowSummary(workflow);
			byte[] doc = om.writeValueAsBytes(summary);
			
			UpdateRequest req = new UpdateRequest(indexName, WORKFLOW_DOC_TYPE, id);
			req.doc(doc);
			req.upsert(doc);
			req.retryOnConflict(5);
			updateWithRetry(req);
 			
		} catch (Throwable e) {
			log.error("Indexing failed {}", e.getMessage(), e);
		}
	}
	
	@Override
	public void index(Task task) {
		// Run all indexing other than workflow indexing in a separate threadpool
		executorService.submit(() -> {
			try {

				String id = task.getTaskId();
				TaskSummary summary = new TaskSummary(task);
				byte[] doc = om.writeValueAsBytes(summary);

				UpdateRequest req = new UpdateRequest(indexName, TASK_DOC_TYPE, id);
				req.doc(doc);
				req.upsert(doc);
				updateWithRetry(req);

			} catch (Throwable e) {
				log.error("Indexing failed {}", e.getMessage(), e);
			}
		});
	}
	
	@Override
	public void add(List<TaskExecLog> taskExecLogs) {
		
		if (taskExecLogs.isEmpty()) {
			return;
		}

		// Run all indexing other than workflow indexing in a separate threadpool
		executorService.submit(() -> {
			int retry = 3;
			while(retry > 0) {
				try {

					BulkRequestBuilder brb = client.prepareBulk();
					for(TaskExecLog taskExecLog : taskExecLogs) {
						IndexRequest request = new IndexRequest(logIndexName, LOG_DOC_TYPE);
						request.source(om.writeValueAsBytes(taskExecLog));
						brb.add(request);
					}
					BulkResponse response = brb.execute().actionGet();
					if(!response.hasFailures()) {
						break;
					}
		            retry--;

				} catch (Throwable e) {
					log.error("Indexing failed {}", e.getMessage(), e);
					retry--;
					if(retry > 0) {
						Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
					}
				}
			}
		});
		
	}
	
	
	public List<TaskExecLog> getTaskLogs(String taskId) {
		
		try {
			
			QueryBuilder qf;
			Expression expression = Expression.fromString("taskId='" + taskId + "'");
			qf = expression.getFilterBuilder();
			
			BoolQueryBuilder filterQuery = QueryBuilders.boolQuery().must(qf);
			QueryStringQueryBuilder stringQuery = QueryBuilders.queryStringQuery("*");
			BoolQueryBuilder fq = QueryBuilders.boolQuery().must(stringQuery).must(filterQuery);
			
			final SearchRequestBuilder srb = client.prepareSearch(logIndexPrefix + "*").setQuery(fq).setTypes(TASK_DOC_TYPE).addSort(SortBuilders.fieldSort("createdTime").order(SortOrder.ASC).unmappedType("long"));
			SearchResponse response = srb.execute().actionGet();
			SearchHit[] hits = response.getHits().getHits();
			List<TaskExecLog> logs = new ArrayList<>(hits.length);
			for(SearchHit hit : hits) {
				String source = hit.getSourceAsString();
				TaskExecLog tel = om.readValue(source, TaskExecLog.class);			
				logs.add(tel);
			}
			
			return logs;
			
		}catch(Exception e) {
			log.error(e.getMessage(), e);
		}
		
		return null;
	}
	
	@Override
	public void addMessage(String queue, Message msg) {

		// Run all indexing other than workflow indexing in a separate threadpool
		executorService.submit(() -> {
			int retry = 3;
			while(retry > 0) {
				try {

					Map<String, Object> doc = new HashMap<>();
					doc.put("messageId", msg.getId());
					doc.put("payload", msg.getPayload());
					doc.put("queue", queue);
					doc.put("created", System.currentTimeMillis());
					IndexRequest request = new IndexRequest(logIndexName, MSG_DOC_TYPE);
					request.source(doc);
		            client.index(request).actionGet();
		            break;

				} catch (Throwable e) {
					log.error("Indexing failed {}", e.getMessage(), e);
					retry--;
					if(retry > 0) {
						Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
					}
				}
			}
		});
		
	}
	
	@Override
	public void add(EventExecution ee) {

		// Run all indexing other than workflow indexing in a separate threadpool
		executorService.submit(() -> {
			try {

				byte[] doc = om.writeValueAsBytes(ee);
				String id = ee.getName() + "." + ee.getEvent() + "." + ee.getMessageId() + "." + ee.getId();
				UpdateRequest req = new UpdateRequest(logIndexName, EVENT_DOC_TYPE, id);
				req.doc(doc);
				req.upsert(doc);
				req.retryOnConflict(5);
				updateWithRetry(req);

			} catch (Throwable e) {
				log.error("Indexing failed {}", e.getMessage(), e);
			}
		});
	
		
	}
	
	private void updateWithRetry(UpdateRequest request) {
		int retry = 3;
		while(retry > 0) {
			try {
				
				client.update(request).actionGet();
				return;
				
			}catch(Exception e) {
				Monitors.error(className, "index");
				log.error("Indexing failed for {}, {}", request.index(), request.type(), e.getMessage());
				retry--;
				if(retry > 0) {
					Uninterruptibles.sleepUninterruptibly(10, TimeUnit.MILLISECONDS);
				}
			}
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
	public void remove(String workflowId) {
		// Run all indexing other than workflow indexing in a separate threadpool
		executorService.submit(() -> {
			try {

				DeleteRequest req = new DeleteRequest(indexName, WORKFLOW_DOC_TYPE, workflowId);
				DeleteResponse response = client.delete(req).actionGet();
				if (!response.isFound()) {
					log.error("Index removal failed - document not found by id " + workflowId);
				}
			} catch (Throwable e) {
				log.error("Index removal failed failed {}", e.getMessage(), e);
				Monitors.error(className, "remove");
			}
		});
	}
	
	@Override
	public void update(String workflowInstanceId, String[] keys, Object[] values) {
		if(keys.length != values.length) {
			throw new IllegalArgumentException("Number of keys and values should be same.");
		}

		// Run all indexing other than workflow indexing in a separate threadpool
		executorService.submit(() -> {
			UpdateRequest request = new UpdateRequest(indexName, WORKFLOW_DOC_TYPE, workflowInstanceId);
			Map<String, Object> source = new HashMap<>();

			for (int i = 0; i < keys.length; i++) {
				String key = keys[i];
				Object value= values[i];
				log.debug("updating {} with {} and {}", workflowInstanceId, key, value);
				source.put(key, value);
			}
			request.doc(source);
			ActionFuture<UpdateResponse> response = client.update(request);
			response.actionGet();
		});
	}
	
	@Override
	public String get(String workflowInstanceId, String fieldToGet) {
		Object value = null;
		GetRequest request = new GetRequest(indexName, WORKFLOW_DOC_TYPE, workflowInstanceId).fields(fieldToGet);
		GetResponse response = client.get(request).actionGet();
		Map<String, GetField> fields = response.getFields();
		if(fields == null) {
			return null;
		}
		GetField field = fields.get(fieldToGet);		
		if(field != null) value = field.getValue();
		if(value != null) {
			return value.toString();
		}
		return null;
	}
	
	private SearchResult<String> search(String structuredQuery, int start, int size, List<String> sortOptions, String freeTextQuery, String docType) throws ParserException {
		QueryBuilder qf = QueryBuilders.matchAllQuery();
		if(StringUtils.isNotEmpty(structuredQuery)) {
			Expression expression = Expression.fromString(structuredQuery);
			qf = expression.getFilterBuilder();
		}
		
		BoolQueryBuilder filterQuery = QueryBuilders.boolQuery().must(qf);
		QueryStringQueryBuilder stringQuery = QueryBuilders.queryStringQuery(freeTextQuery);
		BoolQueryBuilder fq = QueryBuilders.boolQuery().must(stringQuery).must(filterQuery);
		final SearchRequestBuilder srb = client.prepareSearch(indexName).setQuery(fq).setTypes(docType).setNoFields().setFrom(start).setSize(size);
		if(sortOptions != null){
			sortOptions.forEach(sortOption -> {
				SortOrder order = SortOrder.ASC;
				String field = sortOption;
				int indx = sortOption.indexOf(':');
				if(indx > 0){	//Can't be 0, need the field name at-least
					field = sortOption.substring(0, indx);
					order = SortOrder.valueOf(sortOption.substring(indx+1));
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
}
