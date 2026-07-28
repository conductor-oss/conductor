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
package org.conductoross.conductor.ai.agentspan.a2a;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import org.conductoross.conductor.ai.a2a.server.A2AServerException;
import org.conductoross.conductor.ai.a2a.server.A2AServerProperties;
import org.conductoross.conductor.ai.a2a.server.A2AStreamSink;
import org.conductoross.conductor.ai.agentspan.runtime.service.AgentService;
import org.conductoross.conductor.common.metadata.agent.AgentStartRequest;
import org.conductoross.conductor.common.metadata.agent.AgentStartResponse;
import org.conductoross.conductor.common.metadata.agent.AgentSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.service.TaskService;
import com.netflix.conductor.service.WorkflowService;

/**
 * Exposes native Conductor agents (agentspan) as A2A agents (server side). One agent = one A2A
 * agent card, served at {@code /api/a2a/agent/{name}}.
 *
 * <p>Mirrors {@code A2AWorkflowAgent} but drives native agents via {@link AgentService} instead of
 * {@code WorkflowService.startWorkflow()}. Since native agents compile down to workflows, all
 * execution tracking (getTask, cancelTask, stream) reuses the same workflow-state mapping.
 *
 * <p>Gated on both {@code conductor.a2a.server.enabled=true} (the A2A server master switch) and
 * {@code agentspan.embedded=true} (agentspan runtime present).
 */
@Component
@ConditionalOnProperty(
        name = {"conductor.a2a.server.enabled", "agentspan.embedded"},
        havingValue = "true")
public class A2ANativeAgentFacade {

    private static final Logger log = LoggerFactory.getLogger(A2ANativeAgentFacade.class);

    private final AgentService agentService;
    private final WorkflowService workflowService;
    private final TaskService taskService;
    private final A2AServerProperties properties;

    public A2ANativeAgentFacade(
            AgentService agentService,
            WorkflowService workflowService,
            TaskService taskService,
            A2AServerProperties properties) {
        this.agentService = agentService;
        this.workflowService = workflowService;
        this.taskService = taskService;
        this.properties = properties;
    }

    // ---- exposure ----------------------------------------------------------------------------

    public boolean isExposed(String agentName) {
        return agentService.listAgents().stream().anyMatch(s -> s.getName().equals(agentName));
    }

    public List<AgentSummary> exposedAgents() {
        return agentService.listAgents();
    }

    // ---- agent card --------------------------------------------------------------------------

    public AgentCard agentCard(String agentName, String requestBaseUrl) {
        AgentSummary summary = requireExposed(agentName);
        return buildCard(summary, requestBaseUrl);
    }

    private AgentCard buildCard(AgentSummary summary, String requestBaseUrl) {
        String description =
                summary.getDescription() != null
                        ? summary.getDescription()
                        : "Conductor agent '" + summary.getName() + "' exposed as an A2A agent";

        AgentCard card = new AgentCard();
        card.setName(summary.getName());
        card.setDescription(description);
        card.setUrl(agentUrl(summary.getName(), requestBaseUrl));
        card.setVersion(String.valueOf(summary.getVersion()));
        card.setProtocolVersion("0.3.0");
        card.setPreferredTransport("JSONRPC");
        card.setDefaultInputModes(properties.getDefaultInputModes());
        card.setDefaultOutputModes(properties.getDefaultOutputModes());

        AgentCapabilities capabilities = new AgentCapabilities();
        capabilities.setStreaming(true);
        card.setCapabilities(capabilities);

        AgentProvider provider = new AgentProvider();
        provider.setOrganization(properties.getProviderOrganization());
        provider.setUrl(baseUrl(requestBaseUrl));
        card.setProvider(provider);

        AgentSkill skill = new AgentSkill();
        skill.setId(summary.getName());
        skill.setName(summary.getName());
        skill.setDescription(description);
        skill.setTags(summary.getTags());
        skill.setInputModes(properties.getDefaultInputModes());
        skill.setOutputModes(properties.getDefaultOutputModes());
        card.setSkills(List.of(skill));
        return card;
    }

    // ---- A2A methods -------------------------------------------------------------------------

