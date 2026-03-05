/*
 * Copyright 2023 Conductor Authors.
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
package com.netflix.conductor.common.run;

import java.util.LinkedHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.common.metadata.tasks.Task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Core module class DeciderService need to log DomainGroupMoId,AccountMoId from task input. Finding
 * these Ids is already implemented in TaskNotification. TaskNotification is in contribs module. As
 * core can not import contrib module, creating this new class in common and added parsing logic to
 * find DomainGroupMoId and AccountMoId
 */
public class TaskLog extends TaskSummary {
    private String domainGroupMoId = "";
    private String accountMoId = "";

    private String workflowInfoMoId = "";

    private ObjectMapper objectMapper = new ObjectMapper();

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskLog.class);

    public TaskLog(Task task) {
        super(task);
        boolean isFusionMetaPresent = task.getInputData().containsKey("_ioMeta");
        if (!isFusionMetaPresent) {
            return;
        }

        LinkedHashMap fusionMeta = (LinkedHashMap) task.getInputData().get("_ioMeta");
        domainGroupMoId =
                fusionMeta.containsKey("DomainGroupMoId")
                        ? fusionMeta.get("DomainGroupMoId").toString()
                        : "";
        accountMoId =
                fusionMeta.containsKey("AccountMoId")
                        ? fusionMeta.get("AccountMoId").toString()
                        : "";

        workflowInfoMoId =
                fusionMeta.containsKey("WfInfoMoId") ? fusionMeta.get("WfInfoMoId").toString() : "";
    }

    public String getDomainGroupMoId() {
        return this.domainGroupMoId;
    }

    public String getAccountMoId() {
        return this.accountMoId;
    }

    public String getWorkflowInfoMoId() {
        return this.workflowInfoMoId;
    }

    public String toLogString() {
        try {
            ObjectNode logData = JsonNodeFactory.instance.objectNode();
            logData.put("domainGroupMoId", this.getDomainGroupMoId());
            logData.put("accountMoId", this.getAccountMoId());
            logData.put("correlationId", this.getCorrelationId());
            logData.put("workflowInfoMoId", this.getWorkflowInfoMoId());
            logData.put("workflowInstanceId", this.getWorkflowId());
            logData.put("workflowName", this.getWorkflowType());
            logData.put("taskId", this.getTaskId());
            logData.put("taskDefName", this.getTaskDefName());
            return objectMapper.writeValueAsString(logData);
        } catch (Exception ee) {
            LOGGER.error("Error while creating logData", ee);
        }
        return "";
    }
}
