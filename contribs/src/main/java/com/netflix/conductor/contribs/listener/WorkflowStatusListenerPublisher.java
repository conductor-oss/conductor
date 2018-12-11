package com.netflix.conductor.contribs.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.execution.WorkflowStatusListener;
import com.netflix.conductor.dao.QueueDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Collections;


public class WorkflowStatusListenerPublisher implements WorkflowStatusListener {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowStatusListenerPublisher.class);
    private final QueueDAO queueDAO;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String CALLBACK_QUEUE = "_callbackQueue";

    @Inject
    public WorkflowStatusListenerPublisher(QueueDAO queueDAO) {
        this.queueDAO = queueDAO;
    }

    @Override
    public void onWorkflowCompleted(Workflow workflow) {
        LOG.info("Publishing workflow {} on completion callback", workflow.getWorkflowId());
        queueDAO.push(CALLBACK_QUEUE, Collections.singletonList(workflowToMessage(workflow)));
    }

    @Override
    public void onWorkflowTerminated(Workflow workflow) {
        LOG.info("Publishing workflow {} on termination callback", workflow.getWorkflowId());
        queueDAO.push(CALLBACK_QUEUE, Collections.singletonList(workflowToMessage(workflow)));
    }

    private Message workflowToMessage(Workflow workflow) {
        String jsonWf;
        try {
            jsonWf = objectMapper.writeValueAsString(workflow);
        } catch (JsonProcessingException e) {
            LOG.error("Failed to convert Workflow: {} to String. Exception: {}", workflow, e);
            throw new RuntimeException(e);
        }
        return new Message(workflow.getWorkflowId(), jsonWf, null);
    }

}
