package com.netflix.conductor.core.execution.mapper;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.ParametersUtils;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.dao.MetadataDAO;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class KafkaPublishTaskMapperTest {

	private ParametersUtils parametersUtils;
	private MetadataDAO metadataDAO;

	private KafkaPublishTaskMapper kafkaTaskMapper;

	@Rule
	public ExpectedException expectedException = ExpectedException.none();

	@Before
	public void setUp() {
		parametersUtils = mock(ParametersUtils.class);
		metadataDAO = mock(MetadataDAO.class);
		kafkaTaskMapper = new KafkaPublishTaskMapper(parametersUtils, metadataDAO);
	}

	@Test
	public void getMappedTasks() {
		//Given
		WorkflowTask taskToSchedule = new WorkflowTask();
		taskToSchedule.setName("kafka_task");
		taskToSchedule.setType(TaskType.KAFKA_PUBLISH.name());
		taskToSchedule.setTaskDefinition(new TaskDef("kafka_task"));
		String taskId = IDGenerator.generate();
		String retriedTaskId = IDGenerator.generate();

		Workflow workflow = new Workflow();
		WorkflowDef workflowDef = new WorkflowDef();
		workflow.setWorkflowDefinition(workflowDef);

		TaskMapperContext taskMapperContext = TaskMapperContext.newBuilder()
				.withWorkflowDefinition(workflowDef)
				.withWorkflowInstance(workflow)
				.withTaskDefinition(new TaskDef())
				.withTaskToSchedule(taskToSchedule)
				.withTaskInput(new HashMap<>())
				.withRetryCount(0)
				.withRetryTaskId(retriedTaskId)
				.withTaskId(taskId)
				.build();

		//when
		List<Task> mappedTasks = kafkaTaskMapper.getMappedTasks(taskMapperContext);

		//Then
		assertEquals(1, mappedTasks.size());
		assertEquals(TaskType.KAFKA_PUBLISH.name(), mappedTasks.get(0).getTaskType());
	}

	@Test
	public void getMappedTasks_WithoutTaskDef() {
		//Given
		WorkflowTask taskToSchedule = new WorkflowTask();
		taskToSchedule.setName("kafka_task");
		taskToSchedule.setType(TaskType.KAFKA_PUBLISH.name());
		String taskId = IDGenerator.generate();
		String retriedTaskId = IDGenerator.generate();

		Workflow workflow = new Workflow();
		WorkflowDef workflowDef = new WorkflowDef();
		workflow.setWorkflowDefinition(workflowDef);

		TaskDef taskdefinition = new TaskDef();
		String testExecutionNameSpace = "testExecutionNameSpace";
		taskdefinition.setExecutionNameSpace(testExecutionNameSpace);
		String testIsolationGroupId = "testIsolationGroupId";
		taskdefinition.setIsolationGroupId(testIsolationGroupId);
		TaskMapperContext taskMapperContext = TaskMapperContext.newBuilder()
				.withWorkflowDefinition(workflowDef)
				.withWorkflowInstance(workflow)
				.withTaskDefinition(taskdefinition)
				.withTaskToSchedule(taskToSchedule)
				.withTaskInput(new HashMap<>())
				.withRetryCount(0)
				.withRetryTaskId(retriedTaskId)
				.withTaskId(taskId)
				.build();

		//when
		List<Task> mappedTasks = kafkaTaskMapper.getMappedTasks(taskMapperContext);

		//Then
		assertEquals(1, mappedTasks.size());
		assertEquals(TaskType.KAFKA_PUBLISH.name(), mappedTasks.get(0).getTaskType());
		assertEquals(testExecutionNameSpace, mappedTasks.get(0).getExecutionNameSpace());
		assertEquals(testIsolationGroupId, mappedTasks.get(0).getIsolationGroupId());
	}
}
