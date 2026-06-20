/*
 * Copyright 2026 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.conductoross.conductor.ai.a2a.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.conductoross.conductor.ai.a2a.A2AMetrics;
import org.conductoross.conductor.ai.a2a.model.A2AMessage;
import org.conductoross.conductor.ai.a2a.model.A2ATask;
import org.conductoross.conductor.ai.a2a.model.AgentCapabilities;
import org.conductoross.conductor.ai.a2a.model.AgentCard;
import org.conductoross.conductor.ai.a2a.model.AgentProvider;
import org.conductoross.conductor.ai.a2a.model.AgentSkill;
import org.conductoross.conductor.ai.a2a.model.Artifact;
import org.conductoross.conductor.ai.a2a.model.Part;
import org.conductoross.conductor.ai.a2a.model.TaskState;
import org.conductoross.conductor.ai.a2a.model.TaskStatus;
import org.conductoross.conductor.config.A2AServerEnabledCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.IdempotencyStrategy;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.service.MetadataService;
import com.netflix.conductor.service.TaskService;
import com.netflix.conductor.service.WorkflowService;

/**
 * Exposes Conductor workflows as A2A agents (server side). One workflow = one focused A2A agent.
 *
 * <p>Builds the Agent Card from a {@link WorkflowDef}, starts a workflow on {@code message/send}
 * (with the inbound A2A {@code messageId} as the durable idempotency key), and maps a workflow
 * execution onto an A2A task for {@code tasks/get}/{@code tasks/cancel}. The execution's durability
 * (crash-safe, resumable) is inherited from the engine.
 */
@Component
@Conditional(A2AServerEnabledCondition.class)
public class A2AWorkflowAgent {

    /** Input keys injected into the workflow so it can read the raw A2A message. */
    static final String INPUT_TEXT = "_a2a_text";

    static final String INPUT_MESSAGE_ID = "_a2a_message_id";
    static final String INPUT_CONTEXT_ID = "_a2a_context_id";
    static final String METADATA_ENABLED = "a2a.enabled";
    static final String METADATA_TAGS = "a2a.tags";

    private final WorkflowService workflowService;
    private final MetadataService metadataService;
    private final TaskService taskService;
    private final A2AServerProperties properties;

    public A2AWorkflowAgent(
            WorkflowService workflowService,
            MetadataService metadataService,
            TaskService taskService,
            A2AServerProperties properties) {
        this.workflowService = workflowService;
        this.metadataService = metadataService;
        this.taskService = taskService;
        this.properties = properties;
    }

    // ---- exposure ----------------------------------------------------------------------------

    public boolean isExposed(String workflowName) {
        WorkflowDef def = latestDef(workflowName);
        return def != null && isExposed(def);
    }

    private boolean isExposed(WorkflowDef def) {
        if (properties.getExposedWorkflows().contains(def.getName())) {
            return true;
        }
        Object flag = def.getMetadata() == null ? null : def.getMetadata().get(METADATA_ENABLED);
        return flag instanceof Boolean
                ? (Boolean) flag
                : flag != null && Boolean.parseBoolean(flag.toString());
    }

    public List<WorkflowDef> exposedWorkflows() {
        return metadataService.getWorkflowDefsLatestVersions().stream()
                .filter(this::isExposed)
                .collect(Collectors.toList());
    }

    // ---- agent card --------------------------------------------------------------------------

    public AgentCard agentCard(String workflowName, String requestBaseUrl) {
        WorkflowDef def = requireExposed(workflowName);
        return buildCard(def, requestBaseUrl);
    }

    private AgentCard buildCard(WorkflowDef def, String requestBaseUrl) {
        String description =
                def.getDescription() != null
                        ? def.getDescription()
                        : "Conductor workflow '" + def.getName() + "' exposed as an A2A agent";

        AgentCard card = new AgentCard();
        card.setName(def.getName());
        card.setDescription(description);
        card.setUrl(agentUrl(def.getName(), requestBaseUrl));
        card.setVersion(String.valueOf(def.getVersion()));
        card.setProtocolVersion("0.3.0");
        card.setPreferredTransport("JSONRPC");
        card.setDefaultInputModes(properties.getDefaultInputModes());
        card.setDefaultOutputModes(properties.getDefaultOutputModes());
        card.setCapabilities(new AgentCapabilities()); // streaming/push: false (server-side v1)

        AgentProvider provider = new AgentProvider();
        provider.setOrganization(properties.getProviderOrganization());
        provider.setUrl(baseUrl(requestBaseUrl));
        card.setProvider(provider);

        AgentSkill skill = new AgentSkill();
        skill.setId(def.getName());
        skill.setName(def.getName());
        skill.setDescription(description);
        skill.setTags(tags(def));
        skill.setInputModes(properties.getDefaultInputModes());
        skill.setOutputModes(properties.getDefaultOutputModes());
        card.setSkills(List.of(skill));
        return card;
    }

    // ---- A2A methods -------------------------------------------------------------------------

