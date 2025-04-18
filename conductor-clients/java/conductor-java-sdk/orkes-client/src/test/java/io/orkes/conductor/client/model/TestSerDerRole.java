import com.fasterxml.jackson.databind.ObjectMapper
import io.orkes.conductor.client.model.Permission
import io.orkes.conductor.client.model.Role
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class TestSerDerRole {
    private val objectMapper = ObjectMapper()

    @Test
    fun testSerDe() {
        val serverJson = """{"permissions":[{"name":"sample_name"}],"name":"sample_name"}"""
        val role = objectMapper.readValue(serverJson, Role::class.java)
        Assertions.assertNotNull(role)
        Assertions.assertEquals("sample_name", role.name)
        Assertions.assertNotNull(role.permissions)
        Assertions.assertEquals(1, role.permissions.size)
        val permission = role.permissions[0]
        Assertions.assertEquals("sample_name", permission.name)
        val serializedJson = objectMapper.writeValueAsString(role)
        val deserializedOriginal = objectMapper.readTree(serverJson)
        val deserializedSerialized = objectMapper.readTree(serializedJson)
        Assertions.assertEquals(deserializedOriginal, deserializedSerialized)
    }
}