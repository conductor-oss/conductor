package io.conductor.e2e.processing;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import io.conductor.e2e.util.ApiUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

public class MaskSensitiveDataInWorkflowE2ETest {
    static String wfName = "workflow-mask-sensitive-data-e2e";
    private static WorkflowClient workflowClient;
    private static MetadataClient metadataClient;
    private static TaskClient taskClient;

    @BeforeAll
    public static void init() {
        workflowClient = ApiUtil.WORKFLOW_CLIENT;
        metadataClient = ApiUtil.METADATA_CLIENT;
        taskClient = ApiUtil.TASK_CLIENT;

        registerWorkflowDef();
    }

    @AfterAll
    public static void cleanUp() {
        try {
            // Clean up workflow definition
            metadataClient.unregisterWorkflowDef(wfName, 1);
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    private static void registerWorkflowDef() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(wfName);
        workflowDef.setVersion(1);
        workflowDef.setDescription("E2E test workflow for sensitive data masking");

        // Set custom masked fields in addition to defaults (_secrets, _masked)
        workflowDef.setMaskedFields(Arrays.asList("ssn", "creditCard", "apiKey", "password"));

        List<WorkflowTask> tasks = new ArrayList<>();

        // Task 1: INLINE Task with sensitive data processing
        WorkflowTask inlineTask = new WorkflowTask();
        inlineTask.setName("INLINE");
        inlineTask.setTaskReferenceName("inline_task_1");
        inlineTask.setType(TaskType.INLINE.name());

        // INLINE task input with sensitive data
        Map<String, Object> inlineTaskInput = new HashMap<>();
        inlineTaskInput.put("evaluatorType", "graaljs");
        inlineTaskInput.put("expression",
                "(function() {" +
                        "  var result = {" +
                        "    status: 'processed'," +
                        "    _secrets: 'inline-secret-result'," +
                        "    apiKey: 'processed-api-key'," +
                        "    userInfo: {" +
                        "      name: $.userInfo.name," +
                        "      ssn: $.userInfo.ssn," +
                        "      _masked: 'processed-user-data'" +
                        "    }," +
                        "    paymentInfo: {" +
                        "      amount: $.paymentInfo.amount," +
                        "      creditCard: $.paymentInfo.creditCard," +
                        "      _secrets: 'processed-payment-secret'" +
                        "    }" +
                        "  };" +
                        "  return result;" +
                        "})();"
        );

        // Add sensitive input data for the inline task
        inlineTaskInput.put("_secrets", "inline-input-secret");
        inlineTaskInput.put("apiKey", "inline-api-key-123");

        // Nested sensitive input data
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("name", "John Doe");
        userInfo.put("ssn", "123-45-6789");
        userInfo.put("_masked", "user-sensitive-data");
        inlineTaskInput.put("userInfo", userInfo);

        Map<String, Object> paymentInfo = new HashMap<>();
        paymentInfo.put("amount", "100.00");
        paymentInfo.put("creditCard", "4111-1111-1111-1111");
        paymentInfo.put("_secrets", "payment-secret-data");
        inlineTaskInput.put("paymentInfo", paymentInfo);

        inlineTask.setInputParameters(inlineTaskInput);
        tasks.add(inlineTask);

        // Task 2: SET_VARIABLE Task with array/list sensitive data
        WorkflowTask setVariableTask = new WorkflowTask();
        setVariableTask.setName("SET_VARIABLE");
        setVariableTask.setTaskReferenceName("set_variable_task_1");
        setVariableTask.setType(TaskType.SET_VARIABLE.name());

        Map<String, Object> setVariableInput = new HashMap<>();
        setVariableInput.put("_secrets", "set-variable-secret");

        // Create variables with sensitive data including arrays
        List<Map<String, Object>> usersList = new ArrayList<>();
        Map<String, Object> user1 = new HashMap<>();
        user1.put("id", "user1");
        user1.put("password", "secret-password-1");
        user1.put("_masked", "user1-sensitive-data");
        user1.put("role", "admin");
        usersList.add(user1);

        Map<String, Object> user2 = new HashMap<>();
        user2.put("id", "user2");
        user2.put("ssn", "987-65-4321");
        user2.put("_secrets", "user2-secret-data");
        user2.put("role", "user");
        usersList.add(user2);

        setVariableInput.put("usersList", usersList);
        setVariableInput.put("apiKey", "set-variable-api-key");
        setVariableInput.put("password", "set-variable-password");

        // Complex nested structure
        Map<String, Object> configData = new HashMap<>();
        configData.put("environment", "production");
        configData.put("_secrets", "config-secret");

        Map<String, Object> dbConfig = new HashMap<>();
        dbConfig.put("host", "db.example.com");
        dbConfig.put("password", "db-secret-password");
        dbConfig.put("_masked", "db-connection-masked");
        configData.put("database", dbConfig);

        List<Map<String, Object>> services = new ArrayList<>();
        Map<String, Object> service1 = new HashMap<>();
        service1.put("name", "auth-service");
        service1.put("apiKey", "service-api-key-456");
        service1.put("_secrets", "service-secret");
        services.add(service1);
        configData.put("services", services);

        setVariableInput.put("config", configData);

        setVariableTask.setInputParameters(setVariableInput);
        tasks.add(setVariableTask);

        // Task 3: Another INLINE Task simulating complex processing with deeply nested data
        WorkflowTask complexInlineTask = new WorkflowTask();
        complexInlineTask.setName("INLINE");
        complexInlineTask.setTaskReferenceName("complex_inline_task_1");
        complexInlineTask.setType(TaskType.INLINE.name());

        Map<String, Object> complexInlineInput = new HashMap<>();
        complexInlineInput.put("evaluatorType", "graaljs");
        complexInlineInput.put("expression",
                "(function() {" +
                        "  var complexResult = {" +
                        "    processingId: 'proc-' + Date.now()," +
                        "    _secrets: 'complex-processing-secret'," +
                        "    results: [" +
                        "      {" +
                        "        taskName: 'data-processing'," +
                        "        apiKey: 'result-api-key'," +
                        "        _masked: 'processing-result-masked'," +
                        "        data: {" +
                        "          status: 'completed'," +
                        "          password: 'result-password'," +
                        "          _secrets: 'nested-result-secret'," +
                        "          userResults: [" +
                        "            {" +
                        "              userId: 'user123'," +
                        "              creditCard: 'processed-cc-number'," +
                        "              _masked: 'user-result-masked'" +
                        "            }" +
                        "          ]" +
                        "        }" +
                        "      }" +
                        "    ]" +
                        "  };" +
                        "  return complexResult;" +
                        "})();"
        );

        // Complex input with deeply nested sensitive data
        complexInlineInput.put("_secrets", "complex-inline-secret");
        complexInlineInput.put("apiKey", "complex-api-key");

        Map<String, Object> processingConfig = new HashMap<>();
        processingConfig.put("batchSize", 100);
        processingConfig.put("password", "processing-password");
        processingConfig.put("_masked", "processing-config-masked");

        List<Map<String, Object>> processingTasks = new ArrayList<>();
        Map<String, Object> task1 = new HashMap<>();
        task1.put("name", "validate-data");
        task1.put("apiKey", "validation-api-key");
        task1.put("_secrets", "validation-secret");

        Map<String, Object> validationRules = new HashMap<>();
        validationRules.put("required", Arrays.asList("id", "email"));
        validationRules.put("sensitiveFields", Arrays.asList("ssn", "creditCard"));
        validationRules.put("password", "validation-rule-password");
        validationRules.put("_masked", "validation-rules-masked");
        task1.put("rules", validationRules);

        processingTasks.add(task1);
        processingConfig.put("tasks", processingTasks);

        complexInlineInput.put("processingConfig", processingConfig);
        complexInlineTask.setInputParameters(complexInlineInput);
        tasks.add(complexInlineTask);

        WorkflowTask httpTask = new WorkflowTask();
        httpTask.setDescription("HTTP Task");
        httpTask.setWorkflowTaskType(TaskType.HTTP);
        httpTask.setTaskReferenceName("http_task_ref_1");
        httpTask.setName("http_task_1");
        httpTask.setInputParameters(Map.of(
                "uri", "http://httpbin-server:8081/api/hello?name=SecretMaskingTest",
                "method", "GET",
                "headers", Map.of(
                        "apiKeyFromWfInput", "${workflow.input.apiKey}",
                "ssnFromWfInput", "This is my SSN ${workflow.input.ssn}",
                        "apiKeyFromTaskInput","${inline_task_1.input.apiKey}",
                        "ssnFromOutput","${inline_task_1.output.result.paymentInfo.creditCard}")
        ));
        tasks.add(httpTask);

        workflowDef.setTasks(tasks);
        metadataClient.registerWorkflowDef(workflowDef);
    }

    @Test
    public void testMaskSensitiveDataInWorkflow() throws InterruptedException {
        // Prepare workflow input with sensitive data
        Map<String, Object> workflowInput = createWorkflowInputWithSensitiveData();

        // Start workflow
        StartWorkflowRequest startRequest = new StartWorkflowRequest();
        startRequest.setName(wfName);
        startRequest.setVersion(1);
        startRequest.setInput(workflowInput);

        String workflowId = workflowClient.startWorkflow(startRequest);
        assertNotNull(workflowId);

        // Wait for workflow completion (system tasks execute automatically)
        await().pollInterval(1, TimeUnit.SECONDS)
                .atMost(5, TimeUnit.MINUTES)
                .untilAsserted(() -> {
                    Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                    System.out.println("Workflow Run : Checking workflow " + workflow.getStatus() + ", reason: " + workflow.getReasonForIncompletion());
                    assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus(),
                            "Expected isolated workflow to be completed");
                });

        // Get workflow execution status and verify masking
        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        assertNotNull(workflow);

        // Ensure workflow completed
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus(),
                "Workflow should be completed");