    public A2ATask sendMessage(String workflowName, A2AMessage message) {
        WorkflowDef def = requireExposed(workflowName);

        // Multi-turn: a follow-up message that carries an existing taskId resumes the paused
        // execution rather than starting a new one. The taskId is the workflowId we returned on
        // the prior input-required response; the message content becomes the awaited input.
        if (message != null && message.getTaskId() != null && !message.getTaskId().isBlank()) {
            return resume(workflowName, message);
        }

        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName(def.getName());
        request.setInput(buildInput(message));
        if (message != null && message.getContextId() != null) {
            request.setCorrelationId(message.getContextId());
        }
        // Durable idempotency: a retried message/send returns the existing execution. Namespace
        // the key with the workflow name so an identical messageId sent to two different exposed
        // agents cannot collide on the idempotency lookup.
        if (message != null && message.getMessageId() != null) {
            request.setIdempotencyKey(def.getName() + ":" + message.getMessageId());
            request.setIdempotencyStrategy(IdempotencyStrategy.RETURN_EXISTING);
        }

        String workflowId = workflowService.startWorkflow(request);
        return toA2ATask(loadWorkflow(workflowId, def.getName(), false));
    }

    /**
     * Resume a paused execution with a follow-up A2A message. The {@code taskId} on the message is
     * the workflowId; we complete the pending HUMAN/WAIT task (the thing the workflow is blocked
     * on) with the message content as its output, which advances the durable execution. If the
     * workflow is already terminal, or is running but not awaiting input, we return its current
     * state unchanged (no duplicate workflow is started).
     */
    private A2ATask resume(String workflowName, A2AMessage message) {
        String workflowId = message.getTaskId();
        // loadWorkflow validates that this execution belongs to the named exposed agent.
        Workflow workflow = loadWorkflow(workflowId, workflowName, true);
        if (workflow.getStatus() != null && workflow.getStatus().isTerminal()) {
            return toA2ATask(workflow);
        }
        Task blocking = findBlockingTask(workflow);
        if (blocking == null) {
            // Running but not awaiting input — the agent is busy working; report current state.
            return toA2ATask(workflow);
        }
        taskService.updateTask(
                workflowId,
                blocking.getReferenceTaskName(),
                TaskResult.Status.COMPLETED,
                "a2a-resume",
                buildInput(message));
        A2AMetrics.serverResume();
        return toA2ATask(loadWorkflow(workflowId, workflowName, true));
    }

    public A2ATask getTask(String workflowName, String workflowId) {
        requireExposed(workflowName);
        return toA2ATask(loadWorkflow(workflowId, workflowName, true));
    }

    public A2ATask cancelTask(String workflowName, String workflowId) {
        requireExposed(workflowName);
        Workflow workflow = loadWorkflow(workflowId, workflowName, true);
        if (!workflow.getStatus().isTerminal()) {
            try {
                workflowService.terminateWorkflow(workflowId, "Canceled via A2A tasks/cancel");
            } catch (Exception e) {
                // Raced to a terminal state between the check and terminate (e.g. ConflictException
                // on an already-completed workflow) — reload and return the actual terminal state.
            }
            workflow = loadWorkflow(workflowId, workflowName, false);
        }
        return toA2ATask(workflow);
    }

    // ---- mapping -----------------------------------------------------------------------------

    private A2ATask toA2ATask(Workflow workflow) {
        A2ATask task = new A2ATask();
        task.setKind("task");
        task.setId(workflow.getWorkflowId());
        task.setContextId(workflow.getCorrelationId());

        String state = mapState(workflow);
        TaskStatus status = new TaskStatus();
        status.setState(state);
        String note = statusNote(workflow, state);
        if (note != null) {
            status.setMessage(agentTextMessage(note, workflow));
        }
        task.setStatus(status);

        if (Workflow.WorkflowStatus.COMPLETED == workflow.getStatus()
                && workflow.getOutput() != null
                && !workflow.getOutput().isEmpty()) {
            task.setArtifacts(List.of(outputArtifact(workflow.getOutput())));
        }
        return task;
    }

    /** Maps a Conductor {@code WorkflowStatus} onto an A2A {@code TaskState}. */
    String mapState(Workflow workflow) {
        switch (workflow.getStatus()) {
            case COMPLETED:
                return TaskState.COMPLETED;
            case FAILED:
            case TIMED_OUT:
                return TaskState.FAILED;
            case TERMINATED:
                return TaskState.CANCELED;
            case PAUSED:
                // Admin pause has no clean A2A analog; treat as still working.
                return TaskState.WORKING;
            case RUNNING:
            default:
                return isBlockedOnInput(workflow) ? TaskState.INPUT_REQUIRED : TaskState.WORKING;
        }
    }

    /** A RUNNING workflow sitting on a non-terminal HUMAN/WAIT task is awaiting input. */
    private boolean isBlockedOnInput(Workflow workflow) {
        return findBlockingTask(workflow) != null;
    }

