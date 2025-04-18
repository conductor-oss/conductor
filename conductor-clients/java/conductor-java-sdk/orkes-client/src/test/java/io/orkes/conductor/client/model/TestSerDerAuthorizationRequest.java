import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*

class TestSerDerAuthorizationRequest {
    @Test
    fun testSerializationDeserialization() {
        val serverJSON = """{"access":[{"values":["CREATE","READ","EXECUTE","UPDATE","DELETE"],"constants":{"READ":"(100)","EXECUTE":"(200)","DELETE":"(400)","CREATE":"(0)","UPDATE":"(300)"},"sampleValue":"CREATE"}],"subject":{"id":"sample_id","type":"sample_type"},"target":{"id":"sample_id","type":{"values":["WORKFLOW","WORKFLOW_DEF","WORKFLOW_SCHEDULE","EVENT_HANDLER","TASK_DEF","TASK_REF_NAME","TASK_ID","APPLICATION","USER","SECRET_NAME","ENV_VARIABLE","TAG","DOMAIN","INTEGRATION_PROVIDER","INTEGRATION","PROMPT","USER_FORM_TEMPLATE","SCHEMA","CLUSTER_CONFIG","WEBHOOK"],"constants":{"DOMAIN":"(12)","SECRET_NAME":"(9)","ENV_VARIABLE":"(10)","CLUSTER_CONFIG":"(18)","APPLICATION":"(7)","PROMPT":"(15)","TASK_DEF":"(4)","USER":"(8)","USER_FORM_TEMPLATE":"(16)","INTEGRATION_PROVIDER":"(13)","WORKFLOW_DEF":"(1)","INTEGRATION":"(14)","SCHEMA":"(17)","WEBHOOK":"(19)","EVENT_HANDLER":"(3)","TASK_REF_NAME":"(5)","TAG":"(11)","WORKFLOW_SCHEDULE":"(2)","WORKFLOW":"(0)","TASK_ID":"(6)"},"sampleValue":"WORKFLOW"}}}"""
        val mapper = ObjectMapper()
        val request = mapper.readValue(serverJSON, AuthorizationRequest::class.java)
        
        assertNotNull(request.access)
        assertEquals(1, request.access.size)
        val accessItem = request.access[0]
        assertEquals(AuthorizationRequest.AccessEnum.CREATE, accessItem)
        
        assertNotNull(request.subject)
        assertEquals("sample_id", request.subject.id)
        assertEquals("sample_type", request.subject.type)
        
        assertNotNull(request.target)
        assertEquals("sample_id", request.target.id)
        assertNotNull(request.target.type)
        assertEquals("WORKFLOW", request.target.type.sampleValue)
        assertEquals(20, request.target.type.values.size)
        assertEquals("(0)", request.target.type.constants["WORKFLOW"])
        
        val serializedJSON = mapper.writeValueAsString(request)
        assertEquals(mapper.readTree(serverJSON), mapper.readTree(serializedJSON))
    }
}