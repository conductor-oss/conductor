import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.core.type.TypeReference
import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*

class TestSerDerGrantedAccessResponse {
    private val objectMapper = ObjectMapper()
    private val serverJSON = """{"grantedAccess":[{"access":[{"values":["CREATE","READ","EXECUTE","UPDATE","DELETE"],"constants":{"READ":"(100)","EXECUTE":"(200)","DELETE":"(400)","CREATE":"(0)","UPDATE":"(300)"},"sampleValue":"CREATE"}],"tag":"sample_tag","target":{"id":"sample_id","type":{"values":["WORKFLOW","WORKFLOW_DEF","WORKFLOW_SCHEDULE","EVENT_HANDLER","TASK_DEF","TASK_REF_NAME","TASK_ID","APPLICATION","USER","SECRET_NAME","ENV_VARIABLE","TAG","DOMAIN","INTEGRATION_PROVIDER","INTEGRATION","PROMPT","USER_FORM_TEMPLATE","SCHEMA","CLUSTER_CONFIG","WEBHOOK"],"constants":{"DOMAIN":"(12)","SECRET_NAME":"(9)","ENV_VARIABLE":"(10)","CLUSTER_CONFIG":"(18)","APPLICATION":"(7)","PROMPT":"(15)","TASK_DEF":"(4)","USER":"(8)","USER_FORM_TEMPLATE":"(16)","INTEGRATION_PROVIDER":"(13)","WORKFLOW_DEF":"(1)","INTEGRATION":"(14)","SCHEMA":"(17)","WEBHOOK":"(19)","EVENT_HANDLER":"(3)","TASK_REF_NAME":"(5)","TAG":"(11)","WORKFLOW_SCHEDULE":"(2)","WORKFLOW":"(0)","TASK_ID":"(6)"},"sampleValue":"WORKFLOW"}}}]}"""

    @Test
    fun testSerializationDeserialization() {
        try {
            val response = objectMapper.readValue(serverJSON, GrantedAccessResponse::class.java)
            assertNotNull(response)
            assertNotNull(response.grantedAccess)
            assertEquals(1, response.grantedAccess.size)

            val grantedAccess = response.grantedAccess[0]
            assertNotNull(grantedAccess.access)
            assertEquals(1, grantedAccess.access.size)

            val access = grantedAccess.access[0]
            assertNotNull(access.values)
            assertEquals(listOf("CREATE", "READ", "EXECUTE", "UPDATE", "DELETE"), access.values)
            assertNotNull(access.constants)
            assertEquals(mapOf("READ" to "(100)", "EXECUTE" to "(200)", "DELETE" to "(400)", "CREATE" to "(0)", "UPDATE" to "(300)"), access.constants)
            assertEquals("CREATE", access.sampleValue)

            assertEquals("sample_tag", grantedAccess.tag)

            assertNotNull(grantedAccess.target)
            assertEquals("sample_id", grantedAccess.target.id)

            val targetType = grantedAccess.target.type
            assertNotNull(targetType)
            assertNotNull(targetType.values)
            assertEquals(
                listOf("WORKFLOW","WORKFLOW_DEF","WORKFLOW_SCHEDULE","EVENT_HANDLER","TASK_DEF","TASK_REF_NAME","TASK_ID","APPLICATION","USER","SECRET_NAME","ENV_VARIABLE","TAG","DOMAIN","INTEGRATION_PROVIDER","INTEGRATION","PROMPT","USER_FORM_TEMPLATE","SCHEMA","CLUSTER_CONFIG","WEBHOOK"),
                targetType.values
            )
            assertNotNull(targetType.constants)
            assertEquals(
                mapOf(
                    "DOMAIN" to "(12)",
                    "SECRET_NAME" to "(9)",
                    "ENV_VARIABLE" to "(10)",
                    "CLUSTER_CONFIG" to "(18)",
                    "APPLICATION" to "(7)",
                    "PROMPT" to "(15)",
                    "TASK_DEF" to "(4)",
                    "USER" to "(8)",
                    "USER_FORM_TEMPLATE" to "(16)",
                    "INTEGRATION_PROVIDER" to "(13)",
                    "WORKFLOW_DEF" to "(1)",
                    "INTEGRATION" to "(14)",
                    "SCHEMA" to "(17)",
                    "WEBHOOK" to "(19)",
                    "EVENT_HANDLER" to "(3)",
                    "TASK_REF_NAME" to "(5)",
                    "TAG" to "(11)",
                    "WORKFLOW_SCHEDULE" to "(2)",
                    "WORKFLOW" to "(0)",
                    "TASK_ID" to "(6)"
                ),
                targetType.constants
            )
            assertEquals("WORKFLOW", targetType.sampleValue)

            val jsonOutput = objectMapper.writeValueAsString(response)
            val originalTree = objectMapper.readTree(serverJSON)
            val outputTree = objectMapper.readTree(jsonOutput)
            assertEquals(originalTree, outputTree)
        } catch (e: Exception) {
            fail("Exception during serialization/deserialization test: ${e.message}")
        }
    }
}