    /** The pending HUMAN/WAIT task the workflow is blocked on for input, or {@code null}. */
    private Task findBlockingTask(Workflow workflow) {
        if (workflow.getTasks() == null) {
            return null;
        }
        for (Task task : workflow.getTasks()) {
            String type = task.getTaskType();
            if (("HUMAN".equals(type) || "WAIT".equals(type))
                    && task.getStatus() != null
                    && !task.getStatus().isTerminal()) {
                return task;
            }
        }
        return null;
    }

    private String statusNote(Workflow workflow, String state) {
        if (TaskState.FAILED.equals(state)) {
            return workflow.getReasonForIncompletion() != null
                    ? workflow.getReasonForIncompletion()
                    : "Workflow ended in state " + workflow.getStatus();
        }
        if (TaskState.INPUT_REQUIRED.equals(state)) {
            return "Workflow is awaiting input. Send another message/send carrying this task's id"
                    + " to provide the input and resume the execution.";
        }
        return null;
    }

    private Artifact outputArtifact(Map<String, Object> output) {
        Part part = new Part();
        part.setKind("data");
        part.setData(output);
        Artifact artifact = new Artifact();
        artifact.setArtifactId("workflow-output");
        artifact.setName("output");
        artifact.setParts(List.of(part));
        return artifact;
    }

    private A2AMessage agentTextMessage(String text, Workflow workflow) {
        Part part = new Part();
        part.setKind("text");
        part.setText(text);
        A2AMessage message = new A2AMessage();
        message.setRole("agent");
        message.setKind("message");
        message.setParts(List.of(part));
        message.setTaskId(workflow.getWorkflowId());
        message.setContextId(workflow.getCorrelationId());
        return message;
    }

    private Map<String, Object> buildInput(A2AMessage message) {
        Map<String, Object> input = new HashMap<>();
        if (message != null && message.getParts() != null) {
            for (Part part : message.getParts()) {
                if (part.getData() instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) part.getData();
                    input.putAll(data);
                }
            }
        }
        input.put(INPUT_TEXT, partsText(message));
        input.put(INPUT_MESSAGE_ID, message == null ? null : message.getMessageId());
        input.put(INPUT_CONTEXT_ID, message == null ? null : message.getContextId());
        return input;
    }

    // ---- helpers -----------------------------------------------------------------------------

    private WorkflowDef requireExposed(String workflowName) {
        WorkflowDef def = latestDef(workflowName);
        if (def == null || !isExposed(def)) {
            throw A2AServerException.notFound("No A2A agent exposed for workflow: " + workflowName);
        }
        return def;
    }

    private WorkflowDef latestDef(String workflowName) {
        if (workflowName == null || workflowName.isBlank()) {
            return null;
        }
        try {
            return metadataService.getWorkflowDef(workflowName, null);
        } catch (Exception e) {
            return null;
        }
    }

    private Workflow loadWorkflow(String workflowId, String expectedName, boolean includeTasks) {
        Workflow workflow;
        try {
            workflow = workflowService.getExecutionStatus(workflowId, includeTasks);
        } catch (Exception e) {
            workflow = null;
        }
        if (workflow == null) {
            throw A2AServerException.notFound(
                    "No A2A task (workflow execution) found: " + workflowId);
        }
        // An agent only manages its own workflow's executions. (getWorkflowName() throws if the
        // definition is absent, so read it defensively via the definition.)
        WorkflowDef def = workflow.getWorkflowDefinition();
        // Fail closed: if we can't confirm the execution belongs to this agent's workflow (name
        // mismatch OR definition unavailable), deny — don't let agent A read/cancel B's tasks.
        if (expectedName != null && (def == null || !expectedName.equals(def.getName()))) {
            throw A2AServerException.notFound(
                    "Task " + workflowId + " does not belong to agent " + expectedName);
        }
        return workflow;
    }

    private List<String> tags(WorkflowDef def) {
        Object tags = def.getMetadata() == null ? null : def.getMetadata().get(METADATA_TAGS);
        if (tags instanceof List) {
            List<String> out = new ArrayList<>();
            for (Object t : (List<?>) tags) {
                if (t != null) {
                    out.add(t.toString());
                }
            }
            return out;
        }
        return null;
    }

    private String agentUrl(String workflowName, String requestBaseUrl) {
        return baseUrl(requestBaseUrl) + normalizedBasePath() + "/" + workflowName;
    }

    private String baseUrl(String requestBaseUrl) {
        String base =
                properties.getPublicUrl() != null && !properties.getPublicUrl().isBlank()
                        ? properties.getPublicUrl()
                        : requestBaseUrl;
        return base != null && base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private String normalizedBasePath() {
        String path = properties.getBasePath();
        if (path == null || path.isBlank()) {
            return "/a2a";
        }
        String p = path.startsWith("/") ? path : "/" + path;
        return p.endsWith("/") ? p.substring(0, p.length() - 1) : p;
    }

    private String partsText(A2AMessage message) {
        if (message == null || message.getParts() == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Part part : message.getParts()) {
            if (part.getText() != null) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(part.getText());
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