    public A2ATask sendMessage(String agentName, A2AMessage message) {
        requireExposed(agentName);

        // Multi-turn: a follow-up message with an existing taskId resumes the paused execution.
        if (message != null && message.getTaskId() != null && !message.getTaskId().isBlank()) {
            return resume(agentName, message);
        }

        AgentStartRequest request = new AgentStartRequest();
        request.setName(agentName);
        request.setPrompt(partsText(message));
        if (message != null && message.getContextId() != null) {
            request.setSessionId(message.getContextId());
        }
        if (message != null && message.getMessageId() != null) {
            request.setIdempotencyKey(agentName + ":" + message.getMessageId());
        }

        AgentStartResponse response = agentService.start(request);
        return toA2ATask(loadWorkflow(response.getExecutionId(), false));
    }

    private A2ATask resume(String agentName, A2AMessage message) {
        String executionId = message.getTaskId();
        Workflow workflow = loadWorkflow(executionId, true);
        if (workflow.getStatus() != null && workflow.getStatus().isTerminal()) {
            return toA2ATask(workflow);
        }
        Task blocking = findBlockingTask(workflow);
        if (blocking == null) {
            return toA2ATask(workflow);
        }
        Map<String, Object> input = buildResumeInput(message);
        taskService.updateTask(
                executionId,
                blocking.getReferenceTaskName(),
                TaskResult.Status.COMPLETED,
                "a2a-resume",
                input);
        A2AMetrics.serverResume();
        return toA2ATask(loadWorkflow(executionId, true));
    }

    public A2ATask getTask(String agentName, String executionId) {
        requireExposed(agentName);
        return toA2ATask(loadWorkflow(executionId, true));
    }

    public A2ATask cancelTask(String agentName, String executionId) {
        requireExposed(agentName);
        Workflow workflow = loadWorkflow(executionId, true);
        if (!workflow.getStatus().isTerminal()) {
            try {
                workflowService.terminateWorkflow(executionId, "Canceled via A2A tasks/cancel");
            } catch (Exception e) {
                // Raced to terminal between check and terminate — reload actual state.
            }
            workflow = loadWorkflow(executionId, false);
        }
        return toA2ATask(workflow);
    }

    public void streamMessage(
            String agentName, A2AMessage message, Object rpcId, A2AStreamSink sink)
            throws IOException {
        A2ATask task = sendMessage(agentName, message);
        String executionId = task.getId();
        sink.event(envelope(rpcId, task));

        if (isStreamFinal(stateOf(task))) {
            sink.event(envelope(rpcId, statusUpdate(task, true)));
            return;
        }

        String last = stateOf(task);
        long deadline =
                System.currentTimeMillis() + properties.getStreamMaxDurationSeconds() * 1000L;
        while (System.currentTimeMillis() < deadline) {
            sleep(properties.getStreamPollIntervalMillis());
            A2ATask current = toA2ATask(loadWorkflow(executionId, true));
            String state = stateOf(current);
            if (isStreamFinal(state)) {
                if (current.getArtifacts() != null) {
                    for (Artifact artifact : current.getArtifacts()) {
                        sink.event(envelope(rpcId, artifactUpdate(current, artifact)));
                    }
                }
                sink.event(envelope(rpcId, statusUpdate(current, true)));
                return;
            }
            if (!java.util.Objects.equals(state, last)) {
                sink.event(envelope(rpcId, statusUpdate(current, false)));
                last = state;
            }
        }
        A2ATask current = toA2ATask(loadWorkflow(executionId, false));
        TaskStatus status = new TaskStatus();
        status.setState(stateOf(current));
        status.setMessage(
                agentTextMessage(
                        "Stream window elapsed; the agent is still running — continue with tasks/get.",
                        loadWorkflow(executionId, false)));
        sink.event(
                envelope(
                        rpcId,
                        statusUpdateEvent(current.getId(), current.getContextId(), status, true)));
    }

    // ---- URL helpers -------------------------------------------------------------------------

    public String agentUrl(String agentName, String requestBaseUrl) {
        return baseUrl(requestBaseUrl) + normalizedAgentBasePath() + "/" + agentName;
    }

    public String agentCardUrl(String agentName, String requestBaseUrl) {
        return agentUrl(agentName, requestBaseUrl) + "/.well-known/agent-card.json";
    }

    // ---- private helpers ---------------------------------------------------------------------

