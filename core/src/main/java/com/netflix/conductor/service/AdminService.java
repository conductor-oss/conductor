package com.netflix.conductor.service;

import com.google.common.base.Preconditions;
import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.QueueDAO;
import org.apache.commons.lang3.StringUtils;
import com.netflix.conductor.service.utils.ServiceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * @author fjhaveri
 *
 */
@Singleton
@Trace
public class AdminService {

    private static Logger logger = LoggerFactory.getLogger(AdminService.class);

    private final Configuration config;

    private final ExecutionService executionService;

    private final QueueDAO queue;

    private String version;

    private String buildDate;

    @Inject
    public AdminService(Configuration config, ExecutionService executionService, QueueDAO queue) {
        this.config = config;
        this.executionService = executionService;
        this.queue = queue;
        this.version = "UNKNOWN";
        this.buildDate = "UNKNOWN";

        try {
            InputStream propertiesIs = this.getClass().getClassLoader().getResourceAsStream("META-INF/conductor-core.properties");
            Properties prop = new Properties();
            prop.load(propertiesIs);
            this.version = prop.getProperty("Implementation-Version");
            this.buildDate = prop.getProperty("Build-Date");
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * Get all the configuration parameters.
     */
    public Map<String, Object> getAllConfig() {
        Map<String, Object> map = config.getAll();
        map.put("version", version);
        map.put("buildDate", buildDate);
        return map;
    }

    /**
     * Get the list of pending tasks for a given task type.
     *
     * @param taskType
     * @param start
     * @param count
     * @return the id of the workflow instance that can be use for tracking.
     */
    public List<Task> getListOfPendingTask(String taskType, Integer start, Integer count) {
        ServiceUtils.isValid(taskType, "TaskType cannot be null or empty.");
        List<Task> tasks = executionService.getPendingTasksForTaskType(taskType);
        int total = start + count;
        total = (tasks.size() > total) ? total : tasks.size();
        if (start > tasks.size()) start = tasks.size();
        return tasks.subList(start, total);
    }

    /**
     * Queue up all the running workflows for sweep.
     *
     * @param workflowId
     * @return the id of the workflow instance that can be use for tracking.
     */
    public String requeueSweep(String workflowId) {
        ServiceUtils.isValid(workflowId, "WorkflowId cannot be null or empty.");
        boolean pushed = queue.pushIfNotExists(WorkflowExecutor.deciderQueue, workflowId, config.getSweepFrequency());
        return pushed + "." + workflowId;
    }
}