        verifyWorkflowDataMasking(workflow);
        verifyTaskDataMasking(workflow);
    }

    private Map<String, Object> createWorkflowInputWithSensitiveData() {
        Map<String, Object> input = new HashMap<>();

        // Default sensitive fields
        input.put("_secrets", "workflow-level-secret");
        input.put("_masked", "workflow-level-masked-data");

        // Custom sensitive fields
        input.put("ssn", "111-22-3333");
        input.put("creditCard", "5555-5555-5555-4444");
        input.put("apiKey", "workflow-api-key-789");
        input.put("password", "workflow-secret-password");

        // Normal fields (should not be masked)
        input.put("userId", "user123");
        input.put("requestId", "req456");
        input.put("timestamp", System.currentTimeMillis());

        // Nested sensitive data
        Map<String, Object> config = new HashMap<>();
        config.put("environment", "production");
        config.put("_secrets", "config-secret-data");
        config.put("apiKey", "config-api-key");

        Map<String, Object> database = new HashMap<>();
        database.put("host", "db.example.com");
        database.put("password", "db-secret-password");
        database.put("_masked", "db-connection-info");
        config.put("database", database);

        input.put("config", config);

        return input;
    }

    private void verifyWorkflowDataMasking(Workflow workflow) {
        // Verify workflow input masking
        Map<String, Object> input = workflow.getInput();

        // Default fields should be masked
        assertEquals("***", input.get("_secrets"), "Workflow _secrets should be masked");
        assertEquals("***", input.get("_masked"), "Workflow _masked should be masked");

        // Custom fields should be masked
        assertEquals("***", input.get("ssn"), "Workflow ssn should be masked");
        assertEquals("***", input.get("creditCard"), "Workflow creditCard should be masked");
        assertEquals("***", input.get("apiKey"), "Workflow apiKey should be masked");
        assertEquals("***", input.get("password"), "Workflow password should be masked");

        // Normal fields should NOT be masked
        assertEquals("user123", input.get("userId"), "Workflow userId should not be masked");
        assertEquals("req456", input.get("requestId"), "Workflow requestId should not be masked");
        assertNotNull(input.get("timestamp"), "Workflow timestamp should not be masked");

        // Verify nested masking
        Map<String, Object> config = (Map<String, Object>) input.get("config");
        assertNotNull(config, "Config should exist");
        assertEquals("***", config.get("_secrets"), "Config _secrets should be masked");
        assertEquals("***", config.get("apiKey"), "Config apiKey should be masked");
        assertEquals("production", config.get("environment"), "Config environment should not be masked");

        // Verify deeply nested masking
        Map<String, Object> database = (Map<String, Object>) config.get("database");
        assertNotNull(database, "Database config should exist");
        assertEquals("***", database.get("password"), "Database password should be masked");
        assertEquals("***", database.get("_masked"), "Database _masked should be masked");
        assertEquals("db.example.com", database.get("host"), "Database host should not be masked");
    }

    private void verifyTaskDataMasking(Workflow workflow) {
        assertNotNull(workflow.getTasks(), "Tasks should exist");
        assertTrue(workflow.getTasks().size() >= 4, "Should have at least 4 tasks");

        // Verify INLINE task masking
        var inlineTask = workflow.getTasks().stream()
                .filter(task -> "inline_task_1".equals(task.getReferenceTaskName()))
                .findFirst();

        assertTrue(inlineTask.isPresent(), "INLINE task should be present");
        verifyInlineTaskMasking(inlineTask.get());

        // Verify SET_VARIABLE task masking
        var setVariableTask = workflow.getTasks().stream()
                .filter(task -> "set_variable_task_1".equals(task.getReferenceTaskName()))
                .findFirst();

        assertTrue(setVariableTask.isPresent(), "SET_VARIABLE task should be present");
        verifySetVariableTaskMasking(setVariableTask.get());

        // Verify Complex INLINE task masking
        var complexInlineTask = workflow.getTasks().stream()
                .filter(task -> "complex_inline_task_1".equals(task.getReferenceTaskName()))
                .findFirst();

        assertTrue(complexInlineTask.isPresent(), "Complex INLINE task should be present");
        verifyComplexInlineTaskMasking(complexInlineTask.get());

        // Verify HTTP task masking
        var httpTask = workflow.getTasks().stream()
                .filter(task -> "http_task_ref_1".equals(task.getReferenceTaskName()))
                .findFirst();

        assertTrue(httpTask.isPresent(), "HTTP task should be present");
        verifyHttpTaskMasking(httpTask.get());
    }

    private void verifyInlineTaskMasking(Task inlineTask) {
        // Verify input data masking
        Map<String, Object> inputData = inlineTask.getInputData();
        assertEquals("***", inputData.get("_secrets"), "INLINE task _secrets should be masked");
        assertEquals("***", inputData.get("apiKey"), "INLINE task apiKey should be masked");
        assertEquals("graaljs", inputData.get("evaluatorType"), "INLINE evaluatorType should not be masked");

        // Verify nested input masking
        Map<String, Object> userInfo = (Map<String, Object>) inputData.get("userInfo");
        if (userInfo != null) {
            assertEquals("***", userInfo.get("ssn"), "User SSN should be masked");
            assertEquals("***", userInfo.get("_masked"), "User _masked should be masked");
            assertEquals("John Doe", userInfo.get("name"), "User name should not be masked");
        }

        Map<String, Object> paymentInfo = (Map<String, Object>) inputData.get("paymentInfo");
        if (paymentInfo != null) {
            assertEquals("***", paymentInfo.get("creditCard"), "Payment creditCard should be masked");
            assertEquals("***", paymentInfo.get("_secrets"), "Payment _secrets should be masked");
            assertEquals("100.00", paymentInfo.get("amount"), "Payment amount should not be masked");
        }

        // Verify output data masking (INLINE task generates output)
        Map<String, Object> data = inlineTask.getOutputData();
        Object outputData = data.get("result");
        if (outputData instanceof Map) {
            Map<String, Object> resultMap = (Map<String, Object>) outputData;
            if (resultMap != null && !resultMap.isEmpty()) {
                assertEquals("***", resultMap.get("_secrets"), "INLINE output _secrets should be masked");
                assertEquals("***", resultMap.get("apiKey"), "INLINE output apiKey should be masked");
                assertEquals("processed", resultMap.get("status"), "INLINE output status should not be masked");

                // Verify nested output masking
                Map<String, Object> outputUserInfo = (Map<String, Object>) resultMap.get("userInfo");
                if (outputUserInfo != null) {
                    assertEquals("***", outputUserInfo.get("ssn"), "Output user SSN should be masked");
                    assertEquals("***", outputUserInfo.get("_masked"), "Output user _masked should be masked");
                    assertEquals("John Doe", outputUserInfo.get("name"), "Output user name should not be masked");
                }

                Map<String, Object> outputPaymentInfo = (Map<String, Object>) resultMap.get("paymentInfo");
                if (outputPaymentInfo != null) {
                    assertEquals("***", outputPaymentInfo.get("creditCard"), "Output payment creditCard should be masked");
                    assertEquals("***", outputPaymentInfo.get("_secrets"), "Output payment _secrets should be masked");
                    assertEquals("100.00", outputPaymentInfo.get("amount"), "Output payment amount should not be masked");
                }
            }
        }
    }

    private void verifySetVariableTaskMasking(Task setVariableTask) {
        // Verify input data masking
        Map<String, Object> inputData = setVariableTask.getInputData();
        assertEquals("***", inputData.get("_secrets"), "SET_VARIABLE task _secrets should be masked");
        assertEquals("***", inputData.get("apiKey"), "SET_VARIABLE task apiKey should be masked");
        assertEquals("***", inputData.get("password"), "SET_VARIABLE task password should be masked");

        // Verify array/list masking in input
        List<Map<String, Object>> usersList = (List<Map<String, Object>>) inputData.get("usersList");
        if (usersList != null && !usersList.isEmpty()) {
            Map<String, Object> user1 = usersList.get(0);
            assertEquals("***", user1.get("password"), "User1 password should be masked");
            assertEquals("***", user1.get("_masked"), "User1 _masked should be masked");
            assertEquals("user1", user1.get("id"), "User1 ID should not be masked");
            assertEquals("admin", user1.get("role"), "User1 role should not be masked");

            if (usersList.size() > 1) {
                Map<String, Object> user2 = usersList.get(1);
                assertEquals("***", user2.get("ssn"), "User2 SSN should be masked");
                assertEquals("***", user2.get("_secrets"), "User2 _secrets should be masked");
                assertEquals("user2", user2.get("id"), "User2 ID should not be masked");
                assertEquals("user", user2.get("role"), "User2 role should not be masked");
            }
        }

        // Verify complex nested config masking
        Map<String, Object> config = (Map<String, Object>) inputData.get("config");
        if (config != null) {
            assertEquals("***", config.get("_secrets"), "Config _secrets should be masked");
            assertEquals("production", config.get("environment"), "Config environment should not be masked");

            Map<String, Object> dbConfig = (Map<String, Object>) config.get("database");
            if (dbConfig != null) {
                assertEquals("***", dbConfig.get("password"), "DB password should be masked");
                assertEquals("***", dbConfig.get("_masked"), "DB _masked should be masked");
                assertEquals("db.example.com", dbConfig.get("host"), "DB host should not be masked");
            }

            List<Map<String, Object>> services = (List<Map<String, Object>>) config.get("services");
            if (services != null && !services.isEmpty()) {
                Map<String, Object> service1 = services.get(0);
                assertEquals("***", service1.get("apiKey"), "Service apiKey should be masked");
                assertEquals("***", service1.get("_secrets"), "Service _secrets should be masked");
                assertEquals("auth-service", service1.get("name"), "Service name should not be masked");
            }
        }
    }

    private void verifyComplexInlineTaskMasking(Task complexInlineTask) {
        // Verify input data masking
        Map<String, Object> inputData = complexInlineTask.getInputData();
        assertEquals("***", inputData.get("_secrets"), "Complex INLINE task _secrets should be masked");
        assertEquals("***", inputData.get("apiKey"), "Complex INLINE task apiKey should be masked");
        assertEquals("graaljs", inputData.get("evaluatorType"), "Complex INLINE evaluatorType should not be masked");

        // Verify deeply nested input masking
        Map<String, Object> processingConfig = (Map<String, Object>) inputData.get("processingConfig");
        if (processingConfig != null) {
            assertEquals("***", processingConfig.get("password"), "Processing config password should be masked");
            assertEquals("***", processingConfig.get("_masked"), "Processing config _masked should be masked");
            assertEquals(100, processingConfig.get("batchSize"), "Processing config batchSize should not be masked");

            List<Map<String, Object>> processingTasks = (List<Map<String, Object>>) processingConfig.get("tasks");
            if (processingTasks != null && !processingTasks.isEmpty()) {
                Map<String, Object> task1 = processingTasks.get(0);
                assertEquals("***", task1.get("apiKey"), "Processing task apiKey should be masked");
                assertEquals("***", task1.get("_secrets"), "Processing task _secrets should be masked");
                assertEquals("validate-data", task1.get("name"), "Processing task name should not be masked");

                Map<String, Object> validationRules = (Map<String, Object>) task1.get("rules");
                if (validationRules != null) {
                    assertEquals("***", validationRules.get("password"), "Validation rules password should be masked");
                    assertEquals("***", validationRules.get("_masked"), "Validation rules _masked should be masked");
                    assertNotNull(validationRules.get("required"), "Validation rules required should not be masked");
                    assertNotNull(validationRules.get("sensitiveFields"), "Validation rules sensitiveFields should not be masked");
                }
            }
        }

        // Verify output data masking (Complex INLINE task generates complex output)
        Object resultObj = complexInlineTask.getOutputData().get("result");

        if (resultObj instanceof Map) {
            Map<String, Object> outputData = (Map<String, Object>) resultObj;
            if (outputData != null && !outputData.isEmpty()) {
                assertEquals("***", outputData.get("_secrets"), "Complex INLINE output _secrets should be masked");
                assertNotNull(outputData.get("processingId"), "Complex INLINE output processingId should not be masked");

                // Verify nested output array masking
                List<Map<String, Object>> results = (List<Map<String, Object>>) outputData.get("results");
                if (results != null && !results.isEmpty()) {
                    Map<String, Object> result1 = results.get(0);
                    assertEquals("***", result1.get("apiKey"), "Result apiKey should be masked");
                    assertEquals("***", result1.get("_masked"), "Result _masked should be masked");
                    assertEquals("data-processing", result1.get("taskName"), "Result taskName should not be masked");

                    Map<String, Object> resultData = (Map<String, Object>) result1.get("data");
                    if (resultData != null) {
                        assertEquals("***", resultData.get("password"), "Result data password should be masked");
                        assertEquals("***", resultData.get("_secrets"), "Result data _secrets should be masked");
                        assertEquals("completed", resultData.get("status"), "Result data status should not be masked");

                        List<Map<String, Object>> userResults = (List<Map<String, Object>>) resultData.get("userResults");
                        if (userResults != null && !userResults.isEmpty()) {
                            Map<String, Object> userResult1 = userResults.get(0);
                            assertEquals("***", userResult1.get("creditCard"), "User result creditCard should be masked");
                            assertEquals("***", userResult1.get("_masked"), "User result _masked should be masked");
                            assertEquals("user123", userResult1.get("userId"), "User result userId should not be masked");
                        }
                    }
                }
            }
        }
    }

    private void verifyHttpTaskMasking(Task httpTask) {
        // Verify headers masking
        Map<String, Object> headers = (Map<String, Object>) httpTask.getInputData().get("headers");
        assertNotNull(headers, "HTTP task headers should exist");

        Object apiKeyFromWfInputValue = headers.get("apiKeyFromWfInput");
        Object ssnFromWfInputValue = headers.get("ssnFromWfInput");
        Object apiKeyFromTaskInputValue = headers.get("apiKeyFromTaskInput");
        Object ssnFromOutputValue = headers.get("ssnFromOutput");

        // Check if data masked correctly
        assertEquals("***", apiKeyFromWfInputValue,
                "HTTP task apiKeyFromWfInput header should be masked because it uses ${workflow.input.apiKey}");
        assertEquals("This is my SSN ***", ssnFromWfInputValue,
                "HTTP task ssnFromWfInput header should be masked because it uses ${workflow.input.ssn}");
        assertEquals("***", apiKeyFromTaskInputValue,
                "HTTP task apiKeyFromTaskInput header should be masked because it uses ${inline_task_1.input.apiKey}");
        assertEquals("***", ssnFromOutputValue,
                "HTTP task ssnFromOutput header should be masked because it uses ${inline_task_1.output.result.paymentInfo.creditCard}");
    }
}
