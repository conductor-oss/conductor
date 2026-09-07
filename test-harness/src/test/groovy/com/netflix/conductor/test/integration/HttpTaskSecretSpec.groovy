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
package com.netflix.conductor.test.integration

import org.springframework.beans.factory.annotation.Autowired

import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.common.metadata.workflow.WorkflowDef
import com.netflix.conductor.common.metadata.workflow.WorkflowTask
import com.netflix.conductor.dao.QueueDAO
import com.netflix.conductor.tasks.http.HttpTask
import com.netflix.conductor.test.base.AbstractSpecification

import com.sun.net.httpserver.HttpServer

/**
 * End-to-end integration test proving that an HTTP system task resolves a
 * {@code ${workflow.secrets.*}} reference on its ACTUAL outbound HTTP request (server-side
 * hand-off in {@code AsyncSystemTaskExecutor#execute}), while the persisted task input keeps the
 * literal reference untouched.
 */
class HttpTaskSecretSpec extends AbstractSpecification {

    @Autowired
    QueueDAO queueDAO

    @Autowired
    HttpTask httpTask

    private static final String WORKFLOW_NAME = 'http_task_secret_wf'
    private static final String SECRET_LITERAL = '${workflow.secrets.HTTP_TOKEN}'

    private static HttpServer server
    private static volatile String receivedSecretHeader

    def setupSpec() {
        System.setProperty("CONDUCTOR_SECRET_HTTP_TOKEN", "s3cr3t")

        server = HttpServer.create(new InetSocketAddress(0), 0)
        server.createContext("/echo", { exchange ->
            receivedSecretHeader = exchange.getRequestHeaders().getFirst("X-Secret")
            exchange.getRequestBody().withCloseable { it.readAllBytes() }
            def responseBody = '{"ok":true}'.getBytes("UTF-8")
            exchange.getResponseHeaders().add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, responseBody.length)
            exchange.getResponseBody().withCloseable { os -> os.write(responseBody) }
        })
        server.start()
    }

    def cleanupSpec() {
        server.stop(0)
        System.clearProperty("CONDUCTOR_SECRET_HTTP_TOKEN")
    }

    def setup() {
        def wfTask = new WorkflowTask()
        wfTask.name = 'http_task_secret'
        wfTask.taskReferenceName = 'http_task_secret_ref'
        wfTask.type = 'HTTP'
        wfTask.inputParameters = [
                http_request: [
                        uri        : "http://localhost:${server.getAddress().getPort()}/echo".toString(),
                        method     : 'POST',
                        contentType: 'application/json',
                        accept     : 'application/json',
                        headers    : ['X-Secret': SECRET_LITERAL]
                ]
        ]

        def wfDef = new WorkflowDef()
        wfDef.name = WORKFLOW_NAME
        wfDef.version = 1
        wfDef.tasks = [wfTask]
        wfDef.ownerEmail = 'test@example.com'
        wfDef.timeoutSeconds = 3600
        wfDef.timeoutPolicy = WorkflowDef.TimeoutPolicy.ALERT_ONLY
        metadataService.registerWorkflowDef(wfDef)
    }

    def cleanup() {
        try { metadataService.unregisterWorkflowDef(WORKFLOW_NAME, 1) } catch (ignored) {}
        workflowTestUtil.clearWorkflows()
    }

    def "HTTP task resolves secret on outbound request but keeps literal in persisted input"() {
        given: "the mock endpoint has not yet received a request"
        receivedSecretHeader = null

        when: "the workflow is started"
        def workflowId = startWorkflow(WORKFLOW_NAME, 1, 'http-task-secret-test', [:], null)

        then: "the HTTP task is scheduled and its persisted input keeps the literal secret reference"
        def scheduled = null
        conditions.eventually {
            scheduled = workflowExecutionService.getExecutionStatus(workflowId, true)
                    .tasks.find { it.taskType == 'HTTP' }
            assert scheduled != null
            assert scheduled.status == Task.Status.SCHEDULED
        }
        scheduled.inputData.http_request.headers.'X-Secret' == SECRET_LITERAL

        when: "the HTTP system task is executed"
        List<String> polledTaskIds = []
        conditions.eventually {
            polledTaskIds = queueDAO.pop("HTTP", 1, 200)
            assert !polledTaskIds.isEmpty()
        }
        asyncSystemTaskExecutor.execute(httpTask, polledTaskIds.first())

        then: "the secret was resolved on the actual outbound request"
        receivedSecretHeader == 's3cr3t'

        and: "the persisted task input still keeps the literal secret reference and the task completed"
        def completedTask = workflowExecutionService.getExecutionStatus(workflowId, true)
                .tasks.find { it.taskType == 'HTTP' }
        completedTask.inputData.http_request.headers.'X-Secret' == SECRET_LITERAL
        completedTask.status == Task.Status.COMPLETED
    }
}
