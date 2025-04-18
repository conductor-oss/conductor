import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.orkes.conductor.client.model.GrantedAccess
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TestSerDerGrantedAccess {

    private val objectMapper = ObjectMapper()

    @Test
    fun testSerializationDeserialization() {
        val serverJSON = """{"access":[{"values":["CREATE","READ","EXECUTE","UPDATE","DELETE"],"constants":{"READ":"(100)","EXECUTE":"(200)","DELETE":"(400)","CREATE":"(0)","UPDATE":"(300)"},"sampleValue":"CREATE"}],"tag":"sample_tag","target":{"id":"sample_id","type":{"values":["WORKFLOW","WORKFLOW_DEF","WORKFLOW_SCHEDULE","EVENT_HANDLER","TASK_DEF","TASK_REF_NAME","TASK_ID","APPLICATION","USER","SECRET_NAME","ENV_VARIABLE","TAG","DOMAIN","INTEGRATION_PROVIDER","INTEGRATION","PROMPT","USER_FORM_TEMPLATE","SCHEMA","CLUSTER_CONFIG","WEBHOOK"],"constants":{"DOMAIN":"(12)","SECRET_NAME":"(9)","ENV_VARIABLE":"(10)","CLUSTER_CONFIG":"(18)","APPLICATION":"(7)","PROMPT":"(15)","TASK_DEF":"(4)","USER":"(8)","USER_FORM_TEMPLATE":"(16)","INTEGRATION_PROVIDER":"(13)","WORKFLOW_DEF":"(1)","INTEGRATION":"(14)","SCHEMA":"(17)","WEBHOOK":"(19)","EVENT_HANDLER":"(3)","TASK_REF_NAME":"(5)","TAG":"(11)","WORKFLOW_SCHEDULE":"(2)","WORKFLOW":"(0)","TASK_ID":"(6)"},"sampleValue":"WORKFLOW"}}}"""

        val grantedAccess = objectMapper.readValue(serverJSON, GrantedAccess::class.java)

        // Assert access list
        assertNotNull(grantedAccess.access)
        val accessList = grantedAccess.access
        assertEquals(5, accessList.size)
        assertTrue(accessList.contains(GrantedAccess.AccessEnum.CREATE))
        assertTrue(accessList.contains(GrantedAccess.AccessEnum.READ))
        assertTrue(accessList.contains(GrantedAccess.AccessEnum.EXECUTE))
        assertTrue(accessList.contains(GrantedAccess.AccessEnum.UPDATE))
        assertTrue(accessList.contains(GrantedAccess.AccessEnum.DELETE))

        // Assert tag
        assertEquals("sample_tag", grantedAccess.tag)

        // Assert target
        assertNotNull(grantedAccess.target)
        assertEquals("sample_id", grantedAccess.target.id)
        assertEquals(GrantedAccess.TargetRef.TypeEnum.WORKFLOW, grantedAccess.target.type)

        // Serialize back to JSON
        val serializedJSON = objectMapper.writeValueAsString(grantedAccess)

        // Compare JSONs
        val original = objectMapper.readTree(serverJSON)
        val serialized = objectMapper.readTree(serializedJSON)
        assertEquals(original, serialized)
    }
}