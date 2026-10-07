package me.terevo.server

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import me.terevo.app.AppController
import me.terevo.app.ProjectDirectories
import me.terevo.persistence.SqliteProjectService
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StateServerTest {
    private lateinit var workspace: Path
    private lateinit var controller: AppController
    private lateinit var server: StateServer

    @BeforeTest
    fun prepare() {
        workspace = createTempDirectory("terevo-server")
        controller = AppController(SqliteProjectService(timestamp = { "2026-08-30T12:00:00Z" }))
        server = StateServer(controller, ProjectDirectories(workspace))
    }

    @AfterTest
    fun cleanup() {
        controller.close()
        Files.walk(workspace).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
    }

    private fun send(id: Long, action: String): JsonObject =
        Json.parseToJsonElement(server.handle("""{"id":$id,"action":$action}""")).jsonObject

    @Test
    fun `hello carries the initial state and the projects folder`() {
        val hello = Json.parseToJsonElement(server.hello()).jsonObject

        assertEquals(workspace.resolve("Terevo").toString(), hello.getValue("hello").jsonObject["projectsRoot"]?.jsonPrimitive?.content)
        assertEquals("false", hello.getValue("state").jsonObject["isProjectOpen"]?.jsonPrimitive?.content)
    }

    @Test
    fun `a person added through the protocol arrives with its layout`() {
        val created = send(1, """{"type":"createProject","name":"семья"}""")
        assertEquals(1, created.getValue("id").jsonPrimitive.long)
        assertEquals("семья", created.getValue("state").jsonObject["projectName"]?.jsonPrimitive?.content)

        send(2, """{"type":"addPerson"}""")
        send(3, """{"type":"updatePersonForm","fields":{"surname":"Иванов","givenName":"Иван"}}""")
        val saved = send(4, """{"type":"savePerson"}""").getValue("state").jsonObject
        val canvas = saved.getValue("canvas").jsonObject
        val nodes = canvas.getValue("layout").jsonObject.getValue("nodes").jsonArray

        assertEquals(1, nodes.size)
        assertEquals("Иванов Иван", saved.getValue("selectedPerson").jsonObject["name"]?.jsonPrimitive?.content)

        val again = send(5, """{"type":"toggleSidebar"}""").getValue("state").jsonObject.getValue("canvas").jsonObject
        assertEquals(JsonNull, again["layout"], "an unchanged layout is not sent again")
        assertEquals(canvas["layoutVersion"], again["layoutVersion"])
    }

    @Test
    fun `malformed and failing requests are reported, not thrown`() {
        val malformed = Json.parseToJsonElement(server.handle("not json")).jsonObject
        assertTrue(malformed["error"]?.jsonPrimitive?.content.orEmpty().isNotEmpty())

        val failing = send(7, """{"type":"selectPerson","id":"not-a-uuid"}""")
        assertEquals(7, failing.getValue("id").jsonPrimitive.long)
        assertNotEquals(JsonNull, failing["error"])
    }

    @Test
    fun `canvas actions select by id`() {
        send(1, """{"type":"createProject","name":"tree"}""")
        send(2, """{"type":"addPerson"}""")
        send(3, """{"type":"updatePersonForm","fields":{"surname":"Иванов"}}""")
        val id = send(4, """{"type":"savePerson"}""").getValue("state").jsonObject
            .getValue("selectedPerson").jsonObject.getValue("id").jsonPrimitive.content

        val cleared = send(5, """{"type":"selectOnCanvas","id":null}""").getValue("state").jsonObject
        assertEquals(JsonNull, cleared["selectedPerson"])

        val selected = send(6, """{"type":"selectOnCanvas","id":"$id"}""").getValue("state").jsonObject
        assertEquals(id, selected.getValue("canvas").jsonObject["selected"]?.jsonPrimitive?.content)
        assertNull(selected["error"])
    }
}