    private AgentSummary requireExposed(String agentName) {
        return agentService.listAgents().stream()
                .filter(s -> s.getName().equals(agentName))
                .findFirst()
                .orElseThrow(
                        () ->
                                A2AServerException.notFound(
                                        "No A2A agent exposed for native agent: " + agentName));
    }

    private Workflow loadWorkflow(String executionId, boolean includeTasks) {
        Workflow workflow;
        try {
            workflow = workflowService.getExecutionStatus(executionId, includeTasks);
        } catch (Exception e) {
            workflow = null;
        }
        if (workflow == null) {
            throw A2AServerException.notFound(
                    "No A2A task (agent execution) found: " + executionId);
        }
        return workflow;
    }

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

    private String mapState(Workflow workflow) {
        switch (workflow.getStatus()) {
            case COMPLETED:
                return TaskState.COMPLETED;
            case FAILED:
            case TIMED_OUT:
                return TaskState.FAILED;
            case TERMINATED:
                return TaskState.CANCELED;
            case PAUSED:
                return TaskState.WORKING;
            case RUNNING:
            default:
                return findBlockingTask(workflow) != null
                        ? TaskState.INPUT_REQUIRED
                        : TaskState.WORKING;
        }
    }

    private String statusNote(Workflow workflow, String state) {
        if (TaskState.FAILED.equals(state)) {
            return workflow.getReasonForIncompletion() != null
                    ? workflow.getReasonForIncompletion()
                    : "Agent ended in state " + workflow.getStatus();
        }
        if (TaskState.INPUT_REQUIRED.equals(state)) {
            return "Agent is awaiting input. Send another message/send carrying this task's id"
                    + " to provide the input and resume the execution.";
        }
        return null;
    }

    private Artifact outputArtifact(Map<String, Object> output) {
        Part part = new Part();
        part.setKind("data");
        part.setData(output);
        Artifact artifact = new Artifact();
        artifact.setArtifactId("agent-output");
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

    private Map<String, Object> buildResumeInput(A2AMessage message) {
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
        String text = partsText(message);
        if (text != null) {
            input.put("_a2a_text", text);
        }
        return input;
    }

    private String partsText(A2AMessage message) {
        if (message == null || message.getParts() == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Part part : message.getParts()) {
            if (part.getText() != null) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(part.getText());
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private boolean isStreamFinal(String state) {
        return TaskState.isTerminal(state) || TaskState.isInterrupted(state);
    }

    private String stateOf(A2ATask task) {
        return task.getStatus() != null ? task.getStatus().getState() : null;
    }

    private Map<String, Object> envelope(Object rpcId, Object result) {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("jsonrpc", "2.0");
        envelope.put("id", rpcId);
        envelope.put("result", result);
        return envelope;
    }

    private Map<String, Object> statusUpdate(A2ATask task, boolean isFinal) {
        return statusUpdateEvent(task.getId(), task.getContextId(), task.getStatus(), isFinal);
    }

    private Map<String, Object> statusUpdateEvent(
            String taskId, String contextId, TaskStatus status, boolean isFinal) {
        Map<String, Object> event = new HashMap<>();
        event.put("kind", "status-update");
        event.put("taskId", taskId);
        event.put("contextId", contextId);
        event.put("status", status);
        event.put("final", isFinal);
        return event;
    }

    private Map<String, Object> artifactUpdate(A2ATask task, Artifact artifact) {
        Map<String, Object> event = new HashMap<>();
        event.put("kind", "artifact-update");
        event.put("taskId", task.getId());
        event.put("contextId", task.getContextId());
        event.put("artifact", artifact);
        return event;
    }

    private void sleep(long millis) throws IOException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("A2A stream interrupted", e);
        }
    }

    private String baseUrl(String requestBaseUrl) {
        String base =
                properties.getPublicUrl() != null && !properties.getPublicUrl().isBlank()
                        ? properties.getPublicUrl()
                        : requestBaseUrl;
        return base != null && base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private String normalizedAgentBasePath() {
        String path = properties.getAgentBasePath();
        if (path == null || path.isBlank()) {
            return "/api/a2a/agent";
        }
        String p = path.startsWith("/") ? path : "/" + path;
        return p.endsWith("/") ? p.substring(0, p.length() - 1) : p;
    }
}
