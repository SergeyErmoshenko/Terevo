package me.terevo.persistence

import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals

class SqliteNativeProbeTest {

    @Test
    fun `sqlite native library loads in the test jvm`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE probe(value INTEGER NOT NULL)")
                statement.execute("INSERT INTO probe VALUES (42)")
                statement.executeQuery("SELECT value FROM probe").use { rows ->
                    rows.next()
                    assertEquals(42, rows.getInt(1))
                }
            }
        }
    }
}
