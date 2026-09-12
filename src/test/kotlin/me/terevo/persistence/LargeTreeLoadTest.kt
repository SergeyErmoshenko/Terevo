package me.terevo.persistence

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import me.terevo.domain.invariant.Change
import me.terevo.domain.model.FamilyTree
import me.terevo.domain.model.Marriage
import me.terevo.domain.model.ParentChild
import me.terevo.domain.model.Relation
import me.terevo.persistence.db.TerevoDatabase
import me.terevo.testing.person
import me.terevo.testing.shouldBeOk
import kotlin.system.measureTimeMillis
import kotlin.test.*

class LargeTreeLoadTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var repository: SqlDelightTreeRepository

    @BeforeTest
    fun open() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        TerevoDatabase.Schema.create(driver)
        repository = SqlDelightTreeRepository(TerevoDatabase(driver))
    }

    @AfterTest
    fun close() {
        driver.close()
    }

    @Test
    fun `ten thousand people are stored and loaded within the budget`() {
        val people = List(PEOPLE) { person(surname = "Фамилия$it", born = 1800 + it % YEARS) }
        val relations = mutableListOf<Relation>()
        for (index in 1 until PEOPLE) {
            relations.add(
                ParentChild.of(parent = people[index / 2].id, child = people[index].id).shouldBeOk(),
            )
        }
        for (index in 0 until MARRIAGES) {
            val first = people[index]
            val second = people[PEOPLE - 1 - index]
            relations.add(Marriage.of(first = first.id, second = second.id).shouldBeOk())
        }

        val changes = people.map { Change.AddPerson(it) } + relations.map { Change.AddRelation(it) }
        repository.apply(changes).shouldBeOk()

        var loaded = FamilyTree.EMPTY
        val elapsed = measureTimeMillis {
            loaded = repository.load().shouldBeOk()
        }

        assertEquals(PEOPLE, loaded.size)
        assertEquals(relations.size, loaded.relations.size)
        assertTrue(elapsed < LOAD_BUDGET_MILLIS, "load took $elapsed ms")
    }

    private companion object {
        const val PEOPLE = 10_000
        const val MARRIAGES = 2_000
        const val YEARS = 150
        const val LOAD_BUDGET_MILLIS = 2_000L
    }
}
