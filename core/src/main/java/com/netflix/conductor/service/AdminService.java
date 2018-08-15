package com.netflix.conductor.service;

import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.QueueDAO;
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

    private final QueueDAO queueDAO;

    private String version;

    private String buildDate;

    @Inject
    public AdminService(Configuration config, ExecutionService executionService, QueueDAO queueDAO) {
        this.config = config;
        this.executionService = executionService;
        this.queueDAO = queueDAO;
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
     * @return all the configuration parameters.
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
     * @param taskType Name of the task
     * @param start Start index of pagination
     * @param count Number of entries
     * @return list of pending {@link Task}
     */
    public List<Task> getListOfPendingTask(String taskType, Integer start, Integer count) {
        ServiceUtils.checkNotNullOrEmpty(taskType, "TaskType cannot be null or empty.");
        List<Task> tasks = executionService.getPendingTasksForTaskType(taskType);
        int total = start + count;
        total = (tasks.size() > total) ? total : tasks.size();
        if (start > tasks.size()) start = tasks.size();
        return tasks.subList(start, total);
    }

    /**
     * Queue up all the running workflows for sweep.
     *
     * @param workflowId Id of the workflow
     * @return the id of the workflow instance that can be use for tracking.
     */
    public String requeueSweep(String workflowId) {
        ServiceUtils.checkNotNullOrEmpty(workflowId, "WorkflowId cannot be null or empty.");
        boolean pushed = queueDAO.pushIfNotExists(WorkflowExecutor.deciderQueue, workflowId, config.getSweepFrequency());
        return pushed + "." + workflowId;
    }
}
