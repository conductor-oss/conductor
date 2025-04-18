import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.conductor.common.run.TaskSummary
import com.netflix.conductor.common.metadata.tasks.Task
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class TestSerDerTaskSummary {
    @Test
    fun testSerializationDeserialization() {
        val serverJSON = """{"scheduledTime":"sample_scheduledTime","externalInputPayloadStoragePath":"sample_externalInputPayloadStoragePath","workflowPriority":123,"updateTime":"sample_updateTime","executionTime":123,"output":"sample_output","input":"sample_input","taskType":"sample_taskType","reasonForIncompletion":"sample_reasonForIncompletion","domain":"sample_domain","queueWaitTime":123,"taskDefName":"sample_taskDefName","workflowType":"sample_workflowType","correlationId":"sample_correlationId","startTime":"sample_startTime","endTime":"sample_endTime","workflowId":"sample_workflowId","taskId":"sample_taskId","status":{"values":["IN_PROGRESS","CANCELED","FAILED","FAILED_WITH_TERMINAL_ERROR","COMPLETED","COMPLETED_WITH_ERRORS","SCHEDULED","TIMED_OUT","SKIPPED"],"constants":{"IN_PROGRESS":"(false, true, true)","FAILED_WITH_TERMINAL_ERROR":"(true, false, false)","COMPLETED":"(true, true, true)","FAILED":"(true, false, true)","TIMED_OUT":"(true, false, true)","CANCELED":"(true, false, false)","COMPLETED_WITH_ERRORS":"(true, true, true)","SKIPPED":"(true, true, false)","SCHEDULED":"(false, true, true)"},"sampleValue":"IN_PROGRESS"},"externalOutputPayloadStoragePath":"sample_externalOutputPayloadStoragePath"}"""
        val mapper = ObjectMapper()
        val pojo = mapper.readValue(serverJSON, TaskSummary::class.java)

        Assertions.assertEquals("sample_scheduledTime", pojo.workflowId)
        Assertions.assertEquals("sample_workflowType", pojo.workflowType)
        Assertions.assertEquals("sample_correlationId", pojo.correlationId)
        Assertions.assertEquals("sample_scheduledTime", pojo.scheduledTime)
        Assertions.assertEquals("sample_startTime", pojo.startTime)
        Assertions.assertEquals("sample_updateTime", pojo.updateTime)
        Assertions.assertEquals("sample_endTime", pojo.endTime)
        Assertions.assertEquals(Task.Status.IN_PROGRESS, pojo.status)
        Assertions.assertEquals("sample_reasonForIncompletion", pojo.reasonForIncompletion)
        Assertions.assertEquals(123, pojo.executionTime)
        Assertions.assertEquals(123, pojo.queueWaitTime)
        Assertions.assertEquals("sample_taskDefName", pojo.taskDefName)
        Assertions.assertEquals("sample_taskType", pojo.taskType)
        Assertions.assertEquals("sample_input", pojo.input)
        Assertions.assertEquals("sample_output", pojo.output)
        Assertions.assertEquals("sample_taskId", pojo.taskId)
        Assertions.assertEquals("sample_externalInputPayloadStoragePath", pojo.externalInputPayloadStoragePath)
        Assertions.assertEquals("sample_externalOutputPayloadStoragePath", pojo.externalOutputPayloadStoragePath)
        Assertions.assertEquals(123, pojo.workflowPriority)

        val serializedJSON = mapper.writeValueAsString(pojo)
        val originalTree = mapper.readTree(serverJSON)
        val serializedTree = mapper.readTree(serializedJSON)
        Assertions.assertEquals(originalTree, serializedTree)
    }
}