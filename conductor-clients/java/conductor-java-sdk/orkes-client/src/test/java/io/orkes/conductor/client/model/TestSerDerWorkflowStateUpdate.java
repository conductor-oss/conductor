public class TestSerDerWorkflowStateUpdate {
    @Test
    public void testSerializationDeserialization() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String serverJson = "{\"variables\":{\"sample_key\":\"sample_value\"},\"taskReferenceName\":\"sample_taskReferenceName\",\"taskResult\":{\"outputData\":{\"sample_key\":\"sample_value\"},\"extendLease\":true,\"callbackAfterSeconds\":123,\"workerId\":\"sample_workerId\",\"subWorkflowId\":\"sample_subWorkflowId\",\"reasonForIncompletion\":\"sample_reasonForIncompletion\",\"workflowInstanceId\":\"sample_workflowInstanceId\",\"logs\":[{\"log\":\"sample_log\",\"createdTime\":123,\"taskId\":\"sample_taskId\"}],\"taskId\":\"sample_taskId\",\"status\":{\"values\":[\"IN_PROGRESS\",\"FAILED\",\"FAILED_WITH_TERMINAL_ERROR\",\"COMPLETED\"],\"constants\":{\"IN_PROGRESS\":\"(0)\",\"FAILED_WITH_TERMINAL_ERROR\":\"(2)\",\"COMPLETED\":\"(3)\",\"FAILED\":\"(1)\"},\"sampleValue\":\"IN_PROGRESS\"},\"externalOutputPayloadStoragePath\":\"sample_externalOutputPayloadStoragePath\"}}";
        WorkflowStateUpdate obj = mapper.readValue(serverJson, WorkflowStateUpdate.class);
        
        Assertions.assertEquals("sample_taskReferenceName", obj.getTaskReferenceName());
        Assertions.assertNotNull(obj.getVariables());
        Assertions.assertEquals("sample_value", obj.getVariables().get("sample_key"));
        
        TaskResult taskResult = obj.getTaskResult();
        Assertions.assertNotNull(taskResult);
        Assertions.assertEquals("sample_value", taskResult.getOutputData().get("sample_key"));
        Assertions.assertTrue(taskResult.isExtendLease());
        Assertions.assertEquals(123, taskResult.getCallbackAfterSeconds());
        Assertions.assertEquals("sample_workerId", taskResult.getWorkerId());
        Assertions.assertEquals("sample_subWorkflowId", taskResult.getSubWorkflowId());
        Assertions.assertEquals("sample_reasonForIncompletion", taskResult.getReasonForIncompletion());
        Assertions.assertEquals("sample_workflowInstanceId", taskResult.getWorkflowInstanceId());
        Assertions.assertNotNull(taskResult.getLogs());
        Assertions.assertEquals(1, taskResult.getLogs().size());
        Log log = taskResult.getLogs().get(0);
        Assertions.assertEquals("sample_log", log.getLog());
        Assertions.assertEquals(123, log.getCreatedTime());
        Assertions.assertEquals("sample_taskId", log.getTaskId());
        Assertions.assertEquals("sample_taskId", taskResult.getTaskId());
        
        Status status = taskResult.getStatus();
        Assertions.assertNotNull(status);
        Assertions.assertEquals(Arrays.asList("IN_PROGRESS", "FAILED", "FAILED_WITH_TERMINAL_ERROR", "COMPLETED"), status.getValues());
        Assertions.assertEquals("(0)", status.getConstants().get("IN_PROGRESS"));
        Assertions.assertEquals("(2)", status.getConstants().get("FAILED_WITH_TERMINAL_ERROR"));
        Assertions.assertEquals("(3)", status.getConstants().get("COMPLETED"));
        Assertions.assertEquals("(1)", status.getConstants().get("FAILED"));
        Assertions.assertEquals("IN_PROGRESS", status.getSampleValue());
        
        Assertions.assertEquals("sample_externalOutputPayloadStoragePath", taskResult.getExternalOutputPayloadStoragePath());
        
        String marshalledJson = mapper.writeValueAsString(obj);
        JsonNode original = mapper.readTree(serverJson);
        JsonNode marshalled = mapper.readTree(marshalledJson);
        Assertions.assertEquals(original, marshalled);
    }
}