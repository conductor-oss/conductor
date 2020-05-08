package com.netflix.conductor.tests.utils;

import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.service.ExecutionService;
import com.netflix.conductor.service.MetadataService;
import org.apache.commons.lang.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.FileOutputStream;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class WorkflowCleanUpUtil {

    private final MetadataService metadataService;
    private final ExecutionService workflowExecutionService;
    private final WorkflowExecutor workflowExecutor;
    private final QueueDAO queueDAO;

    public static final String TEMP_FILE_PATH = "/input.json";

    @Inject
    public WorkflowCleanUpUtil(MetadataService metadataService, ExecutionService workflowExecutionService,
                               WorkflowExecutor workflowExecutor, QueueDAO queueDAO) {
        this.metadataService = metadataService;
        this.workflowExecutionService = workflowExecutionService;
        this.workflowExecutor = workflowExecutor;
        this.queueDAO = queueDAO;
    }

    public void clearWorkflows() throws Exception {
        List<String> workflowsWithVersion = metadataService.getWorkflowDefs().stream()
                .map(def -> def.getName() + ":" + def.getVersion())
                .collect(Collectors.toList());
        for (String workflowWithVersion : workflowsWithVersion) {
            String workflowName = StringUtils.substringBefore(workflowWithVersion, ":");
            int version = Integer.parseInt(StringUtils.substringAfter(workflowWithVersion, ":"));
            List<String> running = workflowExecutionService.getRunningWorkflows(workflowName, version);
            for (String wfid : running) {
                Workflow workflow = workflowExecutor.getWorkflow(wfid, false);
                if (!workflow.getStatus().isTerminal()) {
                    workflowExecutor.terminateWorkflow(wfid, "cleanup");
                }
            }
        }
        queueDAO.queuesDetail().keySet().forEach(queueDAO::flush);

        new FileOutputStream(this.getClass().getResource(TEMP_FILE_PATH).getPath()).close();
    }
}